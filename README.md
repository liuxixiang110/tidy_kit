# TidyKit

一个自用的 JetBrains 全家桶插件（IDEA / GoLand / DataGrip / PyCharm / WebStorm / PhpStorm / CLion / Rider / Aqua）：
把杂乱的文本收拾干净。一个工具窗口里用标签承载多类小工具，结果都能一键复制。

- 兼容：**下限 build 241（2024.1），不封顶** —— `patchPluginXml` 根本不往 `plugin.xml` 里写 `until-build`，
  平台视同无上限，因此 JetBrains 出新主版本也不需回来改这一行
- 只依赖 `com.intellij.modules.platform`，**编译期也不挂 Java 插件**（源码不碰 PSI），
  所以不带 Java 插件的 IDE（DataGrip、WebStorm、Aqua 等）不会被依赖卡住
- 分发方式：本地打包 + Install Plugin from Disk，**不发布 Marketplace**

## 安装

1. 打包：

   ```powershell
   .\gradlew.bat buildPlugin
   ```

   产物在 `build\distributions\tidy-kit-0.3.0.zip`（zip 文件名取自 Gradle 项目名与版本号）。

   > **不要加 `clean`**：沙盒实例的配置与历史存在 `build\idea-sandbox\config\` 里，`clean` 会一并删掉。
   > 需要全量重编时用 `--rerun-tasks`。

2. 在 IDEA 里：`File → Settings → Plugins → ⚙（齿轮）→ Install Plugin from Disk...`，选中那个 zip。

   GoLand / DataGrip 同理；手工落盘时插件目录是 `%APPDATA%\JetBrains\<产品><版本>\plugins\`
   （例如 `IntelliJIdea2024.3`、`GoLand2026.2`、`DataGrip2026.1`）。
3. 重启 IDEA。

   > 如果你之前装过旧包（插件 id 为 `com.you.jsonformatter`），**先在 `Settings → Plugins` 里把它卸载**再装新的。
   > 当前包的插件 id 已改成 `com.tidy.kit`，IDEA 会当成另一个插件；
   > 两个同时存在会出现两个同名的 TidyKit 工具窗口。
4. 右侧边条出现 **TidyKit** 竖条，点它打开工具窗口（或 `View → Tool Windows → TidyKit`）。

改代码后的日常循环：`buildPlugin` → 重启 IDEA（比每次跑 `runIde` 开两套窗口省事）。
要验证不干净环境下的行为，再用 `.\gradlew.bat runIde` 起独立沙盒。

## 六个入口

顶部一排按钮，每点一次新开一个标签，标签右上角 `×` 关闭。标签可以全部关掉（面板为空，点顶部按钮随时再建）。
点标签任意位置都能切换到它（除了 `×`）。标签头按住左右拖动可以换顺序，松手时生效（拖动过程只跟踪悬停目标，真正换位放在松手后做一次）；换序只动标签的挂载关系，输入内容与展开状态不会丢。

| 按钮 | 标签名 | 干什么 |
|---|---|---|
| JSON格式化 | `JSON格式化 n` | 解析 JSON 渲染成大纲风格树（无连线、纯缩进、清晰折叠三角），输入即自动重算，可全部展开/收起，可按 key/value 查找并高亮（重复查找循环跳转），右键可复制子树 |
| JSON对比 | `JSON对比 n` | 左右两段 JSON 按 key 路径做结构化语义比对，合成一棵差异树 |
| URL编码/解码 | `URL n` | 百分号编码与解码，UTF-8 |
| HTML格式化 | `HTML n` | HTML 重排缩进 |
| XML格式化 | `XML n` | XML 重排缩进 |
| SQL参数格式化 | `SQL n` | 粘贴 GORM SQL 日志，把 `?` 占位符逐个回填为参数，可再美化换行缩进 |

### JSON格式化

- **输入停止约 300ms 自动解析渲染**，没有“格式化”按钮（与对比面板同一套防抖）。
  输入框清空时下方结果树与状态栏一起清空，新开的标签也是空白。
- **模式**：`严格`（标准 JSON）/ `宽松`。宽松会扫描并提取第一个平衡的 `{}`/`[]` 主体、
  容忍单引号、尾逗号、注释，并且会把"顶层是一个被转义的 JSON 字符串"这种现场自动解开内层。
  严格失败时不会自动降级，而是出现`宽松模式重试`按钮。
- 解析成功的文本自动入历史池，`历史 ▾` 可回填（回填后同样会自动重算）。
  副作用：改成自动格式化后，输入过程中恰好合法的中间态（比如 `[{"a":1}]` 敲到一半的
  `{"a":1}`）也会被记一条。
- **查找**：`历史 ▾` 右边的输入框 + `查找` 按钮，回车等价于点按钮。按**子串包含**在 key 与 value 里找，
  区分大小写、不拆词（`user name` 能命中，`name user` 不能）。命中就展开所在分支、给关键字垫上黄底，
  并选中滚到文档序第一处。收起的分支、还没展开的超大节点也能搜到 —— 查找走的是解析出的模型树，
  不是可见行。命中超过 500 处时只展开前 500 处（高亮与循环跳转都不受这个上限影响）。折叠摘要 `{n keys}` 不参与匹配。
  查找后接着改输入不用重查：高亮由渲染器就地判定，自然跟着新结果走。
- **重复查找 = 循环翻阅**：查询串没变时再点 `查找`（或再按回车）就跳到下一处，走到最后一处后再点回到第一处；
  改了输入框里的字再点算新查询，停在第一处。状态栏报 `第 x/N 处，共 N 处`。跳转只展开当前这一处的祖先并滚过去，
  其他分支的展开状态一律不碰。
- 节点数超过 100 000 时状态栏提示改看纯文本（树会很卡）。
- 你手动收起的分支在后续自动重算时**保持不变**（按 key / 数组下标记住展开状态）；
  内容变化后新出现的节点走默认策略：只展开根及其直接子节点。
- `全部收起` 会把整棵树折到**只剩根那一行**（含根自己），回到只想看顶层时点它；`全部展开` 反向。
- **结果树上右键 → `复制`**：把选中节点及其下所有子节点复制成一份完整 JSON。刻意不带该节点自身的
  key、也不带宽松模式展开内层转义 JSON 时那行 `(escaped JSON):` 注释，因此粘出去能直接再解析。
  收起的大节点也一样完整（序列化读原始模型，不经视图层）；树为空或右键在空白处不弹菜单。
- 输入区与结果区之间是可拖动的分割条（与 HTML/XML 面板同一套 `JBSplitter`），高度自己调。

### JSON对比

- 新建的对比标签下方是**空白**（不预置示例内容），状态栏提示“输入 A 和输入 B 都有内容后会自动对比”。
- 输入停止约 **300ms 自动重新对比**，不需要点按钮。
- `仅展示差异`：选中后只保留有差异的分支（强调色 + 加粗 + 描边高亮）；重新渲染会保留你手动展开的节点。
- `对比历史 ▾`：**成对**记录 A、B 两段输入和当时的模式，选中一条即可同时回填两边并恢复现场。上限 30 条。
- 输入区与差异树之间同样是可拖动的分割条。差异树**没有右键复制**（每个节点存的是左右两侧各自的值，
  没有单一“内容”可复制），需要时从两侧输入框自己取。

### URL编码/解码

- **编码**：保留 RFC 3986 未保留字符 `A-Za-z0-9-_.~`，其余按 UTF-8 转大写 `%XX`。
  空格转 `%20`（不是 `+`），语义等同 JavaScript 的 `encodeURIComponent`。
- **解码**：只认 `%XX`，**不把 `+` 当空格**（链接里的加号常常是值本身）；
  遇到不合法的百分号序列原样保留、不报错，所以混排文本也能处理。
- 固定 UTF-8。GBK 编码的老链接（`%D6%D0%CE%C4`）解出来会是乱码。

### HTML格式化 / XML格式化

两者取向不同，是有意为之：

- **HTML 宽松优先**：未闭合标签自动补、多余闭标签原样留一行；属性顺序、引号风格、标签大小写
  一律照搬，不做浏览器式规范化（不补 `tbody`、不补 `head`）；`script`/`style`/`textarea` 内部原样保留。
- **XML 严格**：不是合法 XML 就报错（带行列号）。走 JDK 自带 DOM + identity Transformer，
  命名空间、实体、CDATA 交给平台；开 `FEATURE_SECURE_PROCESSING` 并关闭外部 DTD/实体加载
  （格式化一段贴来的 XML 不会联网），`<?xml ...?>` 声明与 `DOCTYPE` 按原文补回（`pom.xml` 这类带 DOCTYPE 的输入要能格式化）。
- 缩进统一 2 空格，与 JSON 的 pretty 输出一致。

### SQL参数格式化

- **输入两种形态**：整段 GORM 调试日志（`... - [ SQL主体 ] - `参数1`, `参数2``），
  或裸 SQL + 末尾反引号参数行。点 `参数格式化` 把 SQL 里的 `?` 逐个替换为参数，默认原样输出（不重排）。
- **类型智能判定**：双引号包裹的按字符串转单引号（先还原 Go 风格转义 `\"` `\\` `\n` `\t` `\r`，
  内部单引号双写）；纯数字与 `null`/`true`/`false` 原样；GORM 的 `<nil>` 转 `null`；
  其余（如时间戳）兜底加单引号。
- **数量不匹配报错不出结果**；引号串/反引号标识符/注释里的 `?` 不算占位符。
- 裸 SQL 末尾的反引号列表只在“独占一行”或“组数等于占位符数”时才剥下当参数，
  避免把 ``SELECT `a` FROM `t` `` 结尾的反引号标识符误当参数。
- `美化SQL` 对**结果区**做关键字换行 + 2 空格缩进，**不改关键字大小写**、只改空白
  （字符串/注释/token 内容保证不动）；想回到原样版本重点一次 `参数格式化` 即可。
  这是启发式排版器而非完整 SQL 解析器，方言边角可能排得不完美。
- 与 HTML/XML/URL 同样的取舍：按钮触发、无防抖自动重算、不做历史。

## 历史记录

- 存到 IDE 配置目录的 `options/json-formatter-history.xml`，**跨重启保留**，两个独立池：
  单段 JSON（上限 50 条）与对比现场（成对，上限 30 条）。
- 超过 200 000 字符的条目不入池，避免配置目录被撑爆；同内容重复入池只刷新时间不新增。
- HTML/XML/URL/SQL 四个标签**不做历史**。

## 内部标识

- Java 包名与插件 id 均为 `com.tidy.kit`（历史上都叫 `com.you.jsonformatter`），Gradle `group` 同步。
- 插件显示名历史上叫过 `JSON Tree Formatter` / `JSON Formatter` / `Formatter`，现为 **TidyKit**。
- 历史记录存在 `json-formatter-history.xml`（`@State` 名为 `JsonFormatterHistory`），**与包名/插件名解耦**，
  改名不会丢历史。
- 插件级的类已改成中性名：`TidyKitToolWindowFactory`（工具窗口入口）、`TidyKitTabs`（标签容器）、
  `UiColors`（跨功能共用的取色/字体）。仍带 `Json` 前缀的类（`JsonTreePanel`、`JsonDiffPanel`、
  `JsonParseService`、`JsonHistoryStore` 等）确实只处理 JSON，保留原名。
- Gradle 项目名改为 `tidy-kit`，产物 zip 随之叫 `tidy-kit-<版本号>.zip`；工作区目录名（json-tree-formatter）未改。

## 已知限制

- **编辑器内选中格式化没有做**。最早的设计里列了这个入口（`FormatJsonInEditorAction`），
  最终只保留了工具窗口，代码里没有 action 包，`plugin.xml` 里也没有 `<actions>`。
- 树内搜索明确不做（V1 YAGNI）。
- HTML/XML 没有严格/宽松开关（各自的容错策略写死在格式化器里）。
- SQL 参数内容本身含反引号时无法解析（GORM 日志也不转义它）；SQL 美化是启发式排版，
  不保证所有方言的排版都符合人工审美，但保证 token 内容无损。
- XML 的 mixed content（段落里夹标签）空白有语义，不做特判也不重排。
- 无快捷键、无自定义设置页。
- 兼容只设了下限（241）、不设上限，代价是：真遇到 API 不兼容时，会表现成运行期报错
  而不是安装时的“不适配”提示。插件只用 `openapi` / `ui` / `util` 这几块稳定 API，风险低；
  真挂了就回滚上一版 zip。
- 真实运行只在 IDEA 沙盒验证过；GoLand / DataGrip 等属于“元数据允许安装”，
  首次使用建议先拿小文档跑一遍。

## 开发

```powershell
.\gradlew.bat compileJava compileTestJava test   # 纯逻辑层单测,不启 IDE
.\gradlew.bat buildPlugin                         # 出可安装 zip
.\gradlew.bat runIde                              # 沙盒实例(打包前先关掉它,否则 jar 被占用)
.\gradlew.bat verifyPlugin                        # plugin.xml 合规校验
```

结构分层：`model` / `parse` / `compare` / `format` / `codec` / `history` / `sql` 是零 IDE 依赖的纯逻辑层，
可直接单测；`ui` 依赖它们；`toolwindow` 是唯一挂载点。详见 `docs/superpowers/specs/`。
