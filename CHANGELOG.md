# Changelog

全部时间按 2026-10-30 记。版本号目前停在 `0.1.0`（个人使用，不发布，不再细分）。

## 0.1.0 — 2026-10-30

### 新增

- **JSON 树视图**（`JSON格式化`）：自写递归下降解析器（不引第三方库），大纲风格渲染
  （无连线、纯缩进、清晰折叠三角），严格/宽松双模式，宽松模式支持提取平衡主体、
  容忍单引号/尾逗号/注释、自动解开"顶层是转义 JSON 字符串"的内层，节点数超 100 000 提示。
- **JSON 对比**（`JSON对比`）：按 key 路径做结构化语义递归比对，识别新增/删除/值变更/类型不同，
  合成一棵合并差异树；输入停止 300ms 自动对比；`仅展示差异` 过滤；重渲染保留手动展开状态。
- **两层历史池**（`history` 包，跨 IDE 重启）：单段 JSON 上限 50 条，对比现场**成对**上限 30 条
  （同时回填 A、B 与当时模式）；超 200 000 字符不入池；同内容只刷新时间不新增。
- **HTML 格式化**（`format/HtmlFormatter`）：自写轻量 tokenizer，零依赖，宽松容错优先。
- **XML 格式化**（`format/XmlFormatter`）：JDK DOM + identity Transformer，严格报错（带行列号），
  `FEATURE_SECURE_PROCESSING` + 关闭外部 DTD/实体加载，声明与 DOCTYPE 按原文补回。
- **URL 编码/解码**（`codec/UrlCodec`）：RFC 3986 百分号编解码，固定 UTF-8，宽松解码。
- **多标签**：每类功能可开多个标签并存，各自独立状态；标签头带 `×` 关闭（至少保留一个）。
- 顶部按钮行：`JSON格式化` / `JSON对比` / `URL编码/解码` / `HTML格式化` / `XML格式化`。
- 97 个纯逻辑层单测（不启 IDE 即可跑）。

### 变更

- 插件显示名：`JSON Tree Formatter` / `JSON Formatter` → `Formatter` → **`TidyKit`**。
  内部标识（插件 id `com.you.jsonformatter`、包名、Gradle 构件名、历史文件名）全程未变，
  历史记录与启用状态不受改名影响；只有 `workspace.xml` 里旧 toolWindow id 的停靠状态失效一次。
- 顶部原 `+` 按钮改名 `JSON格式化`，语义不变（新建一个 JSON 标签）。
- 标签页命名从 `JSON n` 改为按功能命名（`JSON格式化 n` / `JSON对比 n`，HTML/XML/URL 取短式）。
- 状态栏着色与剪贴板复制从 4 个面板里的重复实现抽成包私有 `ui/UiFeedback`。
- `plugin.xml` 的 `<description>` 重写，覆盖新增的对比/HTML/XML/URL 能力。

### 修复

- **标签 `×` 误触**：IntelliJ 新 UI 的 LAF 给 JButton 强加最小宽度，无边框单字符按钮被撑成隐形宽带，
  点标签里的"空白处"就关掉了整个标签。改为把 preferredSize/minimumSize/maximumSize
  三件套一起锁成 `JBUI.scale(16)` 见方（只设 preferredSize 无效，布局取 minimumSize）。
- **拖选膨胀到行尾**：输入区不换行时，单行长 JSON 在 JTextArea 里拖选会触发横向自动滚动，
  选中区间被膨胀成"光标到末尾全选"。所有文本区统一 `setLineWrap(true) + setWrapStyleWord(true)`。
- **对比丢展开状态**：整树 `setModel` 重建会丢失手动展开的节点，改为先 capture 再 restore。
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
