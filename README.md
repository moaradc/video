# Mono · 极简视频浏览器

**黑白之间，播放与浏览。** 一款以「小窗播放器」为核心、内置轻量浏览器的 Android 应用。

支持 **Android 8.0（API 26）～ 12+**，提供独立的 **64 位（arm64-v8a）** 与 **32 位（armeabi-v7a）** 安装包。

## ✦ 核心特性

### 小窗播放器（全局悬浮）
- **全局拖拽**：悬浮窗可在任何应用之上拖动，1:1 跟随手指，VelocityTracker 实时采样真实拖拽速度
- **物理动效**：松手后按释放瞬间的真实速度滑行，指数摩擦**渐停**；碰到屏幕边缘产生**自适应反弹**（恢复系数随冲击速度 0.22 → 0.5 缩放，允许小幅“陷壁回弹”）
- **网页视频接管**：钩住页面所有 `<video>` 元素——点击注入的「小窗播放」胶囊按钮即调用 `requestFullscreen()`，经 `WebChromeClient.onShowCustomView` 接管视频画面，**任何站点原生 / 自研播放器 UI 均被 Mono 播控层替代**
- 页面原视频位显示「视频正在小窗播放」占位提示（可随滚动跟随）
- 双击窗口切换大小、右下角拖拽调整尺寸、可最小化为边缘气泡
- 支持本地 / 外置存储视频（Media3 ExoPlayer）与网页视频双内容源，mediaPlayback 前台服务常驻

### 轻量浏览（参考 Via 的极简哲学）
- 多标签页（`window.open` / `_blank` 正确接管）
- 网页历史 / 播放记录（跨会话续播）/ 稍后再看 / 收藏书签 / 下载管理
- 域名级广告拦截（内置精简 hosts，实时计数）
- 页内查找、桌面版 UA、四种搜索引擎（必应 / 百度 / 谷歌 / DuckDuckGo）
- 全系统 WebView 内核，无内嵌浏览器引擎，安装包体积与内存占用极小

### 黑白线条 UI
- 纸白 / 墨黑双色主题，1dp 细线分隔，全矢量线性图标
- **暗色压暗**：夜间模式在基础界面叠加压暗层（可调 0–60%），**不影响小窗播放器亮度**（独立悬浮窗不受 Activity 压暗层影响）

### 播放器手势（本地全屏播放）
- 左半屏上下滑调亮度、右半屏上下滑调音量、水平滑动快进快退
- 0.5×–2× 倍速、横竖屏切换、一键转小窗

## ✦ 下载

| 架构 | 设备 | 安装包 |
| --- | --- | --- |
| arm64-v8a | 2019 年后的主流 64 位机型 | `Mono-v1.0.0-arm64-v8a.apk` |
| armeabi-v7a | 32 位老机型 | `Mono-v1.0.0-armeabi-v7a.apk` |

安装时需允许「未知来源」，并在弹出小窗时授予「显示在其他应用上层」权限。

## ✦ 构建

```bash
# 需要 JDK 17、Android SDK 34
./gradlew assembleRelease
# 产物位于 app/build/outputs/apk/release/
```

签名密钥库为仓库内 `app/mono.keystore`（个人项目发行用；如需正式分发请自行更换并注意保管私钥）。

## ✦ 技术要点

| 模块 | 方案 |
| --- | --- |
| 小窗物理 | Choreographer 帧回调 + 指数摩擦 + 边缘自适应反弹（自研 ~100 行，无第三方依赖） |
| 网页视频 | JS 注入钩子 + `onShowCustomView` 画面接管 + JS 桥双向控制（播放/进度/续播） |
| 本地播放 | Media3 ExoPlayer 1.3.1 |
| 下载 | HttpURLConnection Range 断点续传 + MediaStore / 公共目录落地 |
| 存储 | 原生 SQLite（五张表），无 ORM，冷启动开销极低 |
| 体积 | R8 压缩 + 资源收缩，无图片资源、无第三方 UI 库 |

## ✦ 已知边界

- 跨域 `<iframe>` 内的视频（如部分第三方嵌入播放器）无法被 JS 注入钩住，此时站点自身的全屏按钮仍可触发接管；此类会话中小窗的暂停 / 进度控制自动降级隐藏
- m3u8 类分片流仅支持播放，不支持下载合并
- Android 12 上前台服务通知可能延迟 10 秒显示（系统行为）

## ✦ 技术参考

开发过程中调研了以下优秀开源实现与资料，致谢：

- [EasyFloat](https://github.com/prprrocketfloat/EasyFloat)（悬浮窗权限与吸附交互）
- [Leos Void](https://codeberg.org/Leos/VOID)（浏览器视频弹窗接管思路）
- [Via 浏览器](https://viayoo.com/)（极简架构与 1MB 级体积哲学）
- [androidx.media3 / ExoPlayer](https://github.com/androidx/media) 官方文档
- Android 官方：`WebChromeClient.onShowCustomView`、`TYPE_APPLICATION_OVERLAY`、前台服务行为变更文档

## License

MIT
