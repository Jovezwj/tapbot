package com.kbot.tapbot;

import org.json.JSONObject;

/**
 * 单个点击点：屏幕坐标 + 按压时长 + 与下一点之间的间隔。
 * 坐标使用录制时的屏幕绝对坐标（px），回放时按当前屏幕等比缩放。
 */
public class TapPoint {
    public float x;
    public float y;
    public long holdMs = 80;      // 按压时长
    public long gapMs = 300;      // 点击后到下一个点击的间隔

    public TapPoint() {}

    public TapPoint(float x, float y, long holdMs, long gapMs) {
        this.x = x;
        this.y = y;
        this.holdMs = holdMs;
        this.gapMs = gapMs;
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("x", x);
        o.put("y", y);
        o.put("holdMs", holdMs);
        o.put("gapMs", gapMs);
        return o;
    }

    public static TapPoint fromJson(JSONObject o) {
        TapPoint p = new TapPoint();
        p.x = o.optDouble("x", 0);
        p.y = o.optDouble("y", 0);
        p.holdMs = o.optLong("holdMs", 80);
        p.gapMs = o.optLong("gapMs", 300);
        return p;
    }
}
