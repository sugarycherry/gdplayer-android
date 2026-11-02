# GD Player

一个原生的安卓音乐播放器，直连 [GD Studio 的公开音乐接口](https://music-api.gdstudio.xyz/api.php)（Meting 风格），不加载网页、不依赖浏览器。

Kotlin + Jetpack Compose + Media3 (ExoPlayer)，包名 `com.gdmusic.player`，最低 Android 8.0 (API 26)。

## 功能

- **三个可用音源**：网易云 / B站 / JOOX（接口目前只开放这三个）
- **搜索**：关键词搜索歌曲，滚动自动翻页
- **播放**：播放/暂停、上一首/下一首、拖动进度、随机、列表循环/单曲循环
- **歌词**：逐行滚动高亮，支持原文 + 翻译双行（网易云的 `tlyric`）
- **音质切换**：128k / 192k / 320k / 无损（无损走 flac）
- **自动换源**：直链失效时调用 `autosource` 换源兜底；连续失败自动跳过
- **后台播放**：Media3 `MediaSessionService`，通知栏、锁屏、耳机/蓝牙控制都可用
- **通知栏封面**：`MediaMetadata.artworkUri` + `DataSourceBitmapLoader`，由系统在后台加载专辑图
- **收藏 / 历史 / 自建歌单**：本地持久化（SharedPreferences + Gson）
- **网易云歌单导入**：粘贴歌单链接或 ID（如 `3778678`），整单导入为本地歌单
- **下载**：保存到 `Music/GD Player/`（Android 10+ 走 MediaStore）
- **在线更新**：启动时检查 GitHub Release，发现新版本可直接在 App 内下载并调起安装器
- **深色/浅色主题**

## 接口用法

| 动作 | 参数 | 返回 |
| --- | --- | --- |
| 搜索 | `types=search&source=netease&name=x&count=30&pages=1` | 歌曲数组 |
| 取流 | `types=url&source=netease&id=<url_id>&br=320` | `{url,br,size}` |
| 封面 | `types=pic&source=netease&id=<pic_id>&size=600` | `{url}` |
| 歌词 | `types=lyric&source=netease&id=<lyric_id>` | `{lyric,tlyric}` |
| 歌单 | `types=playlist&id=<网易云歌单id>` | 网易云原始歌单 JSON |
| 换源 | `types=autosource&source=…&br=…&name=<JSON>` | `{url,…}` |

关键实现点：

- 取到的 `url` 是**带时间戳的临时直链**，不能缓存。播放器用 `ResolvingDataSource` 在每次 `open()` 时现取地址，MediaItem 里只存 `gdplayer://play/<source>/<url_id>?br=320` 这种逻辑地址。
- 封面接口按 **并发 2 + 140ms 间隔**节流并缓存 URL，避免被接口限流打 503。
- 网络请求对 5xx 自动重试一次。

## 在线更新

1. 启动时（以及设置里点「检查更新」）请求 `api.github.com/repos/<repo>/releases/latest`。
2. 用 tag 与 `BuildConfig.APP_VERSION` 逐段比大小，新版本就弹窗显示更新说明。
3. 「下载并安装」把 APK 下到应用私有目录 `update/`，再用 `FileProvider` + `ACTION_VIEW` 调起系统安装器。
4. 首次安装需要允许「安装未知来源应用」，没有授权时会自动跳到该设置页。

发布新版本只要在 GitHub 建一个 tag（如 `v1.0.2`）并上传 APK 到 Release 即可，App 下次启动就能看到。

仓库是公开的，所以匿名就能读取；如果以后改回私有，需要在 `local.properties` 里加
`github.token=<只读 PAT>` 重新构建（token 会被编进 APK，转发 APK 等于泄露 token，慎用）。

## 构建

```bat
set JAVA_HOME=%LOCALAPPDATA%\gdmusic-build\jdk
set ANDROID_HOME=%LOCALAPPDATA%\gdmusic-build\sdk
gradlew.bat assembleRelease
```

`local.properties` 指向本机 SDK。签名用 `gdplayer.jks` + `keystore.properties`（自签名，口令 `gdplayer2026`），**这两个文件不进仓库**，但升级覆盖安装必须用同一个签名，请自行备份。

## 已知限制

- 接口只开放 `netease` / `bilibili` / `joox` 三个源，其它源会返回 `Value of source is not supported`。
- 部分歌曲（尤其网易云热歌榜里的 VIP 曲目）取不到直链，会自动换源或跳到下一首。
- 接口限流时封面会显示占位图，搜索结果也可能短暂为空。
- 接口由第三方维护，随时可能变；失效位置集中在 `data/MusicApi.kt` 和 `player/StreamResolver.kt`。

## 实测（MuMu Player 12 / Android 15）

| 项目 | 结果 |
| --- | --- |
| 安装启动、热歌榜加载 | 正常，无 crash |
| 搜索 + 分页 | 正常 |
| 播放（320k mp3） | 正常，`state=PLAYING` |
| 歌词滚动 + 翻译 | 正常 |
| 按 Home 切后台 30s | 继续播放，通知栏/MediaSession 正常 |
| 音质切换 | 正常（菜单里 320k ✓） |
| 下载 | 写入 `Music/GD Player/*.mp3`，大小与接口返回一致 |
| 通知栏封面 | `android.largeIcon=Icon(typ=BITMAP)`，metadata 带上 artworkUri |
| 网易云歌单导入 | 导入 `3778678` → 「热歌榜 · 200 首」入本地歌单 |
| 在线更新 | 旧版本号启动后检测到新 release，App 内下载并调起安装器 |
