# SQL 参数格式化设计（日志占位符回填 + 轻量美化）

日期:2026-11-11
状态:已与需求方逐节确认
关联:扩展 `2026-10-30-json-tree-formatter-plugin-design.md`(工具窗口多标签容器)

## 1. 背景与目标

排查接口问题时经常从 GORM 调试日志里拷出一段带 `?` 占位符的 SQL 和末尾的参数列表,
手工把参数逐个塞回占位符既慢又容易错(字符串要补单引号、顺序不能乱)。
目标:新增第六个功能按钮「SQL参数格式化」,粘贴整段日志(或裸 SQL + 参数行),
一键把 `?` 逐个替换成参数,得到能直接执行的 SQL;另提供「美化SQL」做换行缩进。
仅个人使用,不发布 Marketplace。

## 2. 需求决议

- 输入形态:**完整 GORM 日志行**与**裸 SQL + 末尾参数行**两种都支持(需求方选定)
- 类型判定:智能判定——双引号包裹的是字符串转单引号;纯数字与 null/true/false 原样;
  其余未加引号内容(如时间戳)按字符串加单引号(需求方选定)
- 占位符与参数数量不匹配:**报错不出结果**,与 XML 严格取向一致(需求方选定)
- 输出:默认原样(只换参数,不重排);另设「美化SQL」按钮做换行缩进,**不改关键字大小写**;
  另有「复制结果」按钮(需求方指定)
- 不做历史池、不做防抖自动重算(与 HTML/XML/URL 一致:整段粘贴、按钮触发)
- 技术栈与分层:沿用纯 Java 逻辑层可脱离 IDE 单测的约定,零第三方依赖

## 3. 方案选型

采用方案 A:新增 `sql` 纯逻辑包 + 专用 `SqlParamPanel`。
- 否决方案 B(塞进 `format` 包 + 扩展 `TextFormatPanel`):`TextFormatter.format(String)`
  单方法接口装不下"替换 + 美化"两个动作,共用面板要长出 SQL 专属分支,波及已稳定的 HTML/XML
- 否决方案 C(引第三方 SQL 格式化库):违背本工程零第三方依赖、自写解析器的既定约定
  (JSON/HTML 解析器都是手写的),且现成库对 MySQL 方言(`->>`、`ifnull`)未必友好
- 面板布局与 `TextFormatPanel` 有约百行相似,宁可少量重复也不把共用面板改出分支逻辑
  (本工程一贯取向)

## 4. 架构

    src/main/java/com/tidy/kit/
    |- sql/        SqlLogParser / SqlParameterBinder / SqlBeautifier / SqlScan / SqlException
    |- ui/         SqlParamPanel / TidyKitTabs(注册第六个按钮)

`sql` 包与 `parse`/`compare`/`codec`/`format` 同级:不 import Swing 与 com.intellij,
可脱离 IDE 单测。数据流:

    粘贴日志 -> SqlLogParser 提取(SQL 主体, 原始参数列表)
             -> SqlParameterBinder 替换 -> 结果区(原样 SQL)
             -> [美化SQL] -> SqlBeautifier -> 结果区(排版后)

`SqlException` 为受检异常,风格同 `FormatterException`(消息可带行列号;
本功能的错误主要是语义性的,通常只用消息构造器)。

## 5. SqlLogParser(输入提取)

入口:`parse(String input) -> ParsedSql`,其中 `record ParsedSql(String sql, List<String> rawParams)`;
`rawParams` 是反引号内的原始内容(不含反引号本身)。失败抛 `SqlException`。

### 5.1 日志形态(优先识别)

样例结构:`时间戳 [DEBUG] 文件:行 [DAO] - [OK / db.Select / 13.0ms] - [ SQL主体 ] - 参数列表`

- 用输入中**最后一个** `] - ` 切分:右边是参数区;左边区域里取**最后一个** `- [` 之后、
  切分点之前的文本为 SQL 主体(trim)。从后往前找是因为 SQL 里可能含 `]`(如 JSON 路径)。
  时间戳段(`[OK / db.Select / 13.0ms] - `)也含 `] - `,所以还要求**参数区以反引号开头**
  才确认是带参日志;不满足则落到无参数日志/裸 SQL 分支
- 无 `] - ` 但含 `- [` 且 trim 后以 `]` 结尾 → 视为无参数日志,SQL 取 `- [` 之后到结尾 `]` 之前
- 两种都识别不出 → 按裸 SQL 形态处理(5.2)

### 5.2 裸 SQL 形态

末尾的反引号列表**只有在满足其一**时才剥下来当参数:
1. 列表独占一行(第一个反引号前有换行);
2. 反引号组数恰好等于剩余 SQL 中占位符个数(占位符统计口径同第 6 节,引号/注释内的不算)。

否则整段输入当 SQL、零参数。这是为了避免把 ``SELECT `a` FROM `t` `` 结尾的反引号标识符误当参数。

### 5.3 参数列表提取

参数区按反引号分组:`` `1`, `202610`, `"0701"` `` → `["1", "202610", "\"0701\""]`。
组与组之间只允许逗号与空白,出现其他内容 → 抛错"无法解析参数列表"。
空参数区(日志无参数)→ 空列表。

已知限制:参数内容本身含反引号时无法解析(GORM 日志也不转义它),不做支持。

## 6. SqlParameterBinder(占位符回填)

入口:`bind(String sql, List<String> rawParams) -> String`,失败抛 `SqlException`。

### 6.1 占位符扫描

逐字符扫 SQL,跟踪以下区域,**区域内的 `?` 不算占位符**:
- 单引号串(处理 `''` 双写转义与反斜杠转义)
- 双引号串、反引号标识符
- `--` 与 `#` 行注释、`/* */` 块注释

扫描实现收在包私有 `SqlScan`(segments/countPlaceholders/literalEnd),
5.2 的占位符统计与美化器的 token 切分共用同一口径,避免规则漂移;
未闭合的引号/块注释视为延伸到输入末尾,不抛错(严格性由数量校验兜底)。

样例 2 的 `'$.targetVer'`、`'null'`、`''` 等字面量不受影响。

### 6.2 数量校验

占位符数 ≠ 参数数 → 抛错,消息带两边数量,如"SQL 有 7 个占位符,但参数有 5 个"。
无占位符也无参数是合法场景,原样返回 SQL。

### 6.3 类型渲染(按序判定)

| 原始参数 | 渲染 |
| --- | --- |
| 双引号包裹(`"0701"`) | 去双引号、解 Go 风格转义(`\"` `\\` `\n` `\t` `\r`)、内部 `'` 转 `''`,包单引号 → `'0701'` |
| 纯数字(含负数/小数/科学计数,正则 `[+-]?(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?`) | 原样 |
| `null` / `true` / `false`(忽略大小写) | 原样 |
| `<nil>`(GORM 的 nil 输出) | `null` |
| 其余(如时间戳 `2026-11-11 15:08:37`) | 按字符串:`'` 转 `''` 后包单引号 |
| 空内容 | `''` |

样例 1 预期:`root_id = 1 AND ver_no = 202610 ... and code = '0701'`;
样例 2 的 `"RC00005"` → `'RC00005'`。

## 7. SqlBeautifier(轻量美化)

入口:`beautify(String sql) -> String`,**永不抛错**(未知 token 原样通过)。

自写轻量 tokenizer,切成:单词(标识符/关键字)、引号串(`'…'`/`"…"`/`` `…` ``)、数字、
符号(识别 `->>` `->` `<=` `>=` `<>` `!=` `||` 等多字符操作符)、注释、括号、逗号。
**只改空白,绝不改 token 内容**:关键字大小写照搬、字符串/注释原样。

排版规则(缩进 2 空格,与全工程一致):

| token | 行为 |
| --- | --- |
| 子句关键字 SELECT / FROM / WHERE / GROUP BY / HAVING / ORDER BY / LIMIT / UNION [ALL] / WITH / INSERT INTO / VALUES / UPDATE / SET / DELETE FROM | 换行,顶当前括号层级 |
| JOIN 家族(含 LEFT / RIGHT / INNER / CROSS JOIN) | 换行,同层级 |
| ON | 换行,比 JOIN 深一级 |
| AND / OR | 换行,比所属子句深一级 |
| SELECT 列表里的顶层逗号 | 换行,比 SELECT 深一级;函数参数、IN 列表里的逗号不动 |
| 子查询括号(`(` 后第一个有效 token 是 SELECT / WITH) | `(` 后换行、内层缩进 +1,`)` 独占一行回外层;函数调用与 IN 列表括号保持内联 |
| CASE / WHEN / ELSE / END | CASE 后层级 +1;WHEN / ELSE 换行深一级;THEN 内联;END 换行回 CASE 层级 |
| 行注释(`--` / `#`) | 独占一行;块注释原样 |
| 连续空白 | 折叠成一个空格 |

**硬判据**:美化前后"去掉引号串/注释之外的所有空白"得到的字符序列必须完全一致,
保证永不改坏内容。这是启发式排版器而非完整 SQL 解析器:方言边角可能排得不完美,但内容无损。

## 8. UI 层

### 8.1 SqlParamPanel

布局骨架照 `TextFormatPanel`:`JBSplitter(true, 0.4f)` 上输入下只读结果(各带 TitledBorder)、
编辑器字体(`UiColors.editorFont()`)、两区强制 `setLineWrap(true) + setWrapStyleWord(true)`
(不换行时拖选会膨胀到行尾,本工程踩过的坑)、底部状态栏走 `UiFeedback`。

- 控制行:`参数格式化`(解析 + 替换,输出原样 SQL)、`美化SQL`、`清空`;右下角 `复制结果`
- `美化SQL` 作用于**结果区**当前内容;结果为空时提示"先点\"参数格式化\"得到结果"。
  想回到原样版本,重点一次 `参数格式化` 即可(从输入重新解析)
- 状态栏:成功 `已替换 N 个参数` / `已美化,共 N 行`;失败红字带 `SqlException` 消息
- 空输入显示引导文案"粘贴 SQL 日志(或 SQL + 参数行)后点\"参数格式化\"",不算错误

### 8.2 TidyKitTabs 注册

- 顶部第六个按钮 `SQL参数格式化`(排在 XML格式化 之后),tooltip"新建一个 SQL 参数格式化标签"
- `sqlCounter` 计数,标签名取短式 `SQL n`(全称会把标签头撑挤,与 URL/HTML/XML 同理)
- 走公共 `addTab(panel, title, focus)`,自动获得关闭/拖拽换序/整块热区等既有能力
- 类注释"五个按钮"改"六个"

## 9. 错误处理

原则:解析/绑定失败一律报错不出结果(与 XML 严格取向一致);美化器永不失败。

| 情况 | 行为 |
| --- | --- |
| 空输入 | 引导文案,不算错误 |
| 找不到 SQL 主体 / 参数列表畸形 | 红字报错,结果区清空 |
| 占位符与参数数量不匹配 | 红字带两边数量,结果区清空 |
| 无占位符也无参数 | 原样输出 SQL(合法场景) |
| 结果为空时点美化/复制 | 提示先做参数格式化 |

## 10. 测试

纯 JUnit 单测(headless,不启 IDE),沿用工程分层测试约定:

- `SqlLogParserTest`:附录两个样例的日志提取、裸 SQL 参数行(独占一行)、
  组数等于占位符数的同行情形、``SELECT `a` FROM `t` `` 不误剥、无参数日志、
  空参数区、畸形参数列表报错
- `SqlParameterBinderTest`:类型渲染表逐行正例、单引号转义(`O'Brien` → `'O''Brien'`)、
  字面量/注释里的 `?` 不替换、数量不匹配双向报错、`<nil>` → null、空串参数、
  双引号内 Go 转义还原
- `SqlBeautifierTest`:附录样例的黄金输出、**token 无损硬判据**(去空白后序列一致)、
  字符串与关键字大小写不动、子查询缩进、CASE/WHEN/END 排版、IN 列表逗号不换行

面板交互按工程惯例 `runIde` 沙盒手工验收(合成事件不沿 Swing dispatcher 链冒泡,进程内测不了):

- 粘贴样例 1/2 整段日志 → 点参数格式化 → 占位符全部回填、字符串带单引号
- 点美化SQL → 关键字换行缩进、`->>` 与字符串原样;再点参数格式化回到原样版本
- 数量不匹配(删掉一个参数)→ 红字报错、结果区空
- 复制结果 → 粘进数据库客户端可直接执行
- 新标签关闭/拖拽换序后功能正常

## 11. 已知取舍

- 参数内容含反引号无法解析(GORM 日志本身不转义)
- 裸 SQL 的参数剥离是启发式(独占一行或组数匹配),极端歧义输入可能误判;日志形态不受影响
- 美化器是启发式排版,不保证所有方言排得完美,但保证 token 无损
- 不做历史池、不做防抖自动重算、不做"结果转输入"、无快捷键
- 类型判定面向 GORM 日志的参数打印风格(字符串带双引号);其他框架日志若裸打印字符串,
  会被当未加引号内容兜底加单引号,数字形态的字符串(如裸 `0701`)会被当数字——不做支持

## 12. 文档同步

- `README.md`:五个入口改六个,新增「SQL参数格式化」小节
- `CHANGELOG.md`:新增功能条目
- `plugin.xml`:`<description>` 补 SQL 能力
- 主设计文档:标签容器一节指向本文档

## 附录:验收样例

样例 1(3 个占位符,参数 `` `1`, `202610`, `"0701"` ``):

    2026-11-11 15:08:37.588	[DEBUG]	dao/debug_sql.go:53	[DAO] - [OK /  db.Select /    13.0ms] - [
    			SELECT
    				id, root_id, type_id, ver_no, name, code, should_issue, subj_type, calc_subj_type, weight_scheme_id, num_listed, ord_no, remark,heat_ranking , heat_ranking_num
    			FROM
    				ranking_instance
    			WHERE
    				root_id = ?
    				AND ver_no = ?
    				AND deleted_at IS NULL
    		 and code = ? ] - `1`, `202610`, `"0701"`

预期替换结果(节选):`root_id = 1`、`ver_no = 202610`、`code = '0701'`。

样例 2(7 个占位符,参数 `` `3`, `4`, `1`, `141`, `95`, `97`, `"RC00005"` ``):

    2026-11-11 15:08:37.616	[DEBUG]	dao/debug_sql.go:53	[DAO] - [OK /  db.Select /    19.7ms] - [
    		WITH i AS (
    			SELECT
    				id,
    				detail ->> '$.targetVer' target_ver
    			FROM indicator_latest
    			WHERE level = ? AND deleted_at IS NULL
    			UNION ALL
    			SELECT i1.id,
    					CASE WHEN i1.detail ->> '$.targetVer' = 'null' || i1.detail ->> '$.targetVer' = '' || i1.detail ->> '$.targetVer' IS NULL
    						THEN  i2.detail ->> '$.targetVer'
    						ELSE i1.detail ->> '$.targetVer'
    					END target_ver
    			FROM indicator_latest i1
    			LEFT JOIN indicator_latest i2 ON i1.pid = i2.id
    			WHERE i1.level = ? AND i1.deleted_at IS NULL
    		)

    			SELECT sc.id, sc.r_root_id, sc.r_leaf_id, sc.ind_id,
    				   sc.ind_code, sc.univ_code, sc.val_ranking as ranking, sc.val_rank_rng as rank_rng, sc.is_adc,
    				   sc.val, sc.var_details, sc.var_dtl_len,
    					ifnull(json_length(sc.var_details) > 0,false) as has_data,
    					rt.code as subj_code, rt.name as subj_name
    			FROM ind_value_latest sc
    			JOIN i ON sc.ind_id = i.id AND sc.target_ver = i.target_ver
    			LEFT JOIN ranking_type rt ON sc.r_leaf_id = rt.id
    			WHERE sc.r_root_id = ? AND sc.r_leaf_id = ?
    			AND sc.ind_id IN (?, ?) AND sc.univ_code IN (?)
    			AND sc.deleted_at IS NULL
    		] - `3`, `4`, `1`, `141`, `95`, `97`, `"RC00005"`

预期替换结果(节选):`level = 3`、`i1.level = 4`、`sc.r_root_id = 1`、`sc.r_leaf_id = 141`、
`IN (95, 97)`、`IN ('RC00005')`;`'$.targetVer'` 等字面量原样。