# camera — 视觉录像

2026 年全国大学生电子设计竞赛 **H 题「视觉录像」部分**的实现代码。

---

## 画面来源

### 1. 网络摄像头（RTSP）

通过 WiFi 或网线接入，手机和摄像头在同一局域网。

**连接流程**：

```
局域网广播搜索  →  拿到摄像头 IP  →  拼 RTSP 地址  →  EasyPlayer 解码  →  TextureView 显示
```

**播放地址格式**：

```
rtsp://用户名:密码@摄像头IP:10554/tcp/av0_0
```

- 端口 `10554`、路径 `/tcp/av0_0`（TCP 传输，主码流）
- 用户名密码一般默认 `admin`

**解码**用 `videolib` 里的 EasyPlayer（`libEasyRTSPClient.so` + `libproffmpeg.so`），只接收视频帧、关掉音频，减少解码压力。

### 2. 手机摄像头（CameraX）

用 Android 官方的 CameraX：

- `PreviewView` 做预览
- 前置/后置可切换
- 录制用 `Recorder` + `VideoCapture`，自动带音频

---

## 录像保存

### 路径规则

| 情况 | 保存路径 |
|---|---|
| 已授予「所有文件访问权限」，或 Android 9 及以下 | **`/storage/emulated/0/Movies/Camera/`** |
| 没有该权限（Android 10 及以上默认） | **`/storage/emulated/0/Android/data/com.camera/files/Movies/Camera/`** |

也就是说，优先存**手机的「相册/影片」公共目录**，这样用系统相册、文件管理器、数据线连电脑都能直接找到。

拿不到权限时，自动退回到**应用私有目录**——这个目录不需要任何权限，一定能写入，只是路径深一点。

### 文件命名

```
VID_20261112_153045.mp4
```

格式是 `VID_年月日_时分秒.mp4`，按时间排序天然有序。

### 查看

- **公共目录**：打开手机「相册」或「文件管理」，找 `Movies/Camera` 文件夹
- **私有目录**：需要文件管理器开启「显示系统文件」，或直接用数据线连电脑进 `Android/data/com.camera/files/Movies/Camera`

### 想固定存到公共目录怎么办

在系统设置里给本应用打开「所有文件访问权限」：

```
设置 → 应用 → camera → 权限 → 所有文件访问权限 → 允许
```

打开后重新录像，文件就会存到 `Movies/Camera/`。

### 录像格式

两路都是 **MP4（H.264）**：

- 网络摄像头：由 EasyPlayer 边解码边封装成 MP4
- 手机摄像头：由 CameraX `Recorder` 输出 MP4，带麦克风音频

---

## 界面与操作

一个页面，底部三个按钮：

| 按钮 | 作用 |
|---|---|
| 切换到手机摄像头 / 切换到网络摄像头 | 在两路画面之间切换 |
| 翻转镜头 | 手机摄像头模式下前置/后置切换 |
| 开始录像 / 停止录像 | 对当前画面录像 |

网络摄像头模式下，上方会显示 IP、用户名、密码输入框和三个按钮：

| 按钮 | 作用 |
|---|---|
| 搜索 | 局域网广播找摄像头，自动填入 IP |
| 连接 | 用当前参数连 RTSP 并开始播放 |
| 断开 | 停止播放 |

---

## 代码结构

```
camera/
├── app/src/main/java/com/camera/
│   ├── MainActivity.java            界面、两路切换、录像控制
│   └── CameraSearchService.java     大华 DH 协议局域网搜索
├── app/src/main/res/layout/
│   └── activity_main.xml            单页布局
├── videolib/                        RTSP 播放与录像（EasyDarwin EasyPlayer）
│   └── src/main/jniLibs/arm64-v8a/  libEasyRTSPClient.so / libproffmpeg.so
└── app/build.gradle                 CameraX + appcompat + material
```

核心逻辑都在 `MainActivity`：

| 方法 | 作用 |
|---|---|
| `connectRtsp()` | 拼 RTSP 地址、创建播放器、开线程连接 |
| `startPhoneCamera()` / `bindPhoneCamera()` | CameraX 预览绑定 |
| `startRecording()` / `stopRecording()` | 按当前模式走不同的录像分支 |
| `getVideoDir()` | 两级目录回退逻辑 |

---

## 编译

| 项 | 版本 |
|---|---|
| 语言 | Java |
| AGP | 7.3.1 |
| Gradle | 7.4 |
| compileSdk / targetSdk | 34 |
| minSdk | 24 |
| 架构 | arm64-v8a |

```bash
gradlew.bat assembleDebug
```
