package com.kbot.tapbot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 一段录制的点击轨迹脚本。
 * loopCount: -1 表示无限循环，>=0 表示重复次数（0 视为不重复，只回放一遍）。
 */
public class TapScript {
    public String name = "脚本1";
    public List<TapPoint> points = new ArrayList<>();
    public int loopCount = 1;
    public int recordScreenW = 0;   // 录制时屏幕宽（用于回放缩放）
    public int recordScreenH = 0;

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("loopCount", loopCount);
        o.put("recordScreenW", recordScreenW);
        o.put("recordScreenH", recordScreenH);
        JSONArray arr = new JSONArray();
        for (TapPoint p : points) arr.put(p.toJson());
        o.put("points", arr);
        return o;
    }

    public static TapScript fromJson(JSONObject o) {
        TapScript s = new TapScript();
        s.name = o.optString("name", "脚本1");
        s.loopCount = o.optInt("loopCount", 1);
        s.recordScreenW = o.optInt("recordScreenW", 0);
        s.recordScreenH = o.optInt("recordScreenH", 0);
        JSONArray arr = o.optJSONArray("points");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                s.points.add(TapPoint.fromJson(arr.optJSONObject(i)));
            }
        }
        return s;
    }
}
