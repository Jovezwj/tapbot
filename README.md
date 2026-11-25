# TapBot — 定时模拟点击助手（Android）

免 Root 的 Android 自动化点击工具。核心能力：

- **录制操作轨迹**：在屏幕上点哪，就记录哪（坐标 + 按压时长 + 间隔）
- **指定时刻触发**：设定 时:分:秒，到点自动开始回放
- **息屏自动唤醒**：回放前自动亮屏（无密码直接操作）
- **前置 App 校验**：回放前确保目标 App 在前台，不在则自动启动；无法打开则终止并弹通知
- **循环回放**：可设循环次数（-1 无限）

## 技术原理

| 能力 | 实现方式 | 权限 |
|------|----------|------|
| 执行点击 | 无障碍服务 `performGlobalGesture` | 无障碍服务 |
| 录制坐标 | 无障碍服务 `onTouchEvent`（不挡屏幕） | 无障碍服务 |
| 前台检测 | 无障碍 `getRootInActiveWindow()` 取包名 | 无障碍服务 |
| 启动 App | `getLaunchIntentForPackage` | 无 |
| 亮屏唤醒 | `PowerManager.SCREEN_BRIGHT_WAKE_LOCK` | WAKE_LOCK |
| 控制悬浮窗 | `TYPE_APPLICATION_OVERLAY` | 显示在其他应用上层 |

## 使用步骤

1. **安装并打开 App**
2. **开启悬浮窗权限**：点「开启」→ 允许"显示在其他应用上层"
3. **开启无障碍服务**：点「开启」→ 在列表里打开 "TapBot 模拟点击助手"
4. **录制脚本**：
   - 点「开始录制」，屏幕左上角出现悬浮控制条
   - 点悬浮条「录」，去目标 App 里正常操作（点哪记哪）
   - 操作完点悬浮条「停」，脚本自动保存
5. **设置目标 App**（可选）：下拉选淘宝/微信/抖音等，或填任意包名；留空则不校验直接点
6. **设置定时时刻**：时 / 分 / 秒
7. **点「开始定时」**：到点自动亮屏 → 确保 App 在前台 → 回放轨迹

## 编译

```
# 用 Android Studio 打开 TapBot 目录，或直接：
gradle :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

- minSdk 24（Android 7.0），targetSdk 34
- 依赖：appcompat / material / constraintlayout（Gradle 自动拉取）

## 包结构

```
app/src/main/java/com/kbot/tapbot/
├── MainActivity.java            主界面：权限/目标App/定时/脚本/执行
├── TapAccessibilityService.java 无障碍：点击手势/触摸录制/前台检测/启动App
├── OverlayService.java          悬浮窗控制条（录/停/关）
├── TapEngine.java               回放引擎：定时/亮屏/前台校验/循环回放/失败通知
├── TapScript.java               脚本数据模型
├── TapPoint.java                单点击点模型
└── ScriptStore.java             脚本持久化（SharedPreferences + JSON）
```

## 注意事项

- 录制坐标是**屏幕绝对坐标**，回放时按当前屏幕等比缩放，建议录制与执行用同一台手机
- 无障碍服务是系统级能力，请只授予给本应用，不要授予给来源不明的 App
- 部分厂商（小米/华为/OPPO 等）需额外在「自启动」「后台运行」里给本应用开白名单，避免被杀后台导致定时不触发
- 自动点击请遵守目标 App 的服务条款，谨慎用于抢购等场景
