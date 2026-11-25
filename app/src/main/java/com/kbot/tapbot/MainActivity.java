package com.kbot.tapbot;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements TapEngine.Listener {

    private static final String PREF = "tapbot_settings";
    private static final String KEY_LAST_SCRIPT = "last_script";

    private TapEngine engine;
    private ScriptStore store;
    private SharedPreferences prefs;

    private TextView tvOverlay, tvAccess, tvState, tvCountdown, tvProgress, tvLog;
    private Button btnOverlay, btnAccess, btnRecord, btnSchedule, btnNow, btnStop;
    private Spinner spinnerApp;
    private EditText etPkg, etHour, etMin, etSec, etLoop;
    private Switch switchWake;
    private ListView listScripts;

    private final List<String> appNames = new ArrayList<>();
    private final List<String> appPkgs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        engine = new TapEngine(this);
        engine.setListener(this);
        store = new ScriptStore(this);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);

        bindViews();
        fillAppSpinner();
        loadPrefs();
        refreshPermissions();
        refreshScripts();
        wireButtons();
    }

    private void bindViews() {
        tvOverlay = findViewById(R.id.tv_overlay_status);
        tvAccess = findViewById(R.id.tv_access_status);
        tvState = findViewById(R.id.tv_state);
        tvCountdown = findViewById(R.id.tv_countdown);
        tvProgress = findViewById(R.id.tv_progress);
        tvLog = findViewById(R.id.tv_log);
        btnOverlay = findViewById(R.id.btn_overlay);
        btnAccess = findViewById(R.id.btn_access);
        btnRecord = findViewById(R.id.btn_record);
        btnSchedule = findViewById(R.id.btn_schedule);
        btnNow = findViewById(R.id.btn_now);
        btnStop = findViewById(R.id.btn_stop);
        spinnerApp = findViewById(R.id.spinner_app);
        etPkg = findViewById(R.id.et_pkg);
        etHour = findViewById(R.id.et_hour);
        etMin = findViewById(R.id.et_min);
        etSec = findViewById(R.id.et_sec);
        etLoop = findViewById(R.id.et_loop);
        switchWake = findViewById(R.id.switch_wake);
        listScripts = findViewById(R.id.list_scripts);
    }

    private void fillAppSpinner() {
        appNames.add("（不指定，直接点击）");
        appPkgs.add("");
        appNames.add("淘宝"); appPkgs.add("com.taobao.taobao");
        appNames.add("微信"); appPkgs.add("com.tencent.mm");
        appNames.add("抖音"); appPkgs.add("com.ss.android.ugc.aweme");
        appNames.add("京东"); appPkgs.add("com.jingdong.app.mall");
        appNames.add("拼多多"); appPkgs.add("com.xunmeng.pinduoduo");
        appNames.add("自定义包名…"); appPkgs.add("__custom__");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, appNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerApp.setAdapter(adapter);
        spinnerApp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                if (appPkgs.get(pos).equals("__custom__")) {
                    etPkg.setVisibility(View.VISIBLE);
                    etPkg.requestFocus();
                } else {
                    etPkg.setText(appPkgs.get(pos));
                    etPkg.setVisibility(View.VISIBLE);
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private String currentTargetPkg() {
        String pkg = etPkg.getText().toString().trim();
        if (pkg.isEmpty() || pkg.equals("__custom__")) return "";
        return pkg;
    }

    private void loadPrefs() {
        switchWake.setChecked(engine.isWakeEnabled());
        String last = prefs.getString(KEY_LAST_SCRIPT, "");
        if (!last.isEmpty()) {
            // 选中最后脚本
            List<String> names = store.listNames();
            for (int i = 0; i < names.size(); i++) {
                if (names.get(i).equals(last)) {
                    listScripts.setItemChecked(i, true);
                    break;
                }
            }
        }
    }

    private void savePrefs() {
        engine.setTargetPkg(currentTargetPkg());
        engine.setWakeEnabled(switchWake.isChecked());
    }

    private void refreshPermissions() {
        boolean overlay = Settings.canDrawOverlays(this);
        tvOverlay.setText("悬浮窗：" + (overlay ? "已开启" : "未开启"));
        tvOverlay.setTextColor(overlay ? Color.parseColor("#4ADE80") : Color.parseColor("#F87171"));
        btnOverlay.setText(overlay ? "已开" : "开启");

        boolean acc = TapAccessibilityService.instance.isReady();
        tvAccess.setText("无障碍：" + (acc ? "已开启" : "未开启"));
        tvAccess.setTextColor(acc ? Color.parseColor("#4ADE80") : Color.parseColor("#F87171"));
        btnAccess.setText(acc ? "已开" : "开启");
    }

    private void refreshScripts() {
        List<String> names = store.listNames();
        listScripts.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_single_choice, names));
        listScripts.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
    }

    private void wireButtons() {
        btnOverlay.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        btnAccess.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(i);
        });
        switchWake.setOnCheckedChangeListener((buttonView, isChecked) -> engine.setWakeEnabled(isChecked));

        btnRecord.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!TapAccessibilityService.instance.isReady()) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show();
                return;
            }
            requestNotifyPermission();
            startService(new Intent(this, OverlayService.class));
            Toast.makeText(this, "悬浮窗已显示，点「录」开始录制", Toast.LENGTH_SHORT).show();
        });

        btnSchedule.setOnClickListener(v -> {
            savePrefs();
            String script = selectedScript();
            if (script == null) { Toast.makeText(this, "请先录制并选中一个脚本", Toast.LENGTH_SHORT).show(); return; }
            int h = parseInt(etHour, 0), m = parseInt(etMin, 0), s = parseInt(etSec, 0);
            if (h > 23 || m > 59 || s > 59) { Toast.makeText(this, "时间格式错误", Toast.LENGTH_SHORT).show(); return; }
            applyLoopToScript(script);
            prefs.edit().putString(KEY_LAST_SCRIPT, script).apply();
            engine.startScheduled(script, h, m, s);
            Toast.makeText(this, "定时已启动", Toast.LENGTH_SHORT).show();
        });

        btnNow.setOnClickListener(v -> {
            savePrefs();
            String script = selectedScript();
            if (script == null) { Toast.makeText(this, "请先录制并选中一个脚本", Toast.LENGTH_SHORT).show(); return; }
            applyLoopToScript(script);
            prefs.edit().putString(KEY_LAST_SCRIPT, script).apply();
            engine.startNow(script);
            Toast.makeText(this, "立即执行", Toast.LENGTH_SHORT).show();
        });

        btnStop.setOnClickListener(v -> {
            engine.stopAll();
            TapAccessibilityService.instance.stopRecording();
            stopService(new Intent(this, OverlayService.class));
            tvState.setText("状态：已停止");
            tvState.setTextColor(Color.parseColor("#94A3B8"));
            appendLog("已手动停止");
        });

        listScripts.setOnItemClickListener((parent, view, pos, id) -> {
            List<String> names = store.listNames();
            if (pos >= 0 && pos < names.size()) {
                prefs.edit().putString(KEY_LAST_SCRIPT, names.get(pos)).apply();
            }
        });
    }

    private String selectedScript() {
        int pos = listScripts.getCheckedItemPosition();
        List<String> names = store.listNames();
        if (pos >= 0 && pos < names.size()) return names.get(pos);
        return null;
    }

    private void applyLoopToScript(String name) {
        TapScript s = store.load(name);
        if (s == null) return;
        int loop = parseInt(etLoop, 1);
        s.loopCount = loop;
        store.save(s);
    }

    private void requestNotifyPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }

    private int parseInt(EditText et, int def) {
        try {
            return Integer.parseInt(et.getText().toString().trim());
        } catch (Exception e) {
            return def;
        }
    }

    // ---- TapEngine.Listener ----
    @Override
    public void onCountdown(String text) {
        runOnUiThread(() -> tvCountdown.setText("倒计时：" + text));
    }

    @Override
    public void onReplayProgress(int done, int total) {
        runOnUiThread(() -> tvProgress.setText("进度：" + done + " / " + total));
    }

    @Override
    public void onStateChange(String state) {
        runOnUiThread(() -> {
            String txt, color;
            switch (state) {
                case "counting": txt = "状态：倒计时中"; color = "#FBBF24"; break;
                case "replaying": txt = "状态：回放中"; color = "#4ADE80"; break;
                case "done": txt = "状态：已完成"; color = "#4ADE80"; break;
                case "terminated": txt = "状态：已终止"; color = "#F87171"; break;
                default: txt = "状态：空闲"; color = "#94A3B8";
            }
            tvState.setText(txt);
            tvState.setTextColor(Color.parseColor(color));
        });
    }

    @Override
    public void onLog(String msg) {
        runOnUiThread(() -> appendLog(msg));
    }

    private void appendLog(String msg) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date());
        String line = "[" + time + "] " + msg + "\n";
        String cur = tvLog.getText().toString();
        tvLog.setText(line + cur);
        // 限制长度
        if (cur.length() > 4000) tvLog.setText(cur.substring(0, 4000));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissions();
        refreshScripts();
    }
}
