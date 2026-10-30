# JSON Tree Formatter 插件设计(严格/宽松双模式 JSON 格式化 + 大纲风格树视图)

日期:2026-10-30
状态:已与需求方逐节确认

## 1. 背景与目标

现有 IDE JSON 格式化工具的树展开/收起不美观。目标:开发一个 IntelliJ/GoLand 插件,提供"简洁大纲风格"(无连线、纯缩进、清晰折叠三角,类似 IDE Project 面板)的 JSON 树视图,同时支持工具窗口和编辑器内选中格式化两个入口。仅个人使用,不发布 Marketplace。

## 2. 需求决议

- 核心诉求:树视图展开/收起美观(简洁大纲风格)
- 入口:A(ToolWindow)+ B(编辑器选中格式化)都要
- 解析模式:严格/宽松开关,默认严格;宽松模式含去转义、脏输入容错
- 树内搜索:V1 不做(YAGNI)
- 技术栈:Java + Gradle + gradle-intellij-plugin;本机 IntelliJ IDEA 2024.3.4.1(Ultimate)用于 runIde 调试
- 分发:本地 buildPlugin 出 zip,Install Plugin from Disk

## 3. 方案选型

采用方案 1:Swing JTree 自绘树 + 独立纯 Java 解析层。
- 否决方案 2(复用 IDE Json Editor):折叠样式仍受 IDE 默认控制,无法满足美化诉求
- 否决方案 3(TreeWithSelectionUtil 内部 API):文档少、版本升级易破

## 4. 架构

    json-tree-formatter/
    |- build.gradle / settings.gradle
    |- src/main/resources/META-INF/plugin.xml
    |- src/main/java/com/tidy/kit/
        |- model/        JsonNodeModel
        |- parse/        JsonParseService / StrictParser / LenientParser / JsonParseException
        |- ui/           JsonTree / JsonTreeCellRenderer / JsonTreePanel
        |- toolwindow/   JsonFormatterToolWindowFactory
        |- action/       FormatJsonInEditorAction

分层原则:model 零依赖;parse 只依赖 model;ui 只依赖 model;action/toolwindow 是薄壳。解析层不 import 任何 com.intellij 类,可脱离 IDE 单测。
## 5. 组件设计

### 5.1 JsonNodeModel
单类 + NodeType 枚举 {OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL}。字段:key(数组元素为 null)、textValue(数字存字符串保精度)、children、depth、startLine/endLine、escapedInner(内层转义 JSON 原始串)。

### 5.2 JsonParseService
- 入口:parse(String input, ParseMode mode) -> JsonNodeModel,失败抛 JsonParseException(带行列号)
- STRICT:仅标准 JSON
- LENIENT 流水线:① 扫描第一个平衡 {/[ 提取主体 ② 顶层带引号转义字符串递归 parse 内层 ③ 容忍单引号/尾逗号/注释
- 自写递归下降解析器,不引第三方库(需保留错误定位上下文,现成库不可控;规模约 300 行内)

### 5.3 JsonTreePanel(工具窗口与 Dialog 复用)
布局:输入区 JTextArea + 模式下拉 + [格式化] -> 树区 -> [复制结果] + 状态栏。
- 树外观:setRowHeight(字体行高+2)、setShowsRootHandles(true)、putClientProperty("JTree.lineStyle","None") -> 无连线纯缩进 + 折叠三角
- 颜色/字体全部取 IDE ColorKey/FontKey,自动适配 Dark/Light
- Renderer:key 用局部变量色,字符串用字面量色,数字用类型色;对象/数组折叠后行尾显示 {7 keys} / [12] 计数
- 性能:children > 5000 的节点默认折叠懒展开;节点总数 > 10 万提示切纯文本模式

### 5.4 动作层
- JsonFormatterToolWindowFactory:侧边 ToolWindow,内容即 JsonTreePanel,启动时自动填入剪贴板内容(可关)
- FormatJsonInEditorAction:编辑器右键 + Ctrl+Alt+J,选中文本 -> Dialog 内嵌 JsonTreePanel;提供"原地替换为格式化文本"

数据流:展示 = 输入 -> parse -> JsonNodeModel -> JsonTree;格式化 = 输入 -> parse -> toPrettyString(缩进 2 空格) -> 复制/回写。

## 6. 错误处理

原则:解析层只抛带定位信息的错误,UI 决定呈现,不做静默降级。
1. JsonParseException 携带行列号、上下文片段(前后 20 字符)、期望内容;状态栏红字,点击定位高亮输入区
2. 严格失败不自动切宽松,报错旁给"宽松模式重试"按钮
3. 宽松容错白名单:前后杂质、顶层转义字符串、注释、单引号、尾逗号;不容忍:括号不平衡、字符串截断、无 JSON 主体
4. 内层转义 JSON 解析失败不影响外层:按原始字符串渲染 + 灰色 (escaped JSON, 内层解析失败) 标记
5. 原地替换前弹 Diff 预览二次确认;Dialog 关闭即释放

## 7. 测试策略

- 解析层:纯 JUnit。覆盖六类型递归、数字精度、行列号断言、宽松容错逐项正/负例、1000 层嵌套不栈溢出(超限报"嵌套过深")
- toPrettyString:黄金文件对比
- UI 层:无自动化,runIde 沙盒手动验收树外观

## 8. 里程碑

1. 项目骨架(Gradle + plugin.xml + runIde 跑通空 ToolWindow)—— 已完成
2. 解析层 + 单测(model / strict / lenient / toPrettyString)—— 已完成(2026-10-30,25 项单测通过)
3. 树渲染(JsonTreePanel + Renderer,大纲风格外观调校)—— 代码完成(compileJava/buildPlugin 通过,待 runIde 沙盒验收树外观)
4. 两个入口(ToolWindow 完整化 + Editor Action + Dialog)
5. 错误处理与体验收尾(状态栏定位、Diff 回写、剪贴板自动填入)

## 9. 本机环境落地备注(2026-10-30 实测)

- 无系统 JDK 17:PATH 里是 JDK 11。改用 IDEA 自带 JBR 21(C:\Program Files\JetBrains\IntelliJ IDEA 2024.3.4.1\jbr)作为 Gradle toolchain,并用 options.release = 17 锁死字节码兼容 2024.1 平台
- services.gradle.org 不可达,Gradle 发行版走腾讯镜像;gradle wrapper 任务需 --gradle-distribution-url 指向镜像,否则 URL 校验失败
- gradle.properties 中 org.gradle.java.installations.paths 的反斜杠必须写成双反斜杠(Java properties 转义),否则路径被吃掉
- 常用命令(需先设 JAVA_HOME 为上述 JBR 路径):
  .\gradlew.bat runIde        启动沙盒 IDE
  .\gradlew.bat buildPlugin   产出 build\distributions\json-tree-formatter-0.1.0.zip
  .\gradlew.bat verifyPlugin  描述符合规校验
  .\gradlew.bat test          解析层单测