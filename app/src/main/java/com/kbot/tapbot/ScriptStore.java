package com.kbot.tapbot;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 脚本持久化：保存在 SharedPreferences 中（JSON 字符串），key 为脚本名。
 */
public class ScriptStore {
    private static final String PREF = "tapbot_scripts";
    private static final String KEY_INDEX = "script_index";

    private final Context ctx;

    public ScriptStore(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public List<String> listNames() {
        try {
            JSONArray arr = new JSONArray(prefs().getString(KEY_INDEX, "[]"));
            List<String> names = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) names.add(arr.getString(i));
            return names;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void save(TapScript s) {
        try {
            prefs().edit().putString(s.name, s.toJson().toString()).apply();
            JSONArray arr = new JSONArray();
            boolean exists = false;
            for (String n : listNames()) {
                if (n.equals(s.name)) exists = true;
                arr.put(n);
            }
            if (!exists) arr.put(s.name);
            prefs().edit().putString(KEY_INDEX, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public TapScript load(String name) {
        try {
            String json = prefs().getString(name, null);
            if (json == null) return null;
            return TapScript.fromJson(new JSONObject(json));
        } catch (Exception e) {
            return null;
        }
    }

    public void delete(String name) {
        JSONArray arr = new JSONArray();
        for (String n : listNames()) if (!n.equals(name)) arr.put(n);
        prefs().edit().remove(name).putString(KEY_INDEX, arr.toString()).apply();
    }
}
