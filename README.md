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

## 更新日志

### alpha_0.1（持续迭代）

#### 2026-05-19
- **时间选择器重设计**：将编辑日程时的线性双滑块替换为 **24 小时圆盘拨轮**。
  - 固定布局：8:00 在左（9点钟方向）、20:00 在右（3点钟方向）、14:00 在正上方；上半弧为「日」（暖琥珀色），下半弧为「夜」（深靛蓝色），水平分割线标示昼夜边界。
  - 两个带光晕的拖动手柄可拖至圆盘任意位置，15 分钟粒度对齐；圆心显示当前选中的开始→结束时间。
  - 改为圆盘样式后，拖动不再靠近屏幕左右边缘，避免触发系统「返回」手势。
- **Bug fix · 时间滑块崩溃（彻底修复）**：将起始滑块拖过屏幕边缘时，应用仍会崩溃。
  - 根因：`RangeSlider(steps = 66)` 的内部步进量化使用非整数步距（≈14.99 分钟），当两端滑块汇聚至同一步时，float32 精度差导致内部渲染层 `start(749.7686) > endInclusive(749.7685)`，`SliderRange()` 抛出 `IllegalArgumentException`。
  - 修复：改用 `steps = 0`（连续滑块），15 分钟粒度完全由 `snap15()` + `safeRange()` 在 `onValueChange` 回调中处理，彻底消除双重量化冲突。
- **本地持久化**：新增 `ItemsStore`（基于 Gson 的 JSON 文件存储），应用重启后同步/创建的日程数据不再丢失；`CalendoViewModel` 初始化时从磁盘加载，每次数据变更后异步写回。
- **任务页 UI 改进**：
  - 逾期「滚雪球」区块默认折叠，超过 2 条时显示展开按钮。
  - 右上角新增「显示已完成任务」切换按钮，已完成任务默认隐藏。
  - 勾选待办时先显示删除线动画（500ms），再从列表中移除。
  - 勾选框移至标题文字左侧。

#### 2026-05-11
- **日历视图**：点击有任务的日期首次展开当日任务列表，再次点击进入「今天」日视图并定位到该日；无任务日期直接跳转日视图。
- **Google Tasks 同步**：待办事项通过 Google Tasks API 双向同步（而非 Calendar 事件）；与 Google 字段不兼容的本地扩展字段（palette、priority、参与人、精确时段）以纯文本写入 `notes` 的 `[Calendo Meta]` 区块；冲突以 Google 服务端为准。
- **日视图并排展示**：同一时段有多个事件时自动分列收窄并排显示；左侧时标固定 52dp 单行不断行。
- **「今天」标题与导航**：当前日期非今天时，顶栏标题改为「回到今天」可点击跳回；底部「今天」Tab 在已选中状态下再次点击同样回到今日。
- **任务页**：逾期滚雪球默认最多显示 2 条，超出可展开；右上角新增「显示已完成任务」按钮；待办勾选框移至标题左侧。
- **日历点击跳转 Bug 修复**：修复从任务页切回日历页后二次点击日期跳转到错误日期的问题（Pager/VM 竞态）。
- **同步容错**：Calendar 同步逐条推送，单条失败不影响整体；`patch 404` 自动降级为 `insert`；`400` 错误自动重试降级参数；错误信息附加 Google 响应原文便于排查。

## 设计来源

- 墨刀交互稿：<https://rhin8m29.site.modao.ink>（若链接失效，以本仓库已落地界面为准）。

