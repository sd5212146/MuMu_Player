# MuMu Player - RG Rotate 专属音乐播放器 / Music Player for RG Rotate

![Version](https://img.shields.io/badge/version-0.9.89--beta-blue)
![Platform](https://img.shields.io/badge/platform-Android%2010%2B-brightgreen)

<p align="center">
  <b>中文</b> | <a href="#english">English</a>
</p>

---

`<a name="chinese"></a>`

# 🇨🇳 中文

## 📖 项目简介

**MuMu Player** 是一款专为 **Anbernic RG Rotate** 打造的 Android 音乐播放器。充分利用 RG Rotate 独特的 3.5 英寸 720×720 方形屏幕和可旋转转轴设计，提供横竖屏自适应切换的沉浸式听歌体验。

---

## 📱 关于 RG Rotate

**Anbernic RG Rotate** 是一款独特的旋转屏安卓掌机，于 2026 年 5 月发布：

| 参数      | 规格                                                 |
| --------- | ---------------------------------------------------- |
| 屏幕      | 3.5 英寸 IPS LCD，720×720 方形触摸屏，1670 万色     |
| 处理器    | 紫光展锐 T618（8 核，2×A75@2.0GHz + 6×A55@2.0GHz） |
| GPU       | Mali G52 @850MHz                                     |
| 内存/存储 | 3GB LPDDR4X RAM + 32GB eMMC ROM                      |
| 系统      | Android 12                                           |
| 电池      | 2000mAh，5V/2A Type-C 充电                           |
| 网络      | Wi-Fi 5（802.11ac）、蓝牙 5.0                        |
| 扩展      | microSD 卡槽（最大 2TB）                             |
| 重量      | ≈167g（极夜黑）/ ≈197g（激光银）                   |
| 尺寸      | 80×80×21.6mm                                       |
| 特色      | 合金转轴，90° 横竖屏旋转，六轴陀螺仪，可替换高肩键  |

---

## ✨ 功能特性 / Features

### 🎵 播放功能 / Playback

- **格式支持**：MP3、FLAC、APE、WAV、OGG、AAC、WMA 等常见音频格式
- **歌单管理**：浏览所有歌曲、按文件夹分组、搜索歌曲
- **收藏夹**：红心收藏歌曲，支持收藏列表管理
- **播放模式**：顺序播放、单曲循环、列表循环、随机播放
- **倍速播放**：0.5× ~ 2.0× 可调播放速度
- **进度控制**：拖动进度条跳转、上一首/下一首

### 🎨 屏幕适配 / Screen Adaptation

- **方形屏优化**：为 RG Rotate 720×720 正方形屏幕量身定制
- **横竖屏切换**：跟随 RG Rotate 转轴旋转自动切换横竖布局
- **多种比例适配**：设置中可选择 16:9、3:2、4:3、1:1、8:7 等显示比例
- **自适应模式**：开启后内容自动填满屏幕
- **封面大小可调**：每个比例可独立调节专辑封面在屏幕中的占比（15%~85%）

### 🖼️ 专辑与歌词 / Album Art & Lyrics

- **专辑封面**：优先显示本地封面（MediaStore/侧边.jpg），支持网络获取
- **黑胶动画**：无封面时显示旋转黑胶唱片动画，播放时持续旋转
- **在线歌词**：自动获取歌词（网易云音乐、QQ 音乐、酷狗音乐多源自动切换）
- **歌词缓存**：已获取的歌词本地缓存，离线可用
- **歌词偏移**：歌词不同步时可手动调整偏移量
- **字体大小**：5 档歌词字体大小可调（小/默认/大/特大/超大）

### 🎛️ 主题系统 / Themes

- **预设主题**：经典黑、黑金、白银、深海蓝、樱花粉、复古橙、墨玉绿、暗夜紫
- **自定义主题**：自由配置背景色、文字色、主题色、强调色、按钮色
- **金属质感**：部分主题带有 Walkman 风格金属拉丝纹理效果

### 🔒 锁屏与通知 / Lock Screen & Notification

- **锁屏控制**：锁屏时显示播放控制界面（时钟、歌名、封面、进度条、切歌按钮）
- **通知栏控件**：通知栏显示专辑封面、歌名、播放控制按钮（支持 Android 13+）
- **全屏锁屏按钮**：全屏播放时右侧显示屏幕锁定按钮，防止误触

### 🎮 其他特色 / Additional Features

- **游戏手柄**：支持 L1/R1 双击切歌，Select 暂停/播放（适配 RG Rotate 肩键）
- **NAS 播放**：支持通过网络共享（SMB）播放 NAS 上的音乐文件
- **均衡器**：系统均衡器，支持多种音效预设
- **歌词封面管理**：管理已缓存的歌词和封面，批量清除
- **文件夹浏览**：按文件夹分组浏览歌曲
- **歌曲搜索**：按歌名/歌手/专辑搜索
- **隐藏文件夹**：可标记隐藏文件夹，扫描时跳过

---

## 🎮 使用方法 / Usage Guide

### 首次使用 / First Use

1. **安装应用** → 将 APK 安装到 RG Rotate 上
2. **授予权限** → 首次启动时允许"文件管理"权限，用于扫描音乐文件
3. **扫描歌曲** → 应用会自动扫描设备上的音频文件，也可在设置中手动指定扫描路径
4. **开始播放** → 点击任意歌曲即可开始播放

### 主界面 / Main Interface

```
┌──────────────────────────────┐
│  [菜单]  歌曲列表/文件夹   [搜索]│
├──────────────────────────────┤
│  🎵 歌曲1                    │
│  🎵 歌曲2                    │
│  🎵 歌曲3          ← 点击播放 │
│  ...                        │
├──────────────────────────────┤
│  [底部导航栏]                 │
│  歌曲  文件夹  设置           │
└──────────────────────────────┘
```

- **底部导航栏**：切换"所有歌曲"、"文件夹浏览"、"设置"三个页面
- **点击歌曲**：开始播放并进入播放页面

### 播放页面 / Now Playing

#### 正常模式（横屏） / Landscape Mode

```
┌──────────────────────────────┐
│  ┌──────────┐  歌名(居中)     │
│  │          │  歌手           │
│  │ 专辑封面  │                │
│  │          │  歌词滚动       │
│  │          │                │
│  └──────────┘                │
│  ────━━━━━━━━━━━━━━━━━━────  │
│  0:00                    3:30 │
│  [↺] [◀◀] [▶/⏸] [▶▶] [♡] │
│          [⛶ 全屏]            │
└──────────────────────────────┘
```

- **左侧**：专辑封面（点击进入全屏模式）
- **右侧顶部**：歌名（居中）+ 歌手
- **右侧中部**：歌词滚动显示（当前唱到行在第一行）
- **右侧底部**：进度条 + 时间显示
- **底部控制栏**：播放模式、上一首、播放/暂停、下一首、收藏
- **全屏按钮**：右上角 ⛶ 图标进入全屏

#### 竖屏模式 / Portrait Mode

旋转 RG Rotate 屏幕 90° 后，在设置中开启竖屏模式即可：

```
┌──────────────┐
│ (N/N) 歌名   │
│       歌手   │
│              │
│   ┌──────┐   │
│   │ 封面  │   │
│   │ 方形  │   │
│   └──────┘   │
│ 歌词(2行)    │
│ ─────━━━━── │
│ [↺][◀◀][▶][▶▶][♡]│
└──────────────┘
```

- **顶部**：左上角 (N/N) 歌曲序号，居中歌名+歌手
- **中部**：全宽方形封面
- **下部**：2 行歌词 + 进度条 + 播放控件

#### 全屏模式 / Fullscreen Mode

- **进入**：在播放页面点击专辑封面，或上滑手势（>150px）
- **退出**：下滑手势，或点击顶部 X 按钮
- **切换歌词**：上下滑动在全屏封面和歌词模式间切换
- **锁屏**：右侧中间锁形按钮锁定/解锁屏幕（可在设置中显示/隐藏）

### 设置页面 / Settings

#### 音乐设置

| 功能           | 说明                         |
| -------------- | ---------------------------- |
| 扫描音乐       | 手动触发重新扫描             |
| 指定扫描路径   | 设置要扫描的文件夹（可多选） |
| 隐藏文件夹     | 标记不需要扫描的文件夹       |
| 启动后自动播放 | 应用启动时自动继续上次播放   |
| NAS 设置       | 配置网络共享的 NAS 路径      |

#### 系统设置

| 功能         | 说明                                        |
| ------------ | ------------------------------------------- |
| 播放器主题   | 选择或自定义 Walkman 风格主题               |
| 屏幕比例     | 选择显示比例（自适应/16:9/3:2/4:3/1:1/8:7） |
| 封面大小占比 | 调节封面占屏幕比例（横屏模式下生效）        |
| 锁屏控制     | 开启后锁屏时显示播放控制界面                |
| 显示锁屏按钮 | 全屏播放时右侧显示锁屏按钮                  |
| 通知栏控件   | 开启后通知栏显示播放控制（Android 13 兼容） |
| 竖屏模式     | 强制竖屏布局                                |
| 自动识别方向 | 自动检测屏幕方向切换横竖布局                |
| 游戏手柄     | 开启后支持 L1/R1 切歌                       |
| 均衡器       | 打开系统均衡器                              |

#### 歌词与封面

| 功能         | 说明                                       |
| ------------ | ------------------------------------------ |
| 歌词来源顺序 | 设置在线歌词获取优先级（网易云→QQ→酷狗） |
| 歌词字体大小 | 5 档可调                                   |
| 歌词封面管理 | 管理已缓存数据                             |

---

## 🔧 高级技巧 / Tips

### 手柄操作 / Gamepad Controls

| 按键       | 功能      |
| ---------- | --------- |
| L1（双击） | 上一首    |
| R1（双击） | 下一首    |
| Select     | 暂停/播放 |

### 通知栏 / Notification

在设置中开启"通知栏控件"后，通知栏会显示专辑封面、歌名和控制按钮：

- **折叠状态**：显示 3 个按钮（上一首、播放/暂停、下一首）
- **展开状态**：显示完整 5 个按钮（播放模式、上一首、播放/暂停、下一首、收藏）

---

## 🔨 开发 / Development

```bash
# 克隆项目 / Clone
git clone <repo-url>

# 使用 Gradle 构建 / Build with Gradle
cd YYplayer
./gradlew assembleDebug

# 构建产物位于 / Build output at
# app/build/outputs/apk/debug/app-debug.apk
```

### 技术栈 / Tech Stack

| 类别                     | 技术                        |
| ------------------------ | --------------------------- |
| 语言 / Language          | Kotlin                      |
| UI 框架 / UI Framework   | Jetpack Compose + Material3 |
| 播放器 / Player          | ExoPlayer (AndroidX Media3) |
| 图片加载 / Image Loading | Coil (Kotlin coroutines)    |
| 架构 / Architecture      | ViewModel + StateFlow       |
| 最低 SDK / Min SDK       | Android 10 (API 29)         |
| 目标 SDK / Target SDK    | Android 15 (API 35)         |

### 项目结构 / Project Structure

```
app/src/main/java/com/example/yyplayer/
├── data/
│   ├── lyrics/           # 歌词 & 封面获取/缓存
│   ├── model/            # 数据模型（Song, Lyrics, PlayMode 等）
│   ├── repository/       # 数据仓库（设置、收藏、主题等）
│   └── scanner/          # 音乐文件扫描器
├── player/
│   ├── MusicService.kt   # 后台音乐播放服务
│   ├── PlayerController.kt # ExoPlayer 单例管理
│   ├── LockScreenControlActivity.kt # 锁屏控制界面
│   ├── EqualizerController.kt # 均衡器
│   └── GamepadHelper.kt  # 游戏手柄支持
├── ui/
│   ├── navigation/       # 导航
│   ├── screens/          # 页面（NowPlaying, Settings 等）
│   ├── theme/            # 主题系统
│   └── viewmodel/        # ViewModel 层
├── MainActivity.kt       # 主 Activity
└── YYPlayerApplication.kt
```

---

## 📄 许可 / License

本项目仅供个人学习与交流使用。
This project is for personal learning and communication purposes only.

---

`<a name="english"></a>`

# 🇬🇧 English

## 📖 About

**MuMu Player** is an Android music player designed specifically for **Anbernic RG Rotate**, a unique swivel-screen retro gaming handheld with a 3.5-inch 720×720 square display. The player fully utilizes the rotating hinge design, supporting automatic landscape/portrait layout switching.

---

## 📱 About RG Rotate

Released in May 2026, the **Anbernic RG Rotate** is a swivel-screen Android handheld:

| Spec         | Detail                                        |
| ------------ | --------------------------------------------- |
| Display      | 3.5" IPS LCD, 720×720 square touchscreen     |
| SoC          | Unisoc T618 (2×A75@2.0GHz + 6×A55@2.0GHz)   |
| GPU          | Mali G52 @850MHz                              |
| RAM/Storage  | 3GB LPDDR4X + 32GB eMMC                       |
| OS           | Android 12                                    |
| Battery      | 2000mAh, 5V/2A Type-C                         |
| Connectivity | Wi-Fi 5, Bluetooth 5.0                        |
| Expansion    | microSD up to 2TB                             |
| Weight       | ≈167g (Black) / ≈197g (Silver)              |
| Dimensions   | 80×80×21.6mm                                |
| Features     | Swivel hinge, 90° rotation, 6-axis gyroscope |

---

## ✨ Features

### 🎵 Playback

- Supports MP3, FLAC, APE, WAV, OGG, AAC, WMA and more
- Playlist management, folder grouping, search
- Favorites (heart) system
- Play modes: sequential, repeat one, repeat all, shuffle
- Playback speed: 0.5× ~ 2.0×
- Seek bar with drag support

### 🎨 Screen Adaptation

- Optimized for 720×720 square display
- Auto-detect screen rotation via gyroscope
- Configurable aspect ratios: 16:9, 3:2, 4:3, 1:1, 8:7
- Adaptive mode fills the screen automatically
- Adjustable album art size (15%~85%)

### 🖼️ Album Art & Lyrics

- Local album art (MediaStore or sidecar .jpg), online fallback
- Spinning vinyl animation when no album art
- Online lyrics from NetEase, QQ Music, Kugou (auto-fallback)
- Lyrics caching for offline use
- Lyrics offset adjustment
- 5-level font size scaling

### 🎛️ Themes

- 8 preset Walkman-style themes
- Custom color configuration
- Metallic texture effects on select themes

### 🔒 Lock Screen & Notification

- Full lock screen playback controls
- Notification bar controls (Android 13+ compatible)
- Screen lock button in fullscreen mode

### 🎮 Additional

- Gamepad: L1 double-tap previous, R1 double-tap next, Select play/pause
- NAS (SMB) network music playback
- System equalizer
- Lyrics/cover cache management

---

## 🎮 Usage Guide

### First Time

1. Install the APK on RG Rotate
2. Grant file permission when prompted
3. Music scanning starts automatically
4. Tap any song to play

### Main Interface

- **Bottom navigation**: Songs / Folders / Settings
- **Tap a song** to start playing

### Now Playing

**Landscape Mode** (default):

- Left: Album art (tap for fullscreen)
- Right top: Song title (centered) + artist
- Right middle: Scrolling lyrics
- Bottom: Progress bar + play controls

**Portrait Mode**: Enable in Settings, then rotate RG Rotate 90°

- Top: Centered title + artist with N/N counter
- Center: Full-width square album art
- Bottom: 2-line lyrics + controls

**Fullscreen Mode**: Tap album art or swipe up

- Swipe down to exit
- Swipe up/down to toggle between cover and lyrics

---

## 🔨 Development

```bash
git clone <repo-url>
cd YYplayer
./gradlew assembleDebug
```

### Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **Player**: ExoPlayer (AndroidX Media3)
- **Images**: Coil
- **Architecture**: ViewModel + StateFlow
- **Min SDK**: Android 10 (API 29)
- **Target SDK**: Android 15 (API 35)

---

## 📄 License

This project is for personal learning and communication purposes only.
