package com.kbot.tapbot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityWindowInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 无障碍服务：免 Root 的核心能力。
 *  1. performGlobalGesture 在屏幕绝对坐标执行点击
 *  2. onTouchEvent 监听全局触摸事件用于录制（不拦截、不挡屏幕）
 *  3. getRootInActiveWindow 获取当前前台 App 包名
 *  4. 启动目标 App
 */
public class TapAccessibilityService extends AccessibilityService {

    public static TapAccessibilityService instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    /** 录制开关与回调 */
    public volatile boolean recording = false;
    public interface RecordListener {
        void onTapDown(float x, float y);
        void onTapUp(float x, float y);
    }
    public volatile RecordListener recordListener;

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 录制走 onTouchEvent，这里无需处理
    }

    @Override
    public void onInterrupt() {}

    /**
     * 全局触摸事件回调（API 24+）：不消费事件，仅用于录制坐标。
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (recording && recordListener != null) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                recordListener.onTapDown(event.getRawX(), event.getRawY());
            } else if (action == MotionEvent.ACTION_UP) {
                recordListener.onTapUp(event.getRawX(), event.getRawY());
            }
        }
        return false; // 不拦截，事件继续传给系统
    }

    public boolean isReady() {
        return instance != null;
    }

    public void startRecording(RecordListener l) {
        recordListener = l;
        recording = true;
    }

    public void stopRecording() {
        recording = false;
        recordListener = null;
    }

    /**
     * 在绝对坐标 (x,y) 处点击一次。
     */
    public void tap(final float x, final float y, final long holdMs, final Runnable onDone) {
        if (instance == null) {
            if (onDone != null) main.post(onDone);
            return;
        }
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, holdMs));
        final GestureDescription gesture = builder.build();
        executor.execute(() -> {
            try {
                performGlobalGesture(gesture, null, null);
            } catch (Throwable ignored) {}
            if (onDone != null) main.post(onDone);
        });
    }

    /**
     * 当前前台 App 包名。
     */
    public String getForegroundPackage() {
        try {
            AccessibilityWindowInfo root = getRootInActiveWindow();
            if (root != null) {
                return root.getPackageName();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * 启动目标 App。返回是否找到启动器。
     */
    public boolean launchApp(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
