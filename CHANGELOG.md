# Changelog

全部时间按 2026-10-30 记。版本号目前停在 `0.1.0`（个人使用，不发布，不再细分）。

## 0.1.0 — 2026-10-30

### 新增

- **JSON 树视图**（`JSON格式化`）：自写递归下降解析器（不引第三方库），大纲风格渲染
  （无连线、纯缩进、清晰折叠三角），严格/宽松双模式，宽松模式支持提取平衡主体、
  容忍单引号/尾逗号/注释、自动解开"顶层是转义 JSON 字符串"的内层，节点数超 100 000 提示。
- **格式化结果树右键复制**（`JsonTree` 自带 `JPopupMenu`，菜单一项`复制`）：把选中节点及其下所有
  子节点序列化成一份完整 JSON。为此在模型层加了 `JsonNodeModel.toPrettyValueString()`，
  与展示用的 `toPrettyString()` 差在两处：不输出节点自身的 key（否则拿到的是 `"user": {…}` 这样的片段）、
  不输出 `(escaped JSON):` 注释行（那行是给人看的，混进复制结果 JSON 就废了）。
  右键先 `setSelectionPath` 把选中位移到鼠标那一行，避免复制到上一次点选的行；空树/空白处不弹菜单。
  对比树没做（差异节点存的是左右两侧各自的值，没有单一"内容"可复制）。
- **JSON 对比**（`JSON对比`）：按 key 路径做结构化语义递归比对，识别新增/删除/值变更/类型不同，
  合成一棵合并差异树；输入停止 300ms 自动对比；`仅展示差异` 过滤；重渲染保留手动展开状态。
- **两层历史池**（`history` 包，跨 IDE 重启）：单段 JSON 上限 50 条，对比现场**成对**上限 30 条
  （同时回填 A、B 与当时模式）；超 200 000 字符不入池；同内容只刷新时间不新增。
- **HTML 格式化**（`format/HtmlFormatter`）：自写轻量 tokenizer，零依赖，宽松容错优先。
- **XML 格式化**（`format/XmlFormatter`）：JDK DOM + identity Transformer，严格报错（带行列号），
  `FEATURE_SECURE_PROCESSING` + 关闭外部 DTD/实体加载，声明与 DOCTYPE 按原文补回。
- **URL 编码/解码**（`codec/UrlCodec`）：RFC 3986 百分号编解码，固定 UTF-8，宽松解码。
- **多标签**：每类功能可开多个标签并存，各自独立状态；标签头带 `×` 关闭。
- **标签拖拽换序**（`ui/ReorderableTabbedPane`）：标签头按住左右拖动即可换位置，越过相邻标签中线就实时换位。
  `JTabbedPane` 没有 moveTab 这样的 API，所以把重排收成一个原语 `moveTab(from, to)`（取回 content/title/tabComponent
  三个引用 → `remove(from)` → `insertTab(..., to)` → `setTabComponentAt`），拖拽手势只是它的触发器；
  换位只改挂载关系，内容面板始终是同一个实例，输入文本与展开状态不受影响；选中按内容实例恢复，不按下标。
  用手写拖拽而不用 AWT `DragSource`/`DropTarget`：实时重排会把正在拖的标签头 remove 再 add，与 DnD 会话冲突，
  而且传一个下标还得为它写 `Transferable`。代价：没有跟随鼠标的拖拽影像。
- 顶部按钮行：`JSON格式化` / `JSON对比` / `URL编码/解码` / `HTML格式化` / `XML格式化`。
- **兼容 JetBrains 全家桶**：`plugin.xml` 只依赖 `com.intellij.modules.platform`（删掉了
  `<depends>com.intellij.modules.java</depends>`），`build.gradle` 的 `intellij.plugins` 也去掉了
  `com.intellij.java` —— 没有 Java 插件参与编译，本身就是“不依赖它”的证明。
  `patchPluginXml` 只写下限 `sinceBuild = 241`，**不写 `until-build`**（平台视同无上限），
  因此 IDEA / GoLand / DataGrip / PyCharm / WebStorm / PhpStorm / CLion / Rider / Aqua 均可安装。
  踩坑：`untilBuild = '*'` 会被 gradle-intellij-plugin 解析成无效版本号（报“since-build 241.0 大于
  until-build 88.*”），正确写法是 `untilBuild.set(provider { null })`。
  代价：不设上限后，真遇到 API 不兼容时会是运行期报错而不是安装时的“不适配”提示。
- 100 个纯逻辑层单测（不启 IDE 即可跑），后续补到 104 个（新增 4 条覆盖 `toPrettyValueString`，
  其中一条拿"复制结果能被严格模式重新解析"当硬判据）；再加标签重排的下标用例后为 111 个。

### 变更

- **标签不再强制保留一个**：原先 `closeTab` 写的是 `index > 0 && tabCount > 1`（位置 0 那个关不掉、永远至少剩一个）。
  标签可以拖拽换位之后，“按位置”限制已没意义（初始标签被拖走就会换个标签占据位置 0），改成任何标签都能关，
  全关掉就是一块空面板 —— 顶部五个按钮仍在，随时能新建。
- **`JSON格式化` / `JSON对比` 的输入区高度改成可拖**：原先用 `setPreferredSize(new Dimension(10, 140/160))`
  写死，想把输入框拉大只能整个工具窗口拉高。换成 `JBSplitter(true, 0.4f)` 分隔输入区与结果区，
  与 `HTML格式化`/`XML格式化` 用的 `TextFormatPanel` 同一套做法（控制行仍贴在输入区下方）。
  没有加 `setMinimumSplitFraction`/`setProportionKey` —— 那两个方法在 `JBSplitter` 上不存在（属于
  `OnePixelSplitter`），编译不过；HTML/XML 面板本身也没做比例持久化，这里保持一致。
- **`JSON格式化` 改成输入即自动格式化，删掉 `格式化` 按钮**：沿用对比面板那套 300ms 防抖
  （`DocumentListener` 收到删/插事件就 `Timer.restart()`，一次 `setText` 会连发两个事件，天然合并成一次解析）；
  切换模式下拉时也调度重算，用 `lastFormattedMode` 判断"真的变了才调度"，避免下拉弹出/关闭的多余 ActionEvent。
  清空结果走 `clearResult()`：置空树模型、清掉 `lastPretty`（防止复制按钮粘上一次结果）、收起`宽松模式重试`。
- **新建 Tab 不再显示 Swing 示例树**：`JsonTree` 与 `JsonDiffTree` 之前都用的 `JTree()` 无参构造，Swing 会自带一份
  Animal/Plantae 示例模型，被两个渲染器的 fallback 分支渲染成"默认那坨 JSON"（格式化与对比两个标签都中招）。
  现在均在构造里显式 `setModel(new DefaultTreeModel(null))`；输入框为空时也走同一条清空路径。
  对比标签额外在构造尾调一次 `clearResult(...)`，把“输入 A 和输入 B 都有内容后会自动对比”当开场提示，
  否则两侧没填时下面又空又没字，看着像整个面板是坏的。
- 插件显示名：`JSON Tree Formatter` / `JSON Formatter` → `Formatter` → **`TidyKit`**（三处耦合点：`<name>`、
  `<toolWindow id>`（即右侧竖条显示名）、工厂里 `createContent` 的标题）。
- **包名与插件 id：`com.you.jsonformatter` → `com.tidy.kit`**，同步修正 `plugin.xml` 的 `factoryClass` 与
  Gradle `group`。历史存在 `json-formatter-history.xml`（`@State` 名 `JsonFormatterHistory`），
  不依赖包名，因此改包名不丢历史。代价：插件 id 变了，IDEA 视为另一个插件，
  装新包前先卸载旧的，否则会出现两个同名 TidyKit 工具窗口。
- 插件级类去掉 JSON 味：`JsonFormatterToolWindowFactory` → `TidyKitToolWindowFactory`、
  `JsonFormatterTabs` → `TidyKitTabs`（同步 `plugin.xml` 的 `factoryClass`），
  `JsonUiColors` → `UiColors`（HTML/XML 面板也在用它）。只处理 JSON 的类
  （`JsonTreePanel` / `JsonDiffPanel` / `JsonParseService` / `JsonHistoryStore` 等）保留原名。
- Gradle 项目名 `json-tree-formatter` → `tidy-kit`，产物 zip 相应改叫 `tidy-kit-0.1.0.zip`。
- 历史存储键刻意不动：`@State` 名 `JsonFormatterHistory` 与文件 `json-formatter-history.xml`
  属于遗留命名，改它会丢已存的历史。
- 顶部原 `+` 按钮改名 `JSON格式化`，语义不变（新建一个 JSON 标签）。
- JSON 模式下拉框改成中文 `严格` / `宽松`（之前直接展示枚举名 `STRICT` / `LENIENT`）；
  历史文件仍存枚举名（`mode.name()`），旧记录照旧能读回来并自动显示中文。
- 标签页命名从 `JSON n` 改为按功能命名（`JSON格式化 n` / `JSON对比 n`，HTML/XML/URL 取短式）。
- 状态栏着色与剪贴板复制从 4 个面板里的重复实现抽成包私有 `ui/UiFeedback`。
- `plugin.xml` 的 `<description>` 重写，覆盖新增的对比/HTML/XML/URL 能力。

### 修复

- **`全部收起` 点了没反应**：两棵树的 `collapseAllNodes()` 写的都是 `for (i = getRowCount()-1; i > 0; i--) collapseRow(i)`，
  跳过行 0（根）→ 最多只能退到“根 + 直接子节点”；而自动渲染的默认展开恰好就是这个样子，
  所以点下去行数不变、看着就是按钮失效（实测：13 行 → 4 行，而不是 → 1 行）。
  改成模型驱动的 `collapsePath(new TreePath(root))` 一次折完（13 行 → 1 行），顺带去掉大树下
  逐行 collapse 带来的 N 次重绘。代价：“收起”后只剩根那一行，要再展开得点 `全部展开`。
  为了让这个选择在后续自动重算中不被抹掉，capture/restore 不再强制展开根，而是把根的状态也记下来。
- **防抖定时器其实在无限重跑（根因）**：`javax.swing.Timer` 的 `repeats` 默认是 `true`，
  `new Timer(300, listener)` + `restart()` 不是防抖而是“每 300ms 触发一次、永不停”——
  于是自动格式化每隔 300ms 重解析一次并整树重展，用户刚收起的分支立即被弹回（同时 EDT 在空转）。
  抽了包私有 `ui/Debounce.oneShot(delayMs, task)` 显式 `setRepeats(false)`，`JSON格式化` 与
  `JSON对比` 两个定时器共用。对比面板症状不明显只是因为它已经做了展开状态 restore。
- **格式化标签收起后自动弹开**：除了上面那个根因，`JsonTree.showJson` 原先也是直接 `setModel`
  + 按默认策略展开；改成与 `JsonDiffTree` 同一套“先 capture 再 restore”（按 key/下标构成的路径做键），
  没被记录过的新节点仍走默认策略（只展开根及其直接子节点）。
- **标签 `×` 误触**：IntelliJ 新 UI 的 LAF 给 JButton 强加最小宽度，无边框单字符按钮被撑成隐形宽带，
  点标签里的"空白处"就关掉了整个标签。改为把 preferredSize/minimumSize/maximumSize
  三件套一起锁成 `JBUI.scale(16)` 见方（只设 preferredSize 无效，布局取 minimumSize）。
- **拖选膨胀到行尾**：输入区不换行时，单行长 JSON 在 JTextArea 里拖选会触发横向自动滚动，
  选中区间被膨胀成"光标到末尾全选"。所有文本区统一 `setLineWrap(true) + setWrapStyleWord(true)`。
- **对比丢展开状态**：整树 `setModel` 重建会丢失手动展开的节点，改为先 capture 再 restore。
  （当时只改了 `JsonDiffTree`；`JSON格式化` 这一版才补上，且两边都还受下面的定时器问题影响）
- **tooltip 超出屏幕**：历史条目悬浮窗塞入整段 JSON，日志出现
  `popup preferred size is bigger than screen: 310x1941`。改为限 24 行 / 1200 字符，
  单行压缩 JSON 用 `white-space:normal` 让内容折行而不是撑出横条。
- **构建随机失败**：`build.gradle` 未指定源码编码，javac 跟随 Gradle daemon 的默认字符集，
  中文 Windows 上 daemon 换成 JDK 11 启动就把 UTF-8 中文源码读成乱码，一份正常代码报 106 个
  `编码 GBK 的不可映射字符`。显式加 `options.encoding = 'UTF-8'`。

### 已知限制 / 未做

- 编辑器内选中格式化未实现（早期设计的 B 入口，最终只保留工具窗口）。
- 树内搜索（V1 明确 YAGNI 不做）。
- HTML/XML/URL 无历史记录、无严格/宽松开关。
- XML mixed content 空白不做特判；HTML 不规范化属性顺序/引号/大小写，不补 `tbody`/`head`。
- URL 解码不支持 GBK 字符集，也不提供"把 `+` 当空格"的表单模式开关。
- 无快捷键、无 Settings 页。
- 标签顺序不持久化：只存在于当前会话，重启 IDE 后按新建顺序回到默认。
