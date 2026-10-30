# JSON 对比功能设计(结构化差异树)

日期:2026-10-30
状态:已与需求方逐段确认
关联:扩展 `2026-10-30-json-tree-formatter-plugin-design.md`

## 1. 背景与目标

在现有 JSON Tree Formatter 插件基础上,增加"两个 JSON 对比"功能。目标:按 key 路径做结构化语义对比,识别新增/删除/值变更/类型不同,并以一棵合并差异树呈现。仅个人使用,不发布 Marketplace。

## 2. 需求决议

- 对比语义:结构化差异(按 key 路径逐项),非文本按行 diff
- 顺序处理:对象 key 忽略书写顺序(按 key 匹配);数组按下标逐位比较
- 展现形态:单棵合并差异树,每行显示 `左值 → 右值`,颜色区分状态;默认只展开有差异的分支
- 入口:同一工具窗口(`JsonFormatterTabs`)顶部新增"对比"按钮,新开对比标签,与格式化标签混排
- 技术栈与分层:沿用现有 Java + Gradle + 纯 Java 解析层可脱离 IDE 单测的约定

## 3. 方案选型

采用方案 A:新增纯 Java 对比层,产出独立 DiffNode 树,UI 复用现有 JsonTree 大纲风格渲染。
- 否决方案 B(摊平成 path→value map 再比):丢失树结构,展示合并树时需重建,更别扭
- 否决方案 C(复用 IntelliJ DiffTreeBuilder/文本 diff):退化为按行文本 diff,会把 key 顺序等无关差异算进去,且强绑平台难单测,与"结构化差异"诉求冲突

## 4. 数据模型(新增包 com.tidy.kit.compare,零 IDE 依赖)

    enum DiffStatus { EQUAL, ADDED, REMOVED, CHANGED, TYPE_DIFF }

    class DiffNode {
        DiffStatus status;
        String key;                       // 对象成员名;数组元素为 null
        Integer index;                    // 数组下标;非数组元素为 null
        NodeType leftType, rightType;     // TYPE_DIFF 时用于展示
        String leftText, rightText;       // 叶子值(数字/布尔/null/字符串);容器为 null
        List<DiffNode> children;          // 容器节点的子差异
        boolean hasNestedDiff;            // 子树内是否含差异(驱动默认展开与计数)
    }

- 对外入口:`JsonDiffService.compare(JsonNodeModel left, JsonNodeModel right) -> DiffNode`(根)
- 依赖关系:compare 只依赖 model(复用 `JsonNodeModel`/`NodeType`);解析复用现有 `JsonParseService`;UI 依赖 compare + model。compare 层不 import 任何 com.intellij 类。

## 5. 对比算法(递归)

1. 类型不同 -> 该节点 `TYPE_DIFF`,不再深入子结构
2. OBJECT:取左右 key 并集
   - 仅右有 -> `ADDED`;仅左有 -> `REMOVED`;两边有 -> 递归子对比
   - 自身状态:所有子均 `EQUAL` 则 `EQUAL`,否则容器聚合态 `CHANGED`
3. ARRAY:按下标 `0..max(lenL, lenR)` 对齐
   - 右越界元素 -> `ADDED`;左越界元素 -> `REMOVED`;两边都有 -> 递归
4. 叶子(STRING/NUMBER/BOOLEAN/NULL):`textValue` 相等 -> `EQUAL`,否则 `CHANGED`
   - 数字按字符串精确比较(`1` 与 `1.0` 视为不同,与"存字符串保精度"一致)
5. 自底向上回填 `hasNestedDiff` 与差异计数(新增/删除/变更/类型各计数)
6. 递归深度上限沿用 parse 层 `MAX_DEPTH` 思路,超限按 `CHANGED` 保守处理并在状态摘要提示,避免栈溢出

## 6. UI 层

- 入口:`JsonFormatterTabs` 顶部工具条在"＋"右侧新增"对比"按钮 -> 新开对比标签(命名 `对比 1/2…`,可关闭,至少保留一个标签)
- 对比标签面板 `JsonDiffPanel`(复用 JsonTreePanel 的输入/状态下栏风格):
  - 上半:左右两个输入区(输入 A / 输入 B)并排,共用一个 `ParseMode` 下拉 + `[对比]` 按钮
  - 中部:单棵合并差异树,复用 `JsonTree` 大纲风格 + 新增 `JsonDiffCellRenderer`
    - 行文本:叶子 `key: 左值 → 右值`;容器折叠显示 `{n}` / `[n]`
    - 颜色:ADDED 绿 / REMOVED 红 / CHANGED、TYPE_DIFF 橙 / EQUAL 正常灰;优先取 IDE CodeInsight/语义 ColorKey,回退 JBColor,自动适配 Dark/Light
    - 默认仅展开 `hasNestedDiff` 分支,`EQUAL` 子树折叠;提供"全部展开/收起"(复用现有能力)
  - 下栏:状态摘要 `共 N 处差异(新增 a / 删除 d / 变更 c / 类型 t)`;无差异显示"两段 JSON 等价"

## 7. 错误处理

1. 任一侧解析失败:状态栏红字指明是 A 还是 B 失败,附 `JsonParseException` 行列信息
2. 严格失败不自动降级:复用"宽松模式重试"按钮,对两侧同时切 LENIENT 重比
3. 根类型不同:正常产出单个 `TYPE_DIFF` 根,不报错
4. 超大输入:沿用格式化面板的节点数阈值提示思路

## 8. 测试策略

- 对比层纯 JUnit(脱离 IDE),覆盖:
  - 六类型 EQUAL/CHANGED 各例
  - 对象新增/删除 key
  - 数组按下标增删
  - 类型不同(TYPE_DIFF)
  - 数字 `1` vs `1.0` 判为不同
  - key 顺序不同但内容相同 -> 整体 EQUAL
  - 嵌套 `hasNestedDiff` 与差异计数正确
- UI 层无自动化,runIde 沙盒手动验收差异树配色/展开/摘要

## 9. 里程碑

1. compare 层(DiffStatus/DiffNode/JsonDiffService)+ 单测
2. UI:JsonDiffPanel + JsonDiffCellRenderer(差异树渲染)
3. 接入 JsonFormatterTabs"对比"按钮与标签
4. 错误处理与体验收尾(状态摘要、宽松重试、默认展开调校)

## 10. 环境备注

构建/测试命令与 JAVA_HOME(IDEA 自带 JBR 21)约定同主设计文档第 9 节,不再重复。
