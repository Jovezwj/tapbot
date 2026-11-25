package com.kbot.tapbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 悬浮窗服务：提供「开始录制 / 停止录制 / 关闭」控制条。
 * 录制通过无障碍服务的 onTouchEvent 捕获真实触摸坐标（不挡屏幕）。
 */
public class OverlayService extends Service {

    private static final String CHANNEL_ID = "tapbot_overlay";

    private WindowManager wm;
    private View controlView;
    private WindowManager.LayoutParams controlParams;

    private TapScript current;
    private final List<TapPoint> recorded = new ArrayList<>();
    private long lastDown = 0;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
        startForeground(1, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        showControl();
        return START_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "模拟点击助手",
                    NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("TapBot 模拟点击助手")
                .setContentText("悬浮窗运行中")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void showControl() {
        if (controlView != null) {
            try { wm.removeView(controlView); } catch (Throwable ignored) {}
            controlView = null;
        }
        controlView = LayoutInflater.from(this).inflate(R.layout.overlay_control, null);
        controlParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        controlParams.gravity = Gravity.TOP | Gravity.START;
        controlParams.x = 20;
        controlParams.y = 300;

        Button btnRecord = controlView.findViewById(R.id.btn_record);
        Button btnStop = controlView.findViewById(R.id.btn_stop);
        Button btnClose = controlView.findViewById(R.id.btn_close);
        TextView tvStatus = controlView.findViewById(R.id.tv_status);

        btnRecord.setOnClickListener(v -> {
            if (!TapAccessibilityService.instance.isReady()) {
                tvStatus.setText("请先开启无障碍");
                return;
            }
            recorded.clear();
            current = new TapScript();
            current.name = "脚本" + (System.currentTimeMillis() % 100000);
            TapAccessibilityService.instance.startRecording(new TapAccessibilityService.RecordListener() {
                @Override
                public void onTapDown(float x, float y) {
                    lastDown = System.currentTimeMillis();
                }
                @Override
                public void onTapUp(float x, float y) {
                    long gap = System.currentTimeMillis() - lastDown;
                    TapPoint p = new TapPoint(x, y, 80, Math.max(100, Math.min(gap, 3000)));
                    recorded.add(p);
                    android.util.Log.d("TapBot", "recorded " + (int) x + "," + (int) y);
                }
            });
            tvStatus.setText("录制中，去操作吧");
        });
        btnStop.setOnClickListener(v -> {
            stopRecord(tvStatus);
        });
        btnClose.setOnClickListener(v -> stopSelf());

        try {
            wm.addView(controlView, controlParams);
        } catch (Throwable t) {
            android.util.Log.e("TapBot", "addView failed", t);
        }
    }

    private void stopRecord(TextView tvStatus) {
        TapAccessibilityService.instance.stopRecording();
        if (current == null) current = new TapScript();
        current.points = recorded;
        DisplayMetrics dm = new DisplayMetrics();
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE))
                    .getDefaultDisplay().getRealMetrics(dm);
        } catch (Throwable ignored) {}
        current.recordScreenW = dm.widthPixels;
        current.recordScreenH = dm.heightPixels;
        new ScriptStore(this).save(current);
        tvStatus.setText("已保存 " + recorded.size() + " 点");
        android.util.Log.d("TapBot", "saved " + current.name + " points=" + recorded.size());
    }

    @Override
    public void onDestroy() {
        if (controlView != null) {
            try { wm.removeView(controlView); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }
}
