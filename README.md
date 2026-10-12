# Tempo · 间歇训练

Tempo 是一个原生 Android HIIT 计时器，用清晰的倒计时、阶段提示和训练计划管理，帮助你按自己的节奏完成间歇训练。

- 包名：`io.github.leychan.tempo`
- 当前版本：`1.2.0`
- 最低系统：Android 8.0（API 26）
- 技术：Kotlin、Jetpack Compose、Material 3
- 仓库：[leychan/tempo](https://github.com/leychan/tempo)

## 功能

| 模块 | 支持内容 |
| --- | --- |
| 间歇训练 | 准备、锻炼、普通休息、循环间休息自动切换 |
| 计时控制 | 开始、暂停、继续、确认重置、完成后再次训练 |
| 训练进度 | 当前阶段、阶段剩余时间、下一阶段、总剩余时间、周期／循环进度 |
| 参数设置 | 时间通过分／秒滚轮弹窗选择，次数通过数量滚轮弹窗选择 |
| 训练计划 | 新增、命名、选择、编辑、确认删除，本地保存 |
| 日夜主题 | 跟随系统、白天、夜间，选择后立即生效并持久保存 |
| 训练辅助 | 阶段切换、最后三秒、完成提示音，可关闭；运行时保持屏幕常亮 |
| 进度恢复 | 离开应用或锁屏自动暂停，重新打开后手动继续 |

计时页的操作按钮固定在底部；下一阶段、周期和循环进度在常规竖屏布局的首屏展示。白天与夜间主题覆盖训练页、计划列表、编辑页、选择器、对话框及系统栏。

当前版本聚焦 HIIT 与本地训练计划，不包含独立秒表、账号、云同步和后台持续计时。

## 使用方式

1. 在训练页点击「调整」，设置计划名称及训练参数。
2. 点击时间参数，滚动选择分钟和秒；点击周期数或循环数，选择数量。
3. 弹窗「确定」会回填参数，「取消」保留原值。编辑完成后点击「保存计划」。
4. 点击「开始训练」。训练过程中可暂停、继续或重置。
5. 在「我的计划」管理多个训练方案。
6. 点击训练页右上角的主题图标，选择「跟随系统／白天／夜间」；旁边的声音图标控制提示音。

开始训练后，当前计划的编辑和删除会锁定。切换计划需要确认结束当前训练；至少保留一个计划。

## 训练规则

- 准备阶段在整次训练开始时只执行一次。
- 每个循环包含指定次数的锻炼，相邻锻炼之间执行普通休息。
- 每个循环最后一次锻炼后跳过普通休息。
- 相邻循环之间执行循环间休息。
- 最后一个循环完成后直接结束训练。
- 时长为 0 的可选阶段自动跳过。

总时长（秒）：

```text
准备 + 循环数 × [周期数 × 锻炼 + (周期数 - 1) × 普通休息]
     + (循环数 - 1) × 循环间休息
```

默认「轻量间歇」计划：

| 参数 | 值 |
| --- | --- |
| 准备 | 00:03 |
| 锻炼 | 00:15 |
| 休息 | 00:10 |
| 周期数 | 4 |
| 循环数 | 2 |
| 循环间休息 | 02:00 |
| 总时长 | **05:03** |

另提供一个可编辑的「经典 Tabata」计划。单个时间参数最多 60 分钟，锻炼至少 1 秒，次数为 1～99，单次训练总时长不超过 24 小时。

## 本地开发

### 环境

| 工具 | 版本 |
| --- | --- |
| JDK | 17 或 21 |
| Gradle | 8.9，使用项目自带 Wrapper |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 2.0.21 |
| Android SDK Platform | 34 |
| Android Build Tools | 34.0.0 |

第一次构建需要联网下载依赖。Gradle 8.9 不适合使用 JDK 25，请在 Android Studio 中将 Gradle JDK 设为 17 或 21。

### Android Studio

```bash
git clone git@github.com:leychan/tempo.git
```

打开项目根目录，配置 Android SDK，等待 Gradle 同步完成，然后选择 `app` 运行配置和设备，点击 Run。

SDK 路径位于本机的 `local.properties`，例如：

```properties
sdk.dir=D:/soft/Android
```

该文件不会提交到 Git。

### 命令行

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

macOS / Linux：

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Debug 安装包：`app/build/outputs/apk/debug/app-debug.apk`。

测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`。Lint 报告：`app/build/reports/lint-results-debug.html`。

Debug 包用于开发和本地体验；发布版本需要自行配置签名，签名文件不会提交到仓库。

## 项目结构

```text
app/src/main/
├── java/io/github/leychan/tempo/
│   ├── MainActivity.kt          # 生命周期、提示音、系统栏和屏幕常亮
│   ├── TimerViewModel.kt        # 计时刷新、计划操作、主题偏好
│   ├── data/PlanStore.kt        # 本地状态保存及 JSON 编解码
│   ├── domain/
│   │   ├── TrainingPlan.kt      # 参数校验和阶段序列
│   │   ├── TimerEngine.kt       # 单调时钟计时状态机
│   │   └── ThemeMode.kt         # 跟随系统／白天／夜间
│   └── ui/
│       ├── TempoApp.kt          # 训练、计划、编辑页面
│       ├── ParameterPickers.kt  # 时间和数量滚轮弹窗
│       └── Theme.kt             # 两套配色与 Material 3 主题
└── res/                        # 应用图标、原生主题等

app/src/test/                   # JUnit 计时逻辑测试
gradle/wrapper/                  # 可复现的 Gradle 版本
tools/verify-device.ps1          # 可选的 ADB 真机验收辅助脚本
```

## 计时与数据

计时引擎使用 Android 的单调时钟计算实际经过时间，避免系统时间调整和累积刷新误差。暂停保留毫秒精度，迟到的刷新会自动定位到正确阶段。

训练计划、声音偏好、主题偏好及当前进度保存在本机的 SharedPreferences。运行进度约每秒保存一次，离开应用时再次保存；进程被意外终止时可能损失最后不足一秒的进度。恢复的活动训练始终处于暂停状态，不会在重新打开时自动开始。

App 不需要网络权限，不使用账号、分析服务或远程数据上传。数据备份及设备迁移规则关闭；卸载 App 会删除本地计划和偏好。

从早期调试包名 `com.leychan.tempo` 改为当前包名后，Android 会将其视为另一个应用。旧应用无需卸载，旧计划不会自动迁移到新包名。

## 验证

计时单元测试覆盖：

- 参考训练总时长 05:03。
- 每个循环最后一轮跳过普通休息。
- 零秒可选阶段和单周期训练。
- 暂停／继续的毫秒精度和重置。
- 刷新延迟跨越多个阶段。
- 进程恢复、非法参数、小时格式。

真机验收建议检查三个页面及两种主题，主题切换和重启后的偏好保存，时间／数量弹窗的确定与取消，以及后台暂停和恢复。ADB 脚本面向本机开发验收，需要已安装 debug 包、默认两个计划、训练页和无活动训练；通过 `-AdbPath` 与 `-Serial` 指定工具及设备。

本地安装包、参考产品图、开发截图、SDK 路径、构建缓存和签名文件均不提交到 Git。
