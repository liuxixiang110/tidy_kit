# JSON 格式化树右键复制 — 设计

日期:2026-11-02
关联:主设计文档 `2026-10-30-json-tree-formatter-plugin-design.md` 5.3(面板与树交互)

## 1. 目标与范围

在"JSON格式化"的结果树上,右键可复制**选中节点及其下所有子节点**组成的完整 JSON。

范围内:

- `JsonTree`(格式化标签的结果树)新增右键菜单,菜单仅一项"复制"。
- 模型层新增一个"复制专用"的序列化入口,保证复制产物是合法、可再解析的 JSON。

范围外(明确不做):

- **"JSON对比"的差异树不加右键复制。** 原提案包含它,后因差异节点没有单一"内容"可复制(每节点存的是左右两侧各自的值)而砍掉。`JsonDiffTree`、`JsonDiffCellRenderer`、`JsonDiffPanel` 均不改动。
- 不加键盘 Ctrl+C 绑定,不加"复制路径""复制键名"等额外菜单项。
- HTML/XML、URL 编解码标签无关,不涉及。

## 2. 复制语义

复制对象 = 选中节点这一棵子树,输出美化 JSON(缩进 2 空格),满足两条:

1. **不带该节点自身的 key。** 右键 `"user": {…}` 里的 user 节点,复制出 `{…}`,而不是 `"user": {…}`。后者单独粘出去不是合法 JSON。子节点照常带自己的 key。
2. **不追加 `(escaped JSON):` 注释。** 宽松模式解析内层转义 JSON 后,`toPrettyString` 会给该字段补一行 ` (escaped JSON):` 再展开内层内容——那是给人看的,粘出去 JSON 就废了。复制路径整棵树都不输出这条注释,只输出字符串值本身(带引号、保留 `\n` 等转义)。要看展开内容,在树上展开该节点即可,不受影响。

数组元素、根节点本身就没有 key,不受第 1 条影响;根节点复制结果与"复制结果"按钮一致。

## 3. 组件改动

### 3.1 `model/JsonNodeModel`

`appendPretty(StringBuilder, int level)` 扩为 `appendPretty(StringBuilder sb, int level, boolean selfKey, boolean annotateEscaped)`:

- `keyPart` 仅在 `selfKey` 为 true 时输出;
- `escapedInner` 注释块仅在 `annotateEscaped` 为 true 时输出;
- 递归子节点恒传 `selfKey = true`,`annotateEscaped` 沿用本次的值(整棵树口径一致)。

公开入口两档:

| 方法 | 参数 | 用途 | 行为 |
| --- | --- | --- | --- |
| `toPrettyString()` | `(true, true)` | 面板"复制结果"按钮、状态摘要 | **完全不变**,现有黄金文件测试必须继续通过 |
| `toPrettyValueString()` | `(false, false)` | 右键复制 | 新增 |

### 3.2 `ui/JsonTree`

- 构造签名变为 `JsonTree(Consumer<String> statusReporter)`。状态文案由外部给,树不持有 `JBLabel`,保持与面板解耦。
- 内部装一个 `MouseListener`,只处理 `isPopupTrigger()`:
  - `getPathForLocation(e.getPoint())` 命中节点 → 先 `setSelectionPath(path)` 再弹菜单。理由:不先移动选中位,复制到的会是上一次点选的行,和右键点的位置不一致。
  - 未命中(点在空白处)、或模型为空(`getModel().getRoot() == null`,即新建标签/输入已清空) → 不弹菜单。
  - 菜单一项"复制" → `UiFeedback.copyToClipboard(node.getModel().toPrettyValueString())`,随后 `statusReporter.accept("已复制选中节点的内容,共 N 行")`(N = 复制文本的换行数 + 1)。
- 菜单用 Swing `JPopupMenu`/`JMenuItem`(与本工程既有 UI 代码一致,`HistoryPopup` 已验证该路径在 IntelliJ 主题下正常),不引入平台 `ActionGroup`。

**懒构建不影响复制正确性**:`JsonTreeNode` 对超大节点(子节点数 > `LAZY_THRESHOLD` = 5000)延迟物化,但序列化读的是 `JsonNodeModel` 原始模型,不经过视图层,因此收起的大节点复制出来依然完整。

### 3.3 `ui/JsonTreePanel`

`new JsonTree(msg -> setStatus(msg, false))`。字段初始化处引用实例方法是安全的(回调在用户操作时才触发)。编辑器内 Dialog 复用 `JsonTreePanel`,自动获得该功能,无需另做。

## 4. 错误与边界

| 情况 | 行为 |
| --- | --- |
| 树为空模型 | 不弹菜单 |
| 右键在树空白处 | 不弹菜单 |
| 选中叶子(字符串/数字/布尔/null) | 复制其值,字符串带引号,`null` 输出 `null` |
| 空对象/空数组 | 复制 `{}` / `[]` |
| 超大节点处于收起(懒)状态 | 整棵子树照常完整复制 |
| 剪贴板不可用 | 不做特殊处理,异常交由 Swing 默认路径,与既有"复制结果"按钮行为一致,不新增差异 |

## 5. 测试

`PrettyStringTest` 补 4 条(总量 100 → 104):

1. 对象成员节点 `toPrettyValueString()` 不以 `"key": ` 开头,且子成员仍带 key。
2. 数组元素节点复制得到该元素的值。
3. 含内层转义 JSON 的节点,复制结果中不出现 `escaped JSON` 字样。
4. **硬判据**:1、2、3 得到的字符串都能被严格模式 `JsonParseService.parse(..., STRICT)` 重新解析,且再 `toPrettyString()` 稳定(幂等)。

右键命中、选中位移动属 Swing 交互,不做自动化测试,按本工程惯例在 `runIde` 沙盒手工验收。

手工验收清单:

- 右键 `{…}` 里某个对象成员 → 复制 → 粘到输入框,自动格式化成功且树上只剩该子树。
- 右键叶子字符串 → 复制结果带引号。
- 先点选 A 行,再右键 B 行 → 复制到的是 B。
- 输入清空后在空树上右键 → 无菜单。
- 对比标签树上右键 → 仍无菜单(本次未做)。

## 6. 文档同步

- `README.md`:JSON格式化小节增加右键复制说明。
- `CHANGELOG.md`:新增功能条目(注明对比树未纳入)。
- 主设计文档 5.3 布局行补一句右键复制。
