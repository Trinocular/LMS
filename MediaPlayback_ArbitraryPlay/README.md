# Huawei Music MediaPlaybackActivityStrapper SSRF 漏洞报告

## 漏洞概述

华为音乐 App 中存在任意文件播放漏洞。`MediaPlaybackActivityStarter` Activity 被声明为 `exported=true` 且无权限保护，接受外部应用的 Intent data URI，将其转换为文件路径字符串后，经过 `mediaController.playOneshot()` → `MusicUtils.playOneshot()` → `startService()` → `MediaPlaybackService.onStartCommand()` → `ensureOneShot(path)` → `openFile(path)` → `PlayerManager.open()` → `MusicPlayer.open()` → `setDataSource()` → `handleSource()` 路径，最终到达 Android `MediaPlayer.setDataSource()` 或 `SafeContentResolver.openFileDescriptor()`。

## 影响

攻击者可以构造 Intent 传入以下类型的 URI：

- `file://` 路径 — 读取/播放设备上的任意音频文件
- `content://` URI — 通过 ContentResolver 读取其他应用暴露的数据（如 `content://media/external/audio/media/` 读取媒体库）
- `content://com.huawei.filemanager.share.fileprovider/cache/boxtempfolder` 虽然被屏蔽（有黑名单校验），但其他 content URI 不受限

## 校验分析

- 仅检查了空路径、加密后缀（`.enc`/`.hires`）、SafeBox 路径前缀（`com.huawei.filemanager.share.fileprovider`）
- 要求 `READ_EXTERNAL_STORAGE` 权限（但在 Android 10/11 及以上已变为精细化权限，且仅在运行时请求）
- 对于 `content://` URI，无需 `READ_EXTERNAL_STORAGE` 权限即可通过 ContentResolver 访问
- 没有对 URI scheme 进行白名单限制（如只允许 `file://` 或 `content://media/`）
- 没有对路径进行规范化检查（如 `../` 目录遍历）

## 调用链

### 1. 从外部 Intent 获取 URI 数据

`com/android/mediacenter/ui/player/oneshot/MediaPlaybackActivityStarter.java:148`

```java
Uri data = intent.getData();
```

### 2. 将 URI 转换为字符串路径

`com/android/mediacenter/ui/player/oneshot/MediaPlaybackActivityStarter.java:152`

```java
String string = (data.isOpaque() || !"file".equals(data.getScheme()))
    ? data.toString() : data.getPath();
```

### 3. 将文件路径传入 OneShotData 并调用 playOneshot

`com/android/mediacenter/ui/player/oneshot/MediaPlaybackActivityStarter.java:272`

```java
this.mediaController.playOneshot(
    new IMediaController.OneShotData(str, intent.getStringExtra("android.intent.extra.TITLE")));
```

### 4. 将 path 放入 Intent 中发送到 MediaPlaybackService

`com/android/mediacenter/playback/controller/MusicUtils.java:2438`

```java
Intent intent = new Intent(context, MediaPlaybackService.class);
intent.setAction(PlayActions.ONE_SHOT_ACTION);
intent.putExtra("path", oneShotData.getPath());
mStartService(context, intent);
```

### 5. MediaPlaybackService 从 Intent 中提取外部 path 并传递到 ServiceImpl

`com/android/mediacenter/localmusic/MediaPlaybackService.java:124`

```java
String stringExtra = intent.getStringExtra("path");
implInstance.ensureOneShot(stringExtra);
```

### 6. 外部路径放入 Bundle 传递给 PlayerManager

`com/android/mediacenter/localmusic/MediaPlaybackServiceImpl.java:3700`

```java
public void openFile(String str, ...) {
    ...
    bundle.putString("path", str);
    boolean zOpen = ((PlayerManager) this.mPlayer).open(bundle, z4, currentInfo);
}
```

### 7. PlayerManager 提取 path 传给 MusicPlayer

`com/android/mediacenter/localmusic/PlayerManager.java:403`

```java
this.mPath = BundleUtils.getString(bundle, "path");
return super.open(bundle, z2, songBean);
```

### 8. 创建 PathBean 并调用 setDataSource

`com/android/mediacenter/playback/player/MusicPlayer.java:877`

```java
PathBean pathBeanCreatPathBean = PathBean.creatPathBean(string);
return setDataSource(pathBeanCreatPathBean, z3, false, encryptType, null);
```

### 9. 外部路径作为数据源设置到 MediaPlayer（AndroidPlayer.setDataSource）

`com/android/mediacenter/playback/player/MusicPlayer.java:246`

```java
this.mPlayerProxy.setDataSource(pathBean);
```

## 验证证据

通过 `adb shell am start` 播放外部文件：

```
# file:// 外部存储音频 → 成功播放
adb shell "am start -a android.intent.action.VIEW \
    -d 'file:///hw_product/media/Pre-loaded/Music/Dream_It_Possible.flac' \
    com.huawei.music/com.android.mediacenter.ui.player.oneshot.MediaPlaybackActivityStarter"

# content://media 媒体库 → 成功播放
adb shell "am start -a android.intent.action.VIEW \
    -d 'content://media/external/audio/media/25' \
    com.huawei.music/com.android.mediacenter.ui.player.oneshot.MediaPlaybackActivityStarter"
```

日志输出：

```
MediaPlayerAgent_242713966: onPrepared
MediaPlayerNative: Action:start, CurrentState:MEDIA_PLAYER_STARTED
MediaPlayerAgent_242713966: notifyMediaStart playTime: 0
```

## 受影响版本

- 华为音乐 12.11.43.340

## 修复建议

1. 对 URI scheme 添加白名单限制（只允许 `file:///storage/` 等安全路径）
2. 对 `content://` URI 添加 authority 白名单（只允许 `media` 等）
3. 添加路径规范化检查，防止 `../` 目录遍历
4. 对 `path` extra 的来源做签名校验
5. 移除 `MediaPlaybackActivityStarter` 的 `exported=true` 或在 AndroidManifest 中添加权限保护
