# Huawei Music StreamStarter SSRF 漏洞报告

## 漏洞概述

华为音乐 App 中存在 SSRF（Server-Side Request Forgery）漏洞。

`StreamStarter` Activity 被声明为 `exported=true` 且无任何权限保护，通过 `http://` scheme + `audio/*` mimeType 的 intent-filter 暴露给外部应用。该 Activity 接收外部传入的 URI，仅检查 URI 是否以 `http://` 或 `https://` 开头，然后直接将完整的 URI 传递给播放引擎的 `openFileAsync` 方法。该 URI 最终被用于发起 HTTP GET 请求（无任何主机/域名白名单校验、无 SSRF 防护、无内网地址过滤），导致攻击者可以：

- 强制华为音乐 App 向任意外网 URL 发起 HTTP 请求
- 向内网服务（如 10.x.x.x、192.168.x.x、127.0.0.1 等）发起 HTTP 请求
- 可能访问云元数据接口（如 169.254.169.254）、内部 API 等敏感资源
- 利用华为音乐 App 的身份和网络权限执行未授权操作

## 调用链

### 1. StreamStarter Activity 被声明为导出的，且无权限保护

`com.huawei.music/AndroidManifest.xml:2726`

```xml
<activity android:exported="true" ...>
```

### 2. 从外部 Intent 获取攻击者控制的 URI

`com.huawei.music/com/android/mediacenter/ui/player/oneshot/StreamStarter.java:84`

```java
Uri data = intent.getData();
```

### 3. 将攻击者控制的 URI 赋值给成员变量

`com.huawei.music/com/android/mediacenter/ui/player/oneshot/StreamStarter.java:85`

```java
this.uri = data;
```

### 4. 仅检查 URI 是否以 http:// 或 https:// 开头，无主机白名单校验

`com.huawei.music/com/android/mediacenter/ui/player/oneshot/StreamStarter.java:35`

```java
if (StreamStarter.this.uri.toString().startsWith("http://")
    || StreamStarter.this.uri.toString().startsWith("https://"))
```

### 5. 将攻击者控制的 URI 直接传递给播放引擎

`com.huawei.music/com/android/mediacenter/ui/player/oneshot/StreamStarter.java:36`

```java
IPlayServiceHelper.inst().getMediaControl().openFileAsync(StreamStarter.this.uri.toString())
```

### 6. 通过 MediaControllerIml 转发到 MusicUtils

`com.huawei.music/com/huawei/music/playback/impl/MediaControllerIml.java:1489`

```java
public void openFileAsync(String str) { MusicUtils.openFileAsync(str); }
```

### 7. 通过 Binder IPC 调用 MediaPlaybackServiceStub.openFileAsync

`com.huawei.music/com/android/mediacenter/playback/controller/MusicUtils.java:2191`

```java
public static void openFileAsync(String str) { iMediaPlaybackService.openFileAsync(str); }
```

### 8. Binder 侧的 UID 检查通过（同进程调用），继续处理

`com.huawei.music/com/android/mediacenter/localmusic/MediaPlaybackServiceStub.java:1020`

```java
public void openFileAsync(String str) {
    if (StringUtils.isEmpty(str) || !checkUid("openFileAsync")) return;
    ...
}
```

### 9. 将 URI 传递给 PlayerManager

`com.huawei.music/com/android/mediacenter/localmusic/MediaPlaybackServiceImpl.java:3662`

```java
public void openAsync(String str, int i2, boolean z2, String str2) {
    ((PlayerManager) this.mPlayer).openAsync(str, ...);
}
```

### 10. 将 URI 设为下载路径

`com.huawei.music/com/android/mediacenter/playback/player/online/MusicOnlinePlayer.java:1454`

```java
this.mPath = str;
```

### 11. 将 URI 传递给 DownloadTask 执行

`com.huawei.music/com/android/mediacenter/playback/player/online/download/task/DownLoadTaskMgr.java:705`

```java
downloadTask.executeOnExecutor(PLAY_POOL,
    (Object[]) new String[]{downLoadTaskBean.getDownloadPath()});
```

### 12. URL 仅经空格编码处理（无安全校验）

`com.huawei.music/com/android/mediacenter/playback/player/online/download/DownloadTask.java:569`

```java
this.mDownloadUrl = UrlUtil.getCorrectUrl(correctUrl);
```

### 13. 直接向攻击者控制的 URL 发起 HTTP GET 请求

`com.huawei.music/com/android/mediacenter/playback/player/online/download/cahcechecker/StreamConnectHelper.java:409`

```java
Request.Builder builderUrl = httpClient.newRequest().url(this.mConnectUrl);
```

## 验证证据

### Logcat 捕获

```
Pb_DownloadTask: DownloadTask connect
Pb_StreamConnectHelper: openConn tryTime:0
NK_RealHttpClient: request: ConnectTimeout...
NK_DNManager: resolve source:LocalDns
```

### HTTP 服务器捕获的原始请求

```
******** SSRF REQUEST CAPTURED ********
From: 127.0.0.1:41759
User-Agent: com.huawei.music/12.11.43.340 (Linux; Android 10; JAD-AL50) RestClient/8.0.1.316

GET /ssrf_proof_verified HTTP/1.1
RANGE: bytes=0-
Host: 127.0.0.1:9999
Connection: Keep-Alive

=== SSRF CONFIRMED ===
```

## 受影响版本

- 华为音乐 12.11.43.300
- 华为音乐 12.11.43.340

## 修复建议

1. 对 Intent 传入的 URI 添加严格的主机名/域名白名单校验
2. 过滤内网 IP 地址（127.0.0.1, 10.x.x.x, 192.168.x.x, 172.16-31.x.x, 169.254.x.x 等）
3. 对 StreamStarter Activity 添加 `android:permission` 限制
4. 或直接设置 `android:exported="false"`，仅限内部调用

## PoC App

`ssrf-tester/` 目录下包含了完整的 PoC Android 项目源码。
