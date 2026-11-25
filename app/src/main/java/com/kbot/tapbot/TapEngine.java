package com.kbot.tapbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.util.Calendar;
import java.util.List;

/**
 * 回放引擎：
 *  - 指定具体时刻（时:分:秒）触发，到点开始回放
 *  - 回放前自动亮屏唤醒（WakeLock，无密码直接操作）
 *  - 回放前确保目标 App 在前台：不在则启动它并等待，仍失败则终止并弹通知
 *  - 按脚本逐点回放，支持循环次数，坐标按屏幕等比缩放
 */
public class TapEngine {

    public interface Listener {
        void onCountdown(String text);
        void onReplayProgress(int done, int total);
        void onStateChange(String state);   // idle / counting / replaying / done / terminated
        void onLog(String msg);
    }

    private static final String PREF = "tapbot_settings";
    private static final String KEY_TARGET_PKG = "target_app_pkg";
    private static final String KEY_WAKE = "wake_screen";
    private static final String CHANNEL = "tapbot_engine";

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Listener listener;

    private volatile boolean counting = false;
    private volatile boolean replaying = false;
    private Runnable countdownTick;
    private Runnable replayTask;
    private PowerManager.WakeLock wakeLock;

    public TapEngine(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public void setListener(Listener l) { this.listener = l; }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public String getTargetPkg() { return prefs().getString(KEY_TARGET_PKG, ""); }
    public void setTargetPkg(String pkg) { prefs().edit().putString(KEY_TARGET_PKG, pkg).apply(); }
    public boolean isWakeEnabled() { return prefs().getBoolean(KEY_WAKE, true); }
    public void setWakeEnabled(boolean v) { prefs().edit().putBoolean(KEY_WAKE, v).apply(); }

    private void log(String msg) {
        if (listener != null) listener.onLog(msg);
        android.util.Log.d("TapEngine", msg);
    }

    public boolean isCounting() { return counting; }
    public boolean isReplaying() { return replaying; }

    /** 开始定时：到 hour:minute:second 触发回放。 */
    public void startScheduled(String name, int hour, int minute, int second) {
        stopAll();
        TapScript script = new ScriptStore(ctx).load(name);
        if (script == null || script.points.isEmpty()) {
            log("脚本为空，无法回放");
            if (listener != null) listener.onStateChange("idle");
            return;
        }
        counting = true;
        if (listener != null) listener.onStateChange("counting");
        log("定时已启动，目标时刻 " + fmt(hour) + ":" + fmt(minute) + ":" + fmt(second));

        countdownTick = new Runnable() {
            @Override
            public void run() {
                if (!counting) return;
                Calendar now = Calendar.getInstance();
                long nowSec = now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND);
                long targetSec = hour * 3600 + minute * 60 + second;
                long diff = targetSec - nowSec;
                if (diff < 0) diff += 24 * 3600;
                if (diff <= 0) {
                    counting = false;
                    log("到点，开始执行");
                    prepareAndReplay(script);
                    return;
                }
                if (listener != null) listener.onCountdown(fmtCountdown(diff));
                h.postDelayed(this, 1000 - (now.get(Calendar.MILLISECOND) % 1000));
            }
        };
        h.post(countdownTick);
    }

    /** 立即执行（测试用）。 */
    public void startNow(String name) {
        stopAll();
        TapScript script = new ScriptStore(ctx).load(name);
        if (script == null || script.points.isEmpty()) {
            log("脚本为空");
            if (listener != null) listener.onStateChange("idle");
            return;
        }
        log("立即执行 " + name);
        prepareAndReplay(script);
    }

    /**
     * 执行前准备：亮屏唤醒 → 确保目标 App 在前台 → 回放。
     */
    private void prepareAndReplay(final TapScript script) {
        if (!TapAccessibilityService.instance.isReady()) {
            log("无障碍服务未开启，无法点击");
            notifyFail("无障碍服务未开启，无法执行");
            if (listener != null) listener.onStateChange("terminated");
            return;
        }
        // 1. 亮屏唤醒
        if (isWakeEnabled()) {
            wakeScreen();
            log("已亮屏唤醒");
        }
        // 2. 确保目标 App 在前台
        ensureForegroundAndReplay(script, 0);
    }

    /**
     * 确保目标 App 在前台。targetPkg 为空则跳过检测直接回放。
     * 不在前台 → 启动它 → 等待 → 重试；超过 maxRetry 次仍失败 → 终止并通知。
     */
    private void ensureForegroundAndReplay(final TapScript script, final int attempt) {
        final String targetPkg = getTargetPkg();
        if (targetPkg == null || targetPkg.isEmpty()) {
            startReplay(script);
            return;
        }
        final int maxRetry = 3;
        final String fg = TapAccessibilityService.instance.getForegroundPackage();
        if (targetPkg.equals(fg)) {
            log("目标 App 已在前台：" + targetPkg);
            startReplay(script);
            return;
        }
        if (attempt >= maxRetry) {
            log("目标 App 无法打开，终止执行");
            notifyFail("目标 App（" + targetPkg + "）无法打开，本次执行已终止");
            if (listener != null) listener.onStateChange("terminated");
            return;
        }
        log("目标 App 不在前台，尝试启动（第 " + (attempt + 1) + " 次）");
        boolean launched = TapAccessibilityService.instance.launchApp(targetPkg);
        if (!launched) {
            log("未找到该 App 的启动器，稍后重试");
        }
        h.postDelayed(() -> ensureForegroundAndReplay(script, attempt + 1), 1500);
    }

    private void startReplay(final TapScript script) {
        replaying = true;
        if (listener != null) listener.onStateChange("replaying");
        replayTask = new Runnable() {
            @Override
            public void run() {
                if (!replaying) return;
                runLoop(script, 1);
            }
        };
        h.post(replayTask);
    }

    /** 执行第 loop 轮（从 1 开始）。loopCount=-1 无限。 */
    private void runLoop(final TapScript script, final int loop) {
        if (!replaying) return;
        List<TapPoint> pts = script.points;
        if (pts.isEmpty()) { finishReplay(); return; }
        log("第 " + loop + " 轮，共 " + pts.size() + " 点");
        replayPoint(script, pts, 0, () -> {
            if (!replaying) return;
            if (script.loopCount == -1 || loop < script.loopCount) {
                h.postDelayed(() -> runLoop(script, loop + 1), 500);
            } else {
                finishReplay();
            }
        });
    }

    private void finishReplay() {
        replaying = false;
        releaseWake();
        if (listener != null) listener.onStateChange("done");
        log("回放完成");
    }

    private void replayPoint(final TapScript script, final List<TapPoint> pts, final int idx,
                             final Runnable onRoundDone) {
        if (!replaying) return;
        if (idx >= pts.size()) { onRoundDone.run(); return; }
        final TapPoint p = pts.get(idx);
        float[] scale = screenScale(script);
        float x = p.x * scale[0];
        float y = p.y * scale[1];
        if (listener != null) listener.onReplayProgress(idx + 1, pts.size());
        log("点击 " + (idx + 1) + "/" + pts.size() + " @(" + (int) x + "," + (int) y + ")");
        TapAccessibilityService.instance.tap(x, y, p.holdMs, () -> {
            if (!replaying) return;
            h.postDelayed(() -> replayPoint(script, pts, idx + 1, onRoundDone), p.gapMs);
        });
    }

    /** 亮屏唤醒：获取 PARTIAL_WAKE_LOCK 并开启屏幕。 */
    private void wakeScreen() {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm.isInteractive()) {
                log("屏幕已亮，无需唤醒");
                return;
            }
            releaseWake();
            wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP, "tapbot::wake");
            wakeLock.acquire(10 * 60 * 1000L); // 最多 10 分钟
        } catch (Throwable t) {
            log("唤醒失败：" + t.getMessage());
        }
    }

    private void releaseWake() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Throwable ignored) {}
        wakeLock = null;
    }

    private void notifyFail(String msg) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "TapBot 通知",
                        NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(ch);
            }
            Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(ctx, CHANNEL)
                    : new Notification.Builder(ctx);
            b.setContentTitle("TapBot 执行失败")
                    .setContentText(msg)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setAutoCancel(true);
            nm.notify(1001, b.build());
        } catch (Throwable ignored) {}
    }

    private float[] screenScale(TapScript script) {
        float sx = 1f, sy = 1f;
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            if (script.recordScreenW > 0) sx = dm.widthPixels / (float) script.recordScreenW;
            if (script.recordScreenH > 0) sy = dm.heightPixels / (float) script.recordScreenH;
        } catch (Throwable ignored) {}
        return new float[]{sx, sy};
    }

    public void stopAll() {
        counting = false;
        replaying = false;
        if (countdownTick != null) h.removeCallbacks(countdownTick);
        if (replayTask != null) h.removeCallbacks(replayTask);
        h.removeCallbacksAndMessages(null);
        releaseWake();
    }

    private static String fmt(int v) { return v < 10 ? "0" + v : "" + v; }

    private static String fmtCountdown(long totalSec) {
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        return fmt((int) h) + ":" + fmt((int) m) + ":" + fmt((int) s);
    }
}
