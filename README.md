# KLog

Android 日志 SDK，提供日志采集、本地落盘、自动埋点（生命周期 / 点击 / 崩溃）、压缩上传及企业微信通知一体化能力。

[![](https://jitpack.io/v/MartinMoring/KLog.svg)](https://jitpack.io/#MartinMoring/KLog)

---

## 功能

- 异步写盘，无锁队列，不阻塞主线程
- 按天自动分割日志文件，超期自动清理
- **自动埋点**：Activity / Fragment 生命周期、点击事件、崩溃堆栈——调用方无需修改任何业务代码
- 日志上报 UI：选择文件 → 压缩 → 上传 → 企业微信通知
- 上传实现由调用方提供，SDK 不绑定任何存储服务

---

## 接入

### 1. 添加 JitPack 仓库

```gradle
// settings.gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### 2. 添加依赖

```gradle
// app/build.gradle
dependencies {
    implementation 'com.github.MartinMoring:KLog:v1.0.0'
}
```

---

## 快速开始

### 初始化

在 `Application.onCreate()` 中调用一次：

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        KLog.init(
            context = this,
            config = KLogConfig(
                wxWebhookKey      = "your_wecom_webhook_key",  // 企业微信 webhook key
                enableLifecycleLog = true,   // 自动记录 Activity / Fragment 生命周期
                enableClickLog     = true,   // 自动记录点击事件
                enableCrashLog     = true,   // 自动捕获崩溃堆栈
                emailConfig = EmailConfig(   // 可选：SMTP 邮件通知
                    smtpHost   = "smtp.exmail.qq.com",
                    smtpPort   = 465,
                    username   = "log@example.com",
                    password   = "your_password",
                    recipients = listOf("dev@example.com"),
                    senderName = "KLog",
                    useSsl     = true
                )
            )
        )

        // 注册日志上传实现（由调用方实现，见"上传实现"章节）
        KLog.registerUploader(MyLogUploader())
    }
}
```

### 记录日志

```kotlin
KLog.d("Network", "请求开始 url=$url")
KLog.i("Auth",    "登录成功 uid=$uid")
KLog.w("Cache",   "缓存命中率低于阈值")
KLog.e("Crash",   "解析失败", exception)
```

### 打开上报页面

```kotlin
KLog.openReportPage(context)
```

用户在上报页面中选择日志文件、填写问题描述后，SDK 自动完成压缩 → 上传 → 企业微信通知的完整流程。

---

## 配置项

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `logDir` | `File?` | `filesDir/klog` | 日志存储目录 |
| `maxRetainDays` | `Int` | `5` | 日志保留天数，超期自动删除 |
| `flushIntervalMs` | `Long` | `30_000` | 定期落盘间隔（毫秒） |
| `bufferMaxSize` | `Int` | `500` | 内存缓冲条目阈值，超出立即落盘 |
| `wxWebhookKey` | `String` | `""` | 企业微信群机器人 webhook key，为空时不发送通知 |
| `deviceInfoProvider` | `() -> Map<String, String>` | `{}` | 额外设备信息，写入企微通知（如房间号、版本等） |
| `requirePhone` | `Boolean` | `true` | 上报描述中是否必须包含手机号 |
| `enableClickLog` | `Boolean` | `true` | 自动记录点击事件 |
| `enableLifecycleLog` | `Boolean` | `true` | 自动记录生命周期 |
| `enableCrashLog` | `Boolean` | `true` | 自动捕获崩溃堆栈 |
| `emailConfig` | `EmailConfig?` | `null` | SMTP 邮件配置；null 时不发邮件 |

### EmailConfig

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `smtpHost` | `String` | — | SMTP 服务器地址 |
| `smtpPort` | `Int` | `465` | 端口（SSL=465，STARTTLS=587） |
| `username` | `String` | — | 发件邮箱账号 |
| `password` | `String` | — | 发件邮箱密码或授权码 |
| `recipients` | `List<String>` | — | 收件人列表 |
| `senderName` | `String` | `"KLog"` | 发件人显示名称 |
| `useSsl` | `Boolean` | `true` | true=SSL/TLS，false=STARTTLS |

### 携带设备信息示例

```kotlin
KLogConfig(
    deviceInfoProvider = {
        mapOf(
            "设备型号" to Build.MODEL,
            "App版本" to BuildConfig.VERSION_NAME,
            "房间号"  to AppConfig.currentRoom?.room_name.orEmpty()
        )
    }
)
```

---

## 上传实现

SDK 通过 `LogUploader` 接口与存储层解耦，调用方自行实现上传逻辑：

```kotlin
class MyLogUploader : LogUploader {
    override suspend fun upload(zipFile: File, description: String): Result<String> {
        return runCatching {
            // 将 zipFile 上传到你的存储服务（OSS、S3、自有服务器等）
            // 返回文件的公网可访问 URL
            "https://example.com/logs/${zipFile.name}"
        }
    }
}
```

注册：

```kotlin
KLog.registerUploader(MyLogUploader())
```

---

## 自动埋点日志格式

所有自动埋点日志写入同一日志文件，通过 tag 区分：

```
[2026-06-17 10:00:01.123][I][Lifecycle] Activity onCreate: MainActivity
[2026-06-17 10:00:01.456][I][Lifecycle] Fragment onResume: HomeFragment
[2026-06-17 10:00:05.789][I][Click]     onClick: btn_confirm[确认] @ MainActivity
[2026-06-17 10:01:23.000][E][Crash]     Thread [main] crash:
java.lang.NullPointerException: ...
    at com.example.MainActivity.onClick(MainActivity.kt:42)
```

| Tag | 事件 |
|-----|------|
| `Lifecycle` | Activity / Fragment 的 onCreate、onResume、onPause、onDestroy |
| `Click` | 用户触摸点击（覆盖所有 setOnClickListener） |
| `Crash` | 主线程及子线程未捕获异常的完整堆栈 |

> **注意**：点击事件自动埋点通过 `Window.Callback` 拦截触摸事件实现，Dialog / PopupWindow 内的点击不在覆盖范围内。

---

## 日志文件

- 路径：`context.filesDir/klog/log_yyyy-MM-dd.txt`
- 命名规则：按天分割，如 `log_2026-06-17.txt`
- 获取文件列表：

```kotlin
val files: List<File> = KLog.listLogFiles()
```

- 手动落盘（建议在 App 进入后台时调用）：

```kotlin
KLog.flush()
```

---

## 关闭特定功能

```kotlin
KLog.init(
    context = this,
    config = KLogConfig(
        enableClickLog     = false,  // 关闭点击埋点
        enableLifecycleLog = false,  // 关闭生命周期埋点
        enableCrashLog     = false   // 关闭崩溃捕获
    )
)
```

---

## 混淆

SDK 已通过 `consumer-rules.pro` 自动保护公开 API，无需手动添加混淆规则。

---

## 最低版本要求

| 项目 | 要求 |
|------|------|
| minSdk | 24（Android 7.0） |
| compileSdk | 34 |
| Kotlin | 2.0+ |

---

## License

MIT
