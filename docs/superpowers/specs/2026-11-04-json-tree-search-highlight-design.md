# JSON 格式化树查找与高亮 —— 设计

日期:2026-11-04　影响范围:`ui/JsonTreePanel`、`ui/JsonTree`、`ui/JsonTreeCellRenderer`,新增两个包私有工具类

## 1. 需求

在「JSON格式化」标签的控制行 `历史 ▾` 右侧加一个输入框和「查找」按钮:

- 在格式化后的 JSON 树里查 **key 和 value**。
- 命中就**展开所在节点**(处于收起状态时),并**高亮查询的关键字**。
- 匹配按**全值匹配、不拆词**:整条 key/value 字符串里出现连续子串即命中,不做分词、不看词边界。

已确认的语义决定:子串包含(非整值相等、非正则)、**大小写敏感**、多命中时**全部高亮 + 跳到第一个**;
在此基础上追加:**重复点「查找」= 循环翻阅命中**,走到最后一处后再点回到第一处。

## 2. 方案

采用:**纯逻辑搜索器 + 渲染器就地判高亮**。

- 搜索走 `JsonNodeModel` 全量模型树,不依赖 TreeNode 是否物化 —— 收起的分支、`LAZY_THRESHOLD` 以上未物化的大节点都能搜到。
- 高亮不维护"命中集合":把 query 交给渲染器,它本来就在用 HTML 逐段拼 key/value,拼的时候顺手判断这段文本里有没有 query。因此重新解析后 query 还在,高亮自动跟着新树走,没有需要与模型重建同步的缓存。

否决「搜索器返回 `Set<JsonNodeModel>` 命中表」:`JsonNodeModel` 未重写 `equals`,只能靠实例身份,且重解析会产生全新实例,必须记得清 —— 而就地判定把这层状态整个删掉了。

否决「`RowFilter` 式过滤视图 / 编辑器 `FindModel`」:需求是展开并高亮所在节点,过滤会藏掉其余内容,语义不对;编辑器查找框架需要先有文本视图,与树渲染不搭。

## 3. 设计

### 3.1 控件(`ui/JsonTreePanel`)

控制行是 `BoxLayout` 横向,在 `historyButton` 之后、`Box.createHorizontalGlue()` 之前插入 `JBTextField` + `查找` 按钮,前面加 8px strut。

输入框**同时锁 preferred 与 maximum size**:BoxLayout 会把组件撑到 maximum,不锁就会把整行拉高(与 `×` 关闭按钮被 IDE 新 UI 撑大是同一类坑)。宽度 `JBUI.scale(160)`。

`findField.addActionListener(e -> doFind())` 让回车等价于点按钮 —— `JTextField` 的回车本来就发 `ActionEvent`,不需要 KeyBinding。循环跳转也走同一个入口,因此**回车与点按钮完全等价**。

### 3.2 命中判定(`ui/JsonTreeSearch`,包私有、纯逻辑、不 import Swing)

```java
record Hit(List<String> steps, JsonNodeModel node) {}
static List<Hit> hits(JsonNodeModel root, String query)
static String stepOf(JsonNodeModel model, int index)   // "k"+key 或 "i"+index
```

- 比对文本只有两处:`getKey()`(对象成员才有,数组元素与根为 null)和 `getTextValue()`(STRING 是解码后的内容,NUMBER 是字面量文本,布尔/null 是 `true`/`null`)。
- **不比** `collapsedSummary()`:`{3 keys}` 是渲染出来的统计,不是数据。
- 匹配 = `text.contains(query)`,大小写敏感。
- 深度优先遍历,父节点排在子孙之前 —— 「第一个命中」就是文档序最靠前的那个。
- 同一条路径上 key 与 value 都命中,只算**一个** Hit(高亮仍是两处,那是渲染器的事)。
- 步骤编码与 `JsonTree.stepOf` 共用同一个方法,避免两处规则漂移(`JsonTree` 那边只负责再加 `STEP_SEP`)。
- `steps` 是从根走到该节点的完整路径,每段都是那一层节点的 key 或下标;根自身命中时为空。与 `JsonTree` 记忆展开状态用的 `prefix` 同一套语义。

数组元素没有 key,靠下标定位;按 key 找子节点时要扫一遍 `getChildAt(i)`(会物化懒节点),这是既有 `expandAllNodes` 同量级的代价,且只对命中路径发生。

### 3.3 高亮(`ui/JsonHighlight`,包私有、纯字符串)

```java
static String mark(String escapedHtmlText, String query, String background, String foreground)
```

把文本里**所有**匹配片段包成 `<span style="background-color:#...;color:#...">片段</span>`;query 为 null/空或不含匹配时原样返回。

必须在 HTML 转义**之后**调用:先包 span 再转义会把 `<` `>` 一起转掉,span 就没了。反过来(先转义再包)匹配的是转义后的文本,所以调用方传进来的 text 与 query 都要是同一状态 —— 渲染器里传的是 `escape(原文)` 之后的片段,query 也走同一次 `escape`。

写法可行性已实测(见 5):JBR 21 离屏渲染 `JLabel`,
`<span style="background-color:#ffff00">` 出 234 个目标色像素、位置落在文字区;`<span bgcolor>`、`<table bgcolor>` 同样生效;`<font background>` 与 `<mark>` **不生效**;`<b><font color>` 生效。所以直接用 span style,不需要退化成加粗。

配色固定一对,不随 Dark/Light 分支:背景 `#FFD666`、前景 `#1E1E1E`。浅黄底配近黑字在深色行背景上同样读得清,少一处主题判断。

### 3.4 展开与选中(`ui/JsonTree` + `JsonTreePanel.doFind()`)

`JsonTree` 只多四个能力:`setSearchQuery(String)`(转发给渲染器 + `repaint()`)、`rootModel()`(从当前模型取根 `JsonNodeModel`,空模型返回 null)、`expandAndSelect(List<JsonTreeSearch.Hit>)`(按 steps 从根走下来构造 `TreePath` → `expandPath`,选中第一个命中并 `scrollPathToVisible`)、`selectAndReveal(Hit)`(只展开**单独一处**的祖先 + 选中 + 滚动,不碰其他分支的展开状态)。

编排顺序在 `doFind()` 里,顺序是有意的:

1. 取 query,空/全空白 → 重置游标、清高亮、提示"请输入要查找的内容",返回。
2. query 与 `lastFindQuery` **不同** → 当新查询:模型为空则直接返回(不报错、不清高亮);
   `hits = JsonTreeSearch.hits(rootModel, query)`;无命中则重置游标、清高亮并报 `未找到 "xxx"`,
   且不记下这个 query(下一次点同一个 query 仍会重搜,而不是当成"跳下一处")。
3. 有命中 → 记下 `lastFindQuery`/`findHits`,`findCursor = 0`。
4. `setSearchQuery(query)` —— 先让高亮生效,再展开,免得展开过程中重绘用的是旧 query。
5. 展开命中路径(条数上限见 3.6),选中第一个并滚动。
6. 状态栏报 `第 x/N 处，共 N 处`。
7. query 与 `lastFindQuery` **相同** → 只推进游标(`findCursor = nextCursor(findCursor, hits.size())`)
   并 `selectAndReveal(findHits.get(findCursor))`,不重搜、不重新全展开。

### 3.5 重复查找 = 循环跳转

面板持有三个字段:`lastFindQuery`(上次真正用于查找的串)、`findHits`(文档序命中列表)、`findCursor`(当前下标,`-1` 表示还没查过)。判定新旧只看**点查找那一刻输入框里的内容**与 `lastFindQuery` 是否相等,不去监听输入变化 —— 光标位置、打字中间态都不该影响一次跳转。

- 游标推进抽成纯函数 `JsonTreePanel.nextCursor(current, total)`:`current + 1 >= total ? 0 : current + 1`。放在面板而不塞进 `JsonTreeSearch`,因为"跳到哪一处"是编排策略,搜索器只负责"哪些地方命中"。
- 跳转只调 `selectAndReveal`:它只展开当前这一条的祖先。首查时已经全展开过,循环时不需要再动别的分支。
- **游标覆盖全部命中,不受 `MAX_EXPAND_HITS` 限制**(见 3.6):上限管的是"一次展开多少条路径"这个重操作,单条展开的代价与总命中数无关,所以第 501 处照样跳得过去。
- 自动格式化重建模型后,`findHits` 里的 `JsonNodeModel` 会是旧实例 —— 但 `Hit.steps()` 仍是有效的路径编码,而跳转是按 steps 从**当前**视图树走下来的,所以旧列表还能用;若某条 steps 在新树里对不上,`selectAndReveal` 静默什么都不做,用户再点一次就跳到下一处。要彻底重来,改一下输入再点即可(算新查询)。
- 状态栏报 `第 x/N 处，共 N 处` —— 序号本身就是回绕的提示,不另外标注"已经绕回第一处"。

### 3.6 上限

`MAX_EXPAND_HITS = 500` 放在 `JsonTreePanel`(编排层定策略,`JsonTree` 只展开它收到的列表,不知道上限这回事):命中太多时全展开会把树撑成几万行、重绘与滚动都卡。超出部分**只高亮不展开**(高亮是就地判定,不依赖展开),状态栏写"第 1/N 处，共 N 处,仅展开前 500 处"。搜到多少处不设"要不要搜"的门槛。循环跳转不受这个上限影响。

### 3.7 与自动格式化的关系

重算后**不需要重跑查找** —— 这是实现前没想透的一点:高亮既然由渲染器拿 query 就地判定,`showJson` 重建模型后自然跟着新树走,展开状态又本来就由 capture/apply 保持;而自动重跑会连带重新展开+滚动,用户只是接着打字,视图却跳一下,反而是干扰。

query 留在输入框里;新内容里不再命中的那些行,高亮跟着消失。解析失败时树保持上一次内容,高亮也不动。

内层转义 JSON:宽松模式下内层文本既在父节点的 `textValue` 里、又可能已展开成子节点,于是父行整段高亮、子行也高亮 —— 符合直觉,不做去重。

## 4. 边界

| 情况 | 行为 |
|---|---|
| query 为空或全空白 | 清高亮,提示"请输入要查找的内容",不动展开状态 |
| 尚未解析 / 输入已清空(空模型) | 直接返回,不报错不提示 |
| 无命中 | 清高亮,状态栏 `未找到 "xxx"` |
| 命中 > 500 | 全部高亮,只展开前 500 条路径,状态栏说明;游标仍能跳到全部命中 |
| 同一 query 反复点查找/回车 | 不重新搜索,游标往后走一处;越过最后一条回到第一处 |
| 改完输入再点查找 | 算新查询,重新搜索并停在第一处 |
| 上一次没找到,再点同一个 query | 仍然重搜(没找到时不记 query),不会误当成"跳下一处" |
| 查找后重算,旧命中在新树里对不上 | `selectAndReveal` 静默不动,再点一次跳下一处 |
| 命中在收起的分支 / 未物化的大节点 | 照样搜到并展开(搜索走 model 树) |
| 根节点自身命中 | steps 为空,路径就是根,`expandPath` 展开根 |
| 父与子都命中 | 都展开、都高亮,按文档序父在前 |
| key 与 value 同时命中同一节点 | 算一处命中,两处文本都高亮 |
| query 含 `<` `>` `&` | 与文本同走一次 HTML 转义后再匹配,不破坏渲染 |
| 查找后修改输入 | 不需要重跑查找:高亮由渲染器就地判定,自动跟着新树;展开状态由 capture/apply 保持 |

## 5. 测试

纯逻辑单测(无 Swing 依赖,headless 安全):

- `JsonTreeSearchTest`:命中 key、命中 value(含数字字面量子串)、大小写敏感、不拆词(`user name` 命中而 `name user` 不命中)、容器不比折叠摘要、数组按下标出步骤、深度优先且父在前、key+value 同命中只算一条、空/null query、无命中、嵌套路径完整序列。
- `JsonHighlightTest`:null/空 query 原样返回、多处出现全部包裹、只包一次出现的片段、转义后的 `&lt;` 不被二次破坏、span 属性拼接正确。
- `JsonTreeFindCursorTest`:逐格推进、越过最后一条回到 0、单命中永远停在 0、多轮回绕、任意 total 下游标不越界、未查过(-1)落在第一处。

交互部分按本工程惯例在 `runIde` 沙盒手工验收(合成 `dispatchEvent` 不沿 Swing 的 dispatcher 链冒泡,这类交互在进程内测不了):

- 收起的分支里有命中 → 点查找后该分支被展开且关键字有黄底。
- 多处命中 → 全部高亮,视图滚到文档序第一处并选中。
- 超大 JSON(懒节点)里命中 → 能搜到。
- 查找后继续编辑输入 → 高亮跟着新结果走。
- 无命中与空输入 → 状态栏文案对,且旧高亮被清掉。
- 多处命中后反复点查找(或连按回车) → 选中行逐处往后跳、每次都滚到可见;跳到最后一条再点应回到第一处。
- 循环跳转过程中改一下输入再点 → 回到"新查询",停在第一处。

## 6. 文档同步

`README.md` 的 JSON格式化 一节加一句查找说明;`CHANGELOG.md` 未发布版本增补功能条目;主设计文档 5.3 的控制行描述补上查找控件。
