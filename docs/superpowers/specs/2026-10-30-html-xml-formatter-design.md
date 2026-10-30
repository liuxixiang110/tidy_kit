# HTML / XML 格式化设计(两个格式,两种容错取向)

日期:2026-10-30
状态:实现完成后补记。关键取舍(展现形态、HTML 引擎、XML 引擎、是否做历史)在会话中逐项与需求方确认过。
补记原因:这两块功能走的是"直接实现"而非完整 brainstorming 流程,原先只在 `docs/superpowers/specs/`
外的对话里留下决策依据,现补齐以便后续扩展时对齐。

## 1. 背景与目标

插件已从"JSON 工具"扩成多格式文本工具窗口。需求:再加 HTML 与 XML 的缩进美化。
目标是"把粘进来的标记语言排好版看一眼",不是做一个校验器或规范化器。

## 2. 需求决议

- 展现形态:纯文本缩进输出,**不做树渲染**(与 JSON 标签区分开,后者核心诉求才是折叠美观)
- HTML 引擎:自写轻量 tokenizer(与已有手写 JSON 解析器同风格,零第三方依赖)
- XML 引擎:JDK 自带 DOM + identity Transformer(命名空间/实体/CDATA 交给平台,不重造)
- 历史:HTML/XML 先不做历史池(YAGNI,后续需要再接 `history` 包)
- 模式开关:不做严格/宽松下拉。HTML 一律宽松容错,XML 一律严格报错,策略写死在各自实现里

## 3. 架构

    src/main/java/com/tidy/kit/
    |- format/  TextFormatter(接口) / HtmlFormatter / XmlFormatter / FormatterException
    |- ui/      TextFormatPanel(HTML 与 XML 共用) / TidyKitTabs(按钮与标签注册)

`format` 包只依赖 JDK,不 import Swing 与 com.intellij,可脱离 IDE 单测(与 `parse`/`compare`/`codec` 同一分层原则)。
`TextFormatter` 是无状态单方法接口:

    String format(String source) throws FormatterException

面板持有实例直接复用;无法处理的输入抛异常,不允许静默改写内容。

## 4. HtmlFormatter

单遍 tokenizer 建极简标记树(ELEMENT / TEXT / RAW),再递归渲染,缩进 2 空格。
三张集合表决定排版:

- `VOID`:无闭合标签也不该有子节点(area/base/br/col/embed/hr/img/input/link/meta/param/source/track/wbr)
- `INLINE`:与前后文本并排在一行(a/b/code/i/img/span/strong/sub/sup/u/var 等)
- `RAW_TEXT`:从开标签到匹配闭标签之间不做任何解析与重排(script/style/textarea)
  —— 缩进会改变 textarea 的值,也会破坏脚本

容错与"不擅自改内容"的边界:

- 未闭合标签自动补一个闭标签;配不上对的多余闭标签作为 RAW 原样留一行,不静默丢弃用户内容
- 属性顺序、引号风格、标签大小写一律照搬原文:不做浏览器式规范化,不补 `tbody`、不补 `head`
- 空白折叠成一个空格,但保留"此处原有空白"的首尾标记,否则 `Hello <b>W</b>` 会粘连成 `HelloW`
- 注释与 `<!DOCTYPE>` / CDATA / `<?pi ?>` 各自独占一行

## 5. XmlFormatter

`DocumentBuilderFactory`(namespaceAware)+ identity `TransformerFactory`,缩进 2 空格。
安全:开 `FEATURE_SECURE_PROCESSING`,关 `load-external-dtd` 与外部实体加载 ——
格式化一段贴来的 XML 不该联网,也不该被实体展开拖死。
自定义 `ErrorHandler` 把 error/fatalError 转成 `FormatterException`(带 SAXParseException 的行列号);
平台默认实现只会把解析错误打进 stderr,既污染 IDE 日志又拿不到定位信息。

### 5.1 JDK Transformer 的四个静默失真(实测踩到并逐一验证)

1. **不删空白文本节点就不会重新缩进**:Transformer 只要看到元素之间已存在空白文本节点,就认定
   "已经排过版",粘一份 4 空格缩进的 XML 会原样吐出、缩进宽度换不掉。必须先递归删掉
   *纯缩进的*空白文本节点;但含实义文本的 mixed content 元素要整份保留,那里空白有语义。
2. **根本不输出 DOCTYPE**:`pom.xml` 这类依赖 DOCTYPE 的文件会静默丢声明。
   解决:自己从原文里把 `<!DOCTYPE ...>` 原样拼回首行,注意内部子集 `[ ... ]` 里的 `>` 不是结束符。
3. **会按自己的理解改写 `<?xml ?>` 声明里的 encoding**:设 `OMIT_XML_DECLARATION=yes`,
   把原文声明原样挪到第一行。
4. **Windows 上输出 `\r\n`**:与自写格式化器混用时必须归一成 `\n`,否则逐行断言会以
   "expected `<a>` but was `<a>\r`" 这种看不出问题的形式失败。

适用边界:以上针对 JDK 内置实现(Xalan 移植版);接入 Saxon 等外部 TransformerFactory 时行为不同。

## 6. UI 层

`ui/TextFormatPanel` 被 HTML 与 XML 共用,只靠构造参数 `(kind, TextFormatter)` 区分:

- `JBSplitter` 上输入、下只读结果,两块各带 TitledBorder
- 控制行:`格式化` / `清空` / `复制结果` + 状态栏(`UiFeedback` 统一着色与 HTML 转义)
- 两个文本区都 `setLineWrap(true) + setWrapStyleWord(true)`:不换行时单行超长内容拖选会触发
  横向自动滚动,选中区间被膨胀成"光标到末尾全选"
- 空输入显示引导文案,不算错误;失败时结果区清空并把异常消息打到状态栏(红色)

标签注册在 `TidyKitTabs`:顶部按钮 `HTML格式化` / `XML格式化`,标题取短式 `HTML n` / `XML n`
(全称会把标签栏撑挤),各自独立计数器。

## 7. 测试

`HtmlFormatterTest` 15 项、`XmlFormatterTest` 12 项,均为纯 JUnit 逐行断言:
覆盖未闭合补全、多余闭标签留行、RAW_TEXT 内部原样、inline 与文本并排、空白不粘连、
DOCTYPE 与声明保留、缩进宽度可换、mixed content 不重排、非法 XML 报行列号。

## 8. 已知取舍

- XML 的 mixed content 不做特判,段落里夹标签时空白保持原样(要重排就得引入白名单语义)
- HTML 不做属性排序、不加换行到 `>` 之后、不做缩进内属性对齐 —— 目标是可读,不是规范
- 两者都没有历史池与模式开关;`复制结果` 是唯一出口
- 未提供"HTML 内嵌 JSON 一并格式化"的联动
