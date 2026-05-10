# Calendo

日历与待办结合的 Android 应用（Jetpack Compose），交互与信息结构参考 [墨刀高保真原型](https://rhin8m29.site.modao.ink) 与 Time Blocks 风格色块时间轴。

## 运行方式

**JDK：** Android Gradle Plugin 需要 **Java 17**。若终端构建报错 “requires Java 17 / You are currently using Java 11”，请先安装 JDK 17，并执行：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

1. 安装 [Android Studio](https://developer.android.com/studio)，安装 **Android SDK Platform 35** 与 **Build-Tools**。
2. 在本目录配置 `local.properties` 中的 `sdk.dir`（Android Studio 可自动生成）。若使用 Homebrew 的 `android-commandlinetools`：

   ```properties
   sdk.dir=/opt/homebrew/share/android-commandlinetools
   ```

3. 用 Android Studio 打开本仓库根目录，选择设备后 **Run**；或执行：

```bash
./gradlew :app:assembleDebug
```

产品线稿见 `docs/wireframe-day-view.jpg`。

## 功能概览（当前版本）

- **布局**：主内容区 **最大宽度 500dp 居中**，模拟手机信息宽度；底栏半透毛玻璃感，配合系统边距（刘海/底部安全区由 `enableEdgeToEdge` + Material3 边距处理）。
- **底部导航**：**首页**（当日时间轴）、**日历**（周/月切换）、**任务**（待办清单）、**我的**（设置与 Google 授权入口）。
- **首页**：7:00–23:00 时间轴，**左右滑动**切换日期；**今天**在标题行高亮；**待办**过滤芯片；**滚雪球**区域展示「昨日及更早未完成待办」提醒；色块按优先级/调色盘配色（P0/P1 等可在编辑里选）。
- **日历 · 周**：一周七列，窄屏以**竖条色块**表示当日事件密度；**月**：以**圆点**表示多日事件，控制信息密度。
- **任务**：按 **逾期未办（滚雪球）/ 今天 / 即将到来** 分组；点行会跳回首页并定位到该条所在日期（与当前选中日期同步）。
- **我的 · Google 日历**：使用 **Google Sign-In** 申请 `https://www.googleapis.com/auth/calendar` 作用域；在 `res/values/strings.xml` 中把 `default_web_client_id` 换成你在 **Google Cloud Console** 创建的 **Web 应用** 客户端 ID（并配置包名 + 调试 **SHA-1** 的 **Android** 客户端）。**双向同步**（拉取/推送事件、冲突处理、后台周期同步）需再接入 **Calendar API v3** 与 `WorkManager`，本版仅完成**授权与状态展示框架**。

## Google 日历双向同步（后续开发说明）

1. 在 [Google Cloud Console](https://console.cloud.google.com/) 启用 **Google Calendar API**。
2. 创建 **OAuth 2.0** 凭据：Android 应用（`com.calendo.app` + 你的 keystore SHA-1）+ Web 应用（将 Web 客户端 ID 写入 `default_web_client_id` 供 `requestIdToken`）。
3. 使用存取的 `account` 与 `HttpTransport` 调用 Calendar API 做 **events.list / insert / patch / delete**，并与本地 `CalendarItem` 做映射与版本戳/同步令牌；冲突策略需产品层定稿。

## 设计来源

- 墨刀交互稿：<https://rhin8m29.site.modao.ink>（若链接失效，以本仓库已落地界面为准）。

