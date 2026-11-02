# 标签页拖拽换序 — 设计

日期:2026-11-02（首版同日修订:实时换位改为松手生效，见 3.4）
关联:主设计文档 `2026-10-30-json-tree-formatter-plugin-design.md`（工具窗口标签容器）

## 1. 目标与范围

`JSON格式化 1` / `JSON格式化 2` / `JSON对比 1` 这些标签可以互相拖拽换位置。

范围内:

- 新增包私有 `ui/ReorderableTabbedPane`（继承 `JTabbedPane`）:重排原语 `moveTab(from, to)` + 标签头拖拽手势 + 标签头工厂回调。
- `TidyKitTabs` 换用该容器，并去掉"位置 0 关不掉"的限制。

范围外:

- 不改用平台 `ContentManager` 的 content tab（要重写五个入口、计数器、`firstPanel()` 预填等整套标签层，为一个拖拽不划算）。
- 不做拖出工具窗口独立成窗、不做跨 IDE 窗口拖拽、不做"记住顺序到重启后"（顺序是会话内状态）。

关键前提:`TidyKitTabs` 用的是普通 Swing `JTabbedPane`，不是平台 content tab，所以本功能纯 Swing，不碰平台 API。

## 2. 方案选型

**采用:手动鼠标拖拽，松手生效。** 标签头装 `MouseListener` + `MouseMotionListener`，按下后记住被拖的是哪个标签，拖动过程中只更新"悬停目标"，**松手才调用一次 `moveTab`**。只用 Swing，无 `Transferable`/`DragSource` 那套，行为可控。

代价:没有跟随鼠标的拖拽影像，也没有实时跳动——拖的时候看不出会插到哪，松手那一下标签才跳到位。

否决实时换位（本文档首版曾采用）:它必须在鼠标还按着的时候反复改标签模型，而标签头组件的事件冒泡链会被这种"摘下来再塞回去"破坏，实测症状是换完序之后点其他标签不再切换选中。详见 3.4。

否决 A 的备选:标准 AWT DnD（`DragSource` + `DropTarget` + `indexAtLocation`）——要为传一个 int 写 `Transferable`，且 DnD 会话对被拖组件有所有权假设，与重排冲突。

否决 B 的备选:平台 ContentManager——见范围外。

## 3. 组件

### 3.1 `ui/ReorderableTabbedPane`

```
interface HeaderFactory { JComponent create(String title) }   // 由使用方提供
void setHeaderFactory(HeaderFactory factory)
void installDragReorder(Component header)                     // 由 TidyKitTabs 给每个标签头调用
void moveTab(int from, int to)                                // 唯一重排入口，只在松手后的 invokeLater 里调一次
```

`moveTab` 的做法:取出 `getComponentAt(from)` 与 `getTitleAt(from)` → `remove(from)` →
`insertTab(title, null, comp, null, target)` → `setTabComponentAt(target, headerFactory.create(title))` → `setSelectedComponent(comp)`。

- 被摘掉的只是"标签页挂载关系"，内容 JPanel 始终是同一个实例，输入文本、结果树、展开状态、定时器都不受影响。
- 标签头不复用，**用工厂新建**：新建走的是与首次 `addTab` 完全相同的路径（生产环境里确认能点亮的配置），避开刚被 remove 的组件的脏状态。没设工厂时退回复用旧实例（`oldHeader`），保证容器单独被使用时不丢标题。
- 换位后选中被显式设回被挪走的那个标签——用户刚拖的东西得留在眼前。
- `to` 按「结果里排第 to」理解:先 `remove` 后数组长了一个，直接把 `to` 夹到 `[0, count-1]` 后插入。
  不能先把 `to` 减一——那样 `moveTab(c, c+1)` 会退化成原地不动，向右移一个标签就成了空操作
  （实现时踩过，已有回归用例 `adjacentSwapMovesRight`）。
- `from` 非法或 `from == to` 直接返回。
- 标签头左右各留 `JBUI.scale(6)` 内边距（`TidyKitTabs.buildTabHeader` 用 FlowLayout 的 hgap 实现）:
  tab 的尺寸跟着标签头的首选尺寸走，这段空隙算在标签头自己身上而不是留给 pane 当内边距，可点区域就更大。

### 3.2 拖拽手势

- 按下时记住**被拖标签的内容面板实例**（`getComponentAt(indexOfHeader(header))`），不记下标也不记标签头：标签头会在重排时被换掉，内容实例才稳定；下标则每次移动都失效。
- 位移阈值 `JBUI.scale(5)`，不到阈值不算拖拽。**按下与是否拖拽无关**:标签头一收到按下就 `setSelectedIndex(自己)`，
  整块标签都是选中热区——自定义 tabComponent 不是 `JTabbedPane` 的直接子组件，而是被 UI 放进私有的
  `TabContainer`，指望冒泡到 pane 再做一次 `tabForCoordinate`，真正能点中的就只剩标签外围那圈内边距。
- 拖拽态下把鼠标点换算成 tabbedPane 坐标（`SwingUtilities.convertPoint`），`indexAtLocation` 拿到悬停下标存入 `hoverTarget`；**这里不改模型**。悬停到标签条外返回 -1，保留上一个目标。
- 松开时若确曾进入拖拽态，`SwingUtilities.invokeLater` 里按 `indexOfComponent(content)` 重新解析当前下标再 `moveTab`。放到 invokeLater 是为了让这次 release 在事件队列里先走完（标签自己的选中逻辑），再改模型。
- 从 `×` 按钮上起手既不算拖拽也不选中:事件中若源组件的祖先含 `JButton` 则整段忽略（关闭由按钮自己的 ActionListener 负责）。
- 拖拽期间整条标签换成 `MOVE_CURSOR`，松手恢复默认。

### 3.3 `ui/TidyKitTabs`

- `tabbedPane` 字段类型换成 `ReorderableTabbedPane`；构造里 `setHeaderFactory(this::newHeader)`，`addTab` 与重排后重建都走同一个 `newHeader(title)`（建头 + `installDragReorder`）。
- `closeTab` 改为**任何标签都能关，包括最后一个**，关掉最后一个就是空面板。顶部五个按钮仍在，随时能新建。
  这是本设计里唯一的既有行为变更（原先是 `index > 0 && tabCount > 1`）。
- `firstPanel()` 已有 `instanceof` 空判，只在启动时被工厂调用一次，不受拖拽影响。

### 3.4 为什么不许在鼠标按着的时候改模型（踩坑记录）

首版做成了"越过相邻标签中线立即换位"，用着很顺，但换完序之后**点其他标签不再切换选中**。机制:

- 自定义标签头组件并不直接是 `JTabbedPane` 的孩子，而是被 UI 的私有容器 `BasicTabbedPaneUI$TabContainer` 承载
  （`JTabbedPane.removeTabAt` 会 `setTabComponentAt(index, null)`，UI 收到 `indexForTabComponent` 属性变更后
  调 `tabContainer.removeUnusedTabComponents()` 把它摘下来）。
- 点击要能切换选中，前提是点击事件从标签头一路冒泡到 `JTabbedPane`，UI 在那里用
  `tabForCoordinate(...)` 判下标并 `setSelectedIndex`。而子组件往父级派发事件所依赖的 dispatcher 链，
  是 `Container.addImpl` 在"加入那一刻"建立的。
- 一次拖动里对同一个标签头反复 `remove` + `setTabComponentAt`，动的正是这条链；链断了，之后的点击就到不了 pane。

顺带一个坑:合成事件（`component.dispatchEvent(new MouseEvent(...))`）**不会**沿这条链冒泡到祖先，
所以这种交互没法用无头/进程内测试验证，只能真机点。别指望用合成事件写这类回归测试。

## 4. 边界

| 情况 | 行为 |
| --- | --- |
| 只有一个标签 | 无相邻可换，`moveTab(from, from)` 直接返回 |
| 拖到标签条外的空白/内容区 | `indexAtLocation` 返回 -1，保留上一个悬停目标 |
| 全程没越过任何标签就松手 | `hoverTarget` 为 -1 或与自身相同，不动模型 |
| 关掉全部标签 | 允许，面板变空，顶部按钮仍可新建 |
| 从 `×` 起手并移动 | 不触发拖拽，也不误关标签 |

## 5. 测试

`src/test/java/com/tidy/kit/ui/ReorderableTabbedPaneTest.java`（`moveTab` 是纯下标/实例逻辑，headless 可测）:

1. 往后移:`move(0,2)` 后标题顺序为 `[1,2,0]`。
2. **相邻换位向右:`move(0,1)` 得 `[1,0,2]`，再 `move(1,2)` 得 `[1,2,0]`**。
3. 往前移:`move(2,0)` 后为 `[2,0,1]`。
4. 越界夹住:`move(0,99)` 与 `move(2,-5)` 分别等价于移到尾/移到头。
5. 内容面板按实例跟着走，标签头是工厂新建的实例（标题一致）；没设工厂时标题与内容仍正确。
6. 换位后选中的是被挪走的那个标签。
7. `from` 非法或 `from == to` 时标签顺序不变。

手势本身（阈值、悬停判定、松手生效、以及"换序后还能不能点别的标签"）按本工程惯例在 `runIde` 沙盒手工验收:

- 拖动换序后**逐个点其他标签确认还能切换**（这条就是 3.4 那个缺陷的验收判据）。
- 三个不同类型标签混排后拖动，确认输入内容与展开状态没丢。
- 点 `×` 不会引起拖动；拖 `×` 也不会误关标签。
- 点标签的任意位置（标题文字、文字与 × 之间、标签左右空白）都能选中；只有 × 例外。
- 关掉最后一个标签后界面为空，点顶部按钮能继续新建。

## 6. 文档同步

- `README.md`:标签行说明"可拖拽换序（松手生效）"；"至少保留一个"改成"可全部关闭"。
- `CHANGELOG.md`:功能条目 + 实时换位改松手生效的原因。
- 主设计文档 5.5:指向本文档。
