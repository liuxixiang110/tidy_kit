# 标签页拖拽换序 — 设计

日期:2026-11-02
关联:主设计文档 `2026-10-30-json-tree-formatter-plugin-design.md`(工具窗口标签容器)

## 1. 目标与范围

`JSON格式化 1` / `JSON格式化 2` / `JSON对比 1` 这些标签可以互相拖拽换位置。

范围内:

- 新增包私有 `ui/ReorderableTabbedPane`(继承 `JTabbedPane`):重排原语 `moveTab(from, to)` + 标签头拖拽手势。
- `TidyKitTabs` 换用该容器,并去掉"位置 0 关不掉"的限制。

范围外:

- 不改用平台 `ContentManager` 的 content tab(要重写五个入口、计数器、`firstPanel()` 预填等整套标签层,为一个拖拽不划算)。
- 不做拖出工具窗口独立成窗、不做跨 IDE 窗口拖拽、不做"记住顺序到重启后"(顺序是会话内状态)。

关键前提:`TidyKitTabs` 用的是普通 Swing `JTabbedPane`,不是平台 content tab,所以本功能纯 Swing,不碰平台 API。

## 2. 方案选型

**采用:手动鼠标拖拽。** 标签头装 `MouseListener` + `MouseMotionListener`,移动超过阈值进入拖拽态,越过相邻标签中线立即 `moveTab` 实时换位,松手结束。只用 Swing,无 `Transferable`/`DragSource` 那套,行为可控。代价:没有跟随鼠标的半透明拖拽影像,靠"标签实时跳动"表达位置。

否决 A 的备选:标准 AWT DnD(`DragSource` + `DropTarget` + `indexAtLocation`)——要为传一个 int 写 `Transferable`,且实时重排会把正在拖的 header 组件 remove/re-add,与拖拽会话打架。

否决 B 的备选:平台 ContentManager——见范围外。

## 3. 组件

### 3.1 `ui/ReorderableTabbedPane`

```
void installDragReorder(Component header)   // 由 TidyKitTabs 给每个标签头调用
void moveTab(int from, int to)              // 唯一重排入口,拖拽只是它的触发器
```

`moveTab` 的做法:先取出 `getComponentAt(from)` / `getTitleAt(from)` / `getTabComponentAt(from)` 三个引用,`remove(from)`,再 `insertTab(title, null, comp, null, to)` + `setTabComponentAt(to, header)`。

- 被摘掉的只是"标签页挂载关系",内容 JPanel 始终是同一个实例,输入文本、结果树、展开状态、定时器都不受影响。
- `to` 按「结果里排第 to」理解:先 `remove` 后数组长了一个,直接把 `to` 夹到 `[0, count-1]` 后插入。
  不能先把 `to` 减一 —— 那样 `moveTab(c, c+1)` 会退化成原地不动,而拖拽每跨过一根中线做的恰好就是一次
  `moveTab(c, c+1)`,结果就是标签向右永远拖不动(实现时踩过,已有回归用例 `adjacentSwapMovesRight`)。
- `from` 非法或 `from == to` 直接返回。

拖拽手势:

- 按下时记住 **header 实例**而不是下标 —— 实时重排过程中下标一直在变,靠实例身份(`indexOfHeader`)定位当前下标才扛得住连续换位。
- 阈值 `JBUI.scale(5)`,不到阈值不算拖拽,普通点选标签不受影响。
- 拖拽态下把鼠标点换算成 tabbedPane 坐标(`SwingUtilities.convertPoint`),`indexAtLocation` 拿到悬停下标;与当前下标不同就 `moveTab`。悬停在标签条之外返回 -1,不动。
- 从 `×` 按钮上起手不算拖拽:事件中若源组件的祖先含 `JButton` 则忽略。

### 3.2 `ui/TidyKitTabs`

- `tabbedPane` 字段类型换成 `ReorderableTabbedPane`;`addTab` 里建好 header 后调 `installDragReorder(header)`。
- `closeTab` 改为**任何标签都能关,包括最后一个**,关掉最后一个就是空面板。顶部五个按钮仍在,随时能新建。
  这是本设计里唯一的既有行为变更(原先是 `index > 0 && tabCount > 1`)。
- `firstPanel()` 已有 `instanceof` 空判,只在启动时被工厂调用一次,不受拖拽影响。

## 4. 边界

| 情况 | 行为 |
| --- | --- |
| 只有一个标签 | 无相邻可换,拖拽不产生任何变化 |
| 拖到标签条外的空白/内容区 | `indexAtLocation` 返回 -1,不重排 |
| 关掉全部标签 | 允许,面板变空,顶部按钮仍可新建 |
| 拖拽中途按 Esc / 鼠标松开在标签条外 | 结束拖拽态,已换好的顺序保留 |
| 从 `×` 起手并移动 | 不触发拖拽,也不误关标签 |

## 5. 测试

新增 `src/test/java/com/tidy/kit/ui/ReorderableTabbedPaneTest.java`(`moveTab` 是纯下标逻辑,headless 可测):

1. 往后移:`move(0,2)` 后标题顺序为 `[1,2,0]`。
2. **相邻换位向右:`move(0,1)` 得 `[1,0,2]`,再 `move(1,2)` 得 `[1,2,0]`** —— 拖拽逐帧靠的就是这一步。
3. 往前移:`move(2,0)` 后为 `[2,0,1]`。
4. 越界夹住:`move(0,99)` 与 `move(2,-5)` 分别等价于移到尾/移到头。
5. 三者同步:每个位置的 component / title / tabComponent 都跟着同一个 header 实例一起走(用实例身份断言)。
6. 选中跟随内容实例而不是下标:拖完之后用户正在看的面板不能换。
7. `from` 非法或 `from == to` 时标签顺序不变。

手势本身(阈值、悬停判定)按本工程惯例在 `runIde` 沙盒手工验收:

- 三个不同类型标签混排后拖动,确认输入内容与展开状态没丢。
- 点 `×` 不会引起拖动;拖 `×` 也不会误关标签。
- 关掉最后一个标签后界面为空,点顶部按钮能继续新建。

## 6. 文档同步

- `README.md`:标签行说明补"可拖拽换序";"至少保留一个"改成"可全部关闭"。
- `CHANGELOG.md`:新增功能条目,并在变更里写明关闭规则的改变。
- 主设计文档:5.x 补一段指向本文档。
