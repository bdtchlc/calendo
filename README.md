# Calendo

日历与待办结合的 Android 应用（Jetpack Compose）。单日视图采用纵向时间轴（类似 Time Blocks），行程块支持「普通日程」与「待办日程」：待办带勾选框，完成后标题显示删除线。

## 运行方式

**JDK：** Android Gradle Plugin 需要 **Java 17**。若终端构建报错 “requires Java 17 / You are currently using Java 11”，请先安装 JDK 17（例如 Android Studio 自带的 JBR，或 `brew install openjdk@17`），并在当前终端执行：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

（路径因机器而异，以 `java_home -v 17` 输出为准。）

1. 安装 [Android Studio](https://developer.android.com/studio)，并在 SDK Manager 中安装 **Android SDK Platform 35** 与 **Build-Tools**。
2. 在本目录创建 `local.properties`（Android Studio 首次打开工程会自动生成），例如 macOS：

   ```properties
   sdk.dir=/Users/你的用户名/Library/Android/sdk
   ```

3. 用 Android Studio 打开本仓库根目录，等待 Gradle Sync 完成后选择设备或模拟器，点击 **Run**。

或在终端执行（需已配置 SDK）：

```bash
./gradlew :app:assembleDebug
```

产品手稿参考：`docs/wireframe-day-view.jpg`。

## 当前原型功能

- **日**：7:00–23:00 时间轴，示例数据对应手稿日期 `2026年3月21日`。
- **空白格**：点击某个小时区域新建日程（默认时长 1 小时，可在表单里改）。
- **普通日程**：无勾选框，标题过长时省略号截断。
- **待办日程**：勾选完成 → 标题删除线；顶部 **待办** 芯片可只看待办块。
- **周 / 月**：占位页，后续迭代补充。
