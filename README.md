# TidyKit

一个自用 IntelliJ IDEA / GoLand 插件：把杂乱的文本收拾干净。一个工具窗口里用标签承载多类小工具，
结果都能一键复制。

- 目标平台：IDEA 2024.1+（IC / IU 均可，`sinceBuild 241`，`untilBuild 251.*`）
- 分发方式：本地打包 + Install Plugin from Disk，**不发布 Marketplace**

## 安装

1. 打包：

   ```powershell
   .\gradlew.bat clean buildPlugin
   ```

   产物在 `build\distributions\json-tree-formatter-0.1.0.zip`（zip 文件名取自 Gradle 项目名，
   不随插件显示名变化）。

2. 在 IDEA 里：`File → Settings → Plugins → ⚙（齿轮）→ Install Plugin from Disk...`，选中那个 zip。
3. 重启 IDEA。
4. 右侧边条出现 **TidyKit** 竖条，点它打开工具窗口（或 `View → Tool Windows → TidyKit`）。

改代码后的日常循环：`buildPlugin` → 重启 IDEA（比每次跑 `runIde` 开两套窗口省事）。
要验证不干净环境下的行为，再用 `.\gradlew.bat runIde` 起独立沙盒。

## 五个入口

顶部一排按钮，每点一次新开一个标签，标签右上角 `×` 关闭（至少保留一个）。

| 按钮 | 标签名 | 干什么 |
|---|---|---|
| JSON格式化 | `JSON格式化 n` | 解析 JSON 渲染成大纲风格树（无连线、纯缩进、清晰折叠三角），可全部展开/收起 |
| JSON对比 | `JSON对比 n` | 左右两段 JSON 按 key 路径做结构化语义比对，合成一棵差异树 |
| URL编码/解码 | `URL n` | 百分号编码与解码，UTF-8 |
| HTML格式化 | `HTML n` | HTML 重排缩进 |
| XML格式化 | `XML n` | XML 重排缩进 |

### JSON格式化

- **模式**：`严格`（标准 JSON）/ `宽松`。宽松会扫描并提取第一个平衡的 `{}`/`[]` 主体、
  容忍单引号、尾逗号、注释，并且会把"顶层是一个被转义的 JSON 字符串"这种现场自动解开内层。
  严格失败时不会自动降级，而是出现`宽松模式重试`按钮。
- 解析成功后自动入历史池，`历史 ▾` 可回填。
- 节点数超过 100 000 时状态栏提示改看纯文本（树会很卡）。

### JSON对比

- 输入停止约 **300ms 自动重新对比**，不需要点按钮。
- `仅展示差异`：选中后只保留有差异的分支（强调色 + 加粗 + 描边高亮）；重新渲染会保留你手动展开的节点。
- `对比历史 ▾`：**成对**记录 A、B 两段输入和当时的模式，选中一条即可同时回填两边并恢复现场。上限 30 条。

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

## 历史记录

- 存到 IDE 配置目录的 `options/json-formatter-history.xml`，**跨重启保留**，两个独立池：
  单段 JSON（上限 50 条）与对比现场（成对，上限 30 条）。
- 超过 200 000 字符的条目不入池，避免配置目录被撑爆；同内容重复入池只刷新时间不新增。
- HTML/XML/URL 三个标签**不做历史**。

## 内部标识（改名不会影响它们）

插件显示名改成 TidyKit（历史上叫过 `JSON Tree Formatter` / `JSON Formatter` / `Formatter`），
以下内部标识始终未变，所以历史记录和启用状态不受影响：插件 id `com.you.jsonformatter`、
包名 `com.you.jsonformatter`、Gradle 构件名、历史文件名。

## 已知限制

- **编辑器内选中格式化没有做**。最早的设计里列了这个入口（`FormatJsonInEditorAction`），
  最终只保留了工具窗口，代码里没有 action 包，`plugin.xml` 里也没有 `<actions>`。
- 树内搜索明确不做（V1 YAGNI）。
- HTML/XML 没有严格/宽松开关（各自的容错策略写死在格式化器里）。
- XML 的 mixed content（段落里夹标签）空白有语义，不做特判也不重排。
- 无快捷键、无自定义设置页。

## 开发

```powershell
.\gradlew.bat compileJava compileTestJava test   # 纯逻辑层单测,不启 IDE
.\gradlew.bat buildPlugin                         # 出可安装 zip
.\gradlew.bat runIde                              # 沙盒实例(打包前先关掉它,否则 jar 被占用)
.\gradlew.bat verifyPlugin                        # plugin.xml 合规校验
```

结构分层：`model` / `parse` / `compare` / `format` / `codec` / `history` 是零 IDE 依赖的纯逻辑层，
可直接单测；`ui` 依赖它们；`toolwindow` 是唯一挂载点。详见 `docs/superpowers/specs/`。
