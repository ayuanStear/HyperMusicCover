# HyperMusicCover

把正在播放的专辑封面做成 HyperOS 锁屏的封面 / 壁纸，并把锁屏通知收成一个「通知岛」胶囊 —— 一个基于 LSPosed（Xposed）的 HyperOS 锁屏美化模块。

包名 `com.os4.musiccover`，当前版本 `0.0.6`（versionCode 7），`minSdk 35 / targetSdk 37`，只面向 HyperOS（小米）+ Android 15 及以上。
本仓库是 [zyl6932/HyperMusicCover](https://github.com/zyl6932/HyperMusicCover) 的个人分支，在其基础上修复通知岛与收起手势，并清理测试用探针代码。

## 功能与作用

### 锁屏媒体卡片与胶囊岛

- 播放音乐时，锁屏底部出现媒体胶囊（封面 + 歌名 + 播放状态）。
- 点按 / 上拉胶囊会展开成大卡片：封面、标题、进度、播放控制按钮。
- 大卡片下拉可以收回胶囊；卡片与胶囊之间的形变是连续的（`MiniCardMorph`），不是简单显隐。
- 封面出现时锁屏时钟会跟着缩小、玻璃效果跟着变化，退出时再放大回来。
- 支持“封面模式”开关：关掉后锁屏保持系统原生媒体卡片。

### 封面（锁屏壁纸）

- 两种展现形式：全屏封面（把封面推到锁屏壁纸）与方形卡片。
- 卡片可调大小、位置（靠上 / 居中 / 靠下）、圆角（圆 / 方）。
- 可调封面纵向位置（bias），适配不同分辨率和时钟样式。

### 锁屏歌词

- 在锁屏上跟随当前播放显示歌词，可单独开关。
- 歌词来源支持多个渠道：Apple Music、网易云、QQ 音乐、酷我、本地文件、网页歌词等，按可用性自动回退（`LyricSource` / `NcmLyrics` / `QqLyrics` / `KuwoLyrics` / `AppleLyrics` / `LocalLyrics` / `WebLyrics`）。
- 支持逐字 / 逐行样式、翻译、模糊与淡出等显示调整。

### 通知岛（本分支重点修复）

- 锁屏通知不再一条条铺开，而是收成一个胶囊岛：左侧是**最新那条通知的图标**，文字是合计的「通知 / N 条通知」。
- 胶囊下拉展开成通知列表，列表可以上滑滚动查看较早的通知。
- 列表滚上去之后，第一次下拉是**把列表滚回第一条**；回到第一条后再下拉，才把整堆通知收进胶囊。
- 收起时各行按**倒叙**依次进岛（离岛最远的那条先进），不会出现“先拉的先回去”。
- 点按通知打开对应应用；删除、清空等仍走系统行为。

### 其它

- 隐藏桌面图标：隐藏后可从拨号盘输入 `*#*#95993926#*#*` 打开设置，或从 LSPosed 模块列表进入。
- 设置项：主题（跟随系统 / 浅色 / 深色 / Monet）、语言（简体中文 / English）、悬浮导航栏、液态玻璃、背景模糊。
- 数据管理：设置导出 / 导入（JSON），第三方便捷迁移。
- 快捷重启：重启系统界面、重启壁纸。
## 运行要求

- 小米 HyperOS（Android 15 及以上；`minSdk 35`，`targetSdk 37`），已 Root。
- 已安装并启用 LSPosed（Xposed API 102）。
- 模块作用域：

  | 作用域 | 作用 |
  | --- | --- |
  | `com.android.systemui` | 锁屏媒体卡片、通知岛、时钟联动、锁屏歌词 |
  | `com.miui.miwallpaper` | 把封面推成锁屏壁纸 |
  | `com.miui.aod` | 息屏显示（封面 / 时钟） |
  | `miui.systemui.plugin` | 小米系统 UI 插件里的岛与图标资源 |
  | `com.apple.android.music` | Apple Music 的歌词来源 |

## 安装与使用

1. 安装 APK。
2. 在 LSPosed 里勾选本模块并选中上面的全部作用域。
3. 重启系统界面（模块设置里有「重启系统界面」，或直接重启手机）。
4. 播放音乐，锁屏上就会出现媒体胶囊；下拉 / 点按即可使用对应功能。
5. 如果隐藏了桌面图标：拨号盘输入 `*#*#95993926#*#*`，或从 LSPosed 模块列表点进设置。

## 手势一览

| 位置 | 手势 | 结果 |
| --- | --- | --- |
| 锁屏媒体胶囊 | 点按 / 上拉 | 展开成媒体大卡片 |
| 媒体大卡片 | 下拉 | 收回胶囊 |
| 通知胶囊岛 | 下拉 | 展开通知列表 |
| 通知列表 | 上滑 | 滚动查看更早的通知 |
| 通知列表（已滚上去） | 下拉第一下 | 先把列表滚回第一条（不收岛） |
| 通知列表（已在第一条） | 下拉 | 把整堆通知按倒叙收进胶囊 |
| 通知行 | 点按 | 打开对应应用的通知 |
## 构建

需要 JDK 17 与 Android SDK（platform 37、build-tools 37）：

```bash
./gradlew :app:assembleDebug     # 调试包，含诊断日志
./gradlew :app:assembleRelease   # 正式包（R8 压缩）
```

- Release 签名优先读根目录 `keystore.properties`（或环境变量 `SIGNING_STORE_FILE` / `SIGNING_STORE_PASSWORD` / `SIGNING_KEY_ALIAS` / `SIGNING_KEY_PASSWORD`）；都没有时回退到本机 `.android-user/debug.keystore`，这样连续本地构建可以直接 `adb install -r` 覆盖安装，不用卸载。
- 产物：`app/build/outputs/apk/debug|release/`。

## 代码结构

主要都在 `app/src/main/java/com/os4/musiccover/`：

| 文件 | 作用 |
| --- | --- |
| `Main.java` | 模块入口：Xposed 钩子、设置项、`op` 诊断指令 |
| `MiniPlayerRuntime.kt` | 媒体卡片 / 胶囊岛运行时：形变调度、拖动、命中判定 |
| `MiniCardMorph.kt` | 卡片与胶囊之间形变的几何与弹簧 |
| `LockIslands.kt` | 通知岛：通知的读取、聚合、堆叠与列表 |
| `CoverPush.java` / `WallpaperProbe.java` | 把封面推到锁屏壁纸 / 息屏显示 |
| `ClockCollapse.java` / `ClockLifecycleGate.java` | 时钟随封面缩放、息屏与唤醒的生命周期 |
| `LockLyrics.java` / `LyricView.java` / `LyricSource.java` 等 | 锁屏歌词与来源 |
| `MiniPlayerView.kt` / `MiniPlayerActivity.kt` / `MainActivity.kt` | 锁屏上的视图与设置界面 |
| `Xp.java` | 反射工具 |

`docs/` 放功能说明：

- `通知岛胶囊与收起手势修改说明.md`：本分支对通知岛图标与收起手势的修复说明
- `动画抽搐修复说明.md`：锁屏 / 息屏切换时钟抽搐的修复记录

## 已知问题

- 个别情况下音乐卡片会在「原生卡片」与「胶囊」之间反复切换（随曲目变化触发，约每秒一次），切换时通知岛会跟着上下移动。见 `docs/通知岛胶囊与收起手势修改说明.md` 末尾。
- 只在小米 HyperOS 上验证；其它 ROM 未做适配。
- 模块依赖系统内部字段与类名，HyperOS 大版本升级后可能需要跟着改。

## 来源与许可

- 上游项目：[zyl6932/HyperMusicCover](https://github.com/zyl6932/HyperMusicCover)。
- 本仓库是个人分支，改动集中在通知岛（胶囊通知的图标与收起手势）以及相关清理。
- 许可：GNU Affero General Public License v3.0，见 `LICENSE`、`NOTICE`。基于本项目修改并分发时，同样需要以 AGPL-3.0 开源。