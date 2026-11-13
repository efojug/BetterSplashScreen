# BetterSplashScreen

ColorOS 基础启动遮罩模块，使用 libxposed API 102，仅作用于 `com.android.systemui`。

已实现固定遮罩策略，签名匹配表示结构可用。

## 行为

- 忽略系统 Splash 中的应用自定义图片、动画图标、图标底色及底部品牌图。
- 图标优先级：经验证的 ColorOS 主题取图链 → 当前用户的桌面启动组件图标 → 应用图标 → 系统默认图标。
- 当前没有经过验证的 ROM 主题取图配置，因此主题分支报告 `unverified`，实际回退到 `LauncherApps` 的桌面组件图标。该 API 可能经过厂商主题处理，但此处不作保证。普通 PackageManager 图标不会标记为主题图标。
- 从 SystemUI Application 的系统配置读取深浅模式，浅色纯白、深色纯黑，均不透明。每次启动重新计算，不使用目标应用强制的主题模式。
- 采用系统图标基准尺寸，在最终 framework Builder 写入完整 Drawable，不额外套圆角或缩放，不走自适应图标只取前景的处理；居中显示。
- 不缓存图标，下次启动重新获取。系统自身图标缓存是否及时更新仍需真机验证。
- 保留系统退出动画与窗口移除协议，不 Hook system_server。
- Legacy 遮罩改为原生图标外观后，仍保持原来的不可复制属性，由 Shell 清理，不意外开放应用侧 SplashScreen 接管。

仅处理已经进入 `makeSplashScreenContentView` 的 Splash／Solid／Legacy 内容。不把快照、NONE、Windowless、透明／浮动 Activity、桌面或最近任务改成 Splash，也不处理应用自己的启动页。

## 启用与构建

安装 `app/build/outputs/apk/debug/app-debug.apk`，在支持 API 102 的框架管理器启用模块，作用域选择系统界面，然后重启 SystemUI 或设备。停用后也需要重启目标进程。

应用首页显示模块启用状态和 SystemUI Hook 状态。启用状态来自 libxposed 框架服务，另行显示系统界面作用域是否选中；Hook 安装成功、等待命中、已命中及失败原因来自当前 SystemUI 进程回传。未收到回传时显示“未确认”，不会把框架连接当成 Hook 成功。

每次打开页面时查询一次，不定时刷新；也可手动刷新。查询期间按钮显示“正在刷新…”且不可点击，收到有效回复、发送失败或 2 秒超时后恢复。离开页面取消未完成的查询。广播限定目标包、共享并校验发送 UID、匹配随机查询令牌与版本号，超时则清除上次结果。不持久化成功标志。状态通道随 SystemUI 进程退出而失效，更新模块后需重启 SystemUI。新增框架服务依赖及其服务桥接 Provider，不使用远程偏好保存状态。

使用 JDK 25（与现有 Gradle daemon 配置一致）、项目 Gradle Wrapper 及 `local.properties` 指向的 Android SDK。保留现有 compile/target SDK 37

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assembleRelease
```

## 适配与诊断

`ShellBindings` 校验完整方法签名、返回值、字段类型／可写性及 Shell 类型常量。两个 Builder 类同时存在也会报告歧义。必需能力全部解析后才注册核心 Hook；注册时先关闭修改，整组成功后启用，失败解绑已安装部分。

ThreadLocal 栈只关联同线程同步嵌套构建，并在 `finally` 清理。异步厂商背景覆盖按已确认 View 的弱引用映射拦截，不使用“最近包名”。构建后恢复 Builder 和共享临时属性。after 失败保留已经取得的结果，不保存 Hook chain、不捕获原方法异常后再次执行。

主题候选探针仅观察完整签名的 `getIconExt(Context, ActivityInfo, int, boolean, boolean, boolean)`。`ColorOsThemeAdapter` 的证据白名单为空；没有固件证据时不会采用候选或标记为已验证主题图标。补充具体指纹前必须核对厂商取图链、同一用户／组件下至少两套主题的桌面对照、主题切换和未覆盖应用回退；需要其他接口时新增具体 ROM 适配实现。

框架日志或 `adb logcat -s BetterSplash` 中：`module_loaded` 包含完整 ROM 指纹；`capability` 包含解析签名与支持状态；`install_group` 是安装结果；`hook_hit` 是实际命中；`icon_selected`／`icon_fallback` 记录图标来源与回退；`content_complete` 记录内容类型与背景决策。会话日志含 session、task、display、user、包名。关键命中日志按进程去重，错误保留原因。