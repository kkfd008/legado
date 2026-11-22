# 章节目录匹配规则说明

> 本文基于 Legado 源码（`master`，2026-11-22）整理，所有结论均标注了源码文件与行号，便于对照核查。
>
> 项目里存在**两套完全独立**的"章节目录匹配"机制，取决于书籍类型：

| 维度 | 本地 TXT 书籍 | 网络书源书籍 |
| --- | --- | --- |
| 规则本质 | Java 正则表达式 | 自研规则 DSL（5 种模式） |
| 规则存放 | `Book.tocUrl`（正则字符串） | `BookSource.ruleToc`（`TocRule` 对象） |
| 规则编辑器 | TXT 目录规则对话框 | 书源编辑页 → 目录规则 |
| 实现文件 | `model/localBook/TextFile.kt` | `model/webBook/BookChapterList.kt` + `model/analyzeRule/*` |
| 匹配对象 | 文件正文中的章节标题行 | 目录页 HTML / JSON 里的节点 |

---

## 一、本地 TXT：正则切分章节

### 1.1 使用的语言与正则版本

- **实现语言**：Kotlin（Android）。
- **正则引擎**：Android 平台内置的 `java.util.regex.Pattern`，**没有使用任何第三方正则库**，因此不存在独立的"正则库版本"；语法就是 Java 标准正则语法。
- **固定编译标志**：`Pattern.MULTILINE`（等价于内联 `(?m)`），见 `TextFile.kt:96`、`TextFile.kt:448`、`TxtTocRuleEditDialog.kt:82`。因此 `^` / `$` 按行匹配，且**没有开启 DOTALL**——`.` 默认不匹配换行。
- 规则内可自行写内联标志，例如默认规则 `-25` 用了 `(?im)`（`i` = 忽略大小写，`m` = 多行）。

### 1.2 核心函数与文件

| 函数 | 位置 | 职责 |
| --- | --- | --- |
| `TextFile.getChapterList(book)` | `TextFile.kt:41` | 单例入口 |
| `TextFile.getChapterList()` | `TextFile.kt:78` | 决定编码、选规则、编译 Pattern |
| `getTocRule(content)` | `TextFile.kt:442` | **自动挑选最合适的目录规则** |
| `getTocRules()` | `TextFile.kt:473` | 取启用的规则；为空时导入默认规则 |
| `analyze(pattern)` | `TextFile.kt:152` | **按正则分块扫描、生成章节列表** |
| `analyze(fileStart, fileEnd)` | `TextFile.kt:341` | 无规则兜底，按字数硬切 |
| `getContent(chapter)` | `TextFile.kt:111` | 按 `start/end` 字节偏移读取章节正文 |
| `checkValid(tocRule)` | `TxtTocRuleEditDialog.kt:75` | 保存自定义规则时的语法校验 |

规则数据实体：`data/entities/TxtTocRule.kt`（`name` / `rule` / `example` / `enable` / `serialNumber`）。
默认规则 25 条：`app/src/main/assets/defaultData/txtTocRule.json`，另有 `id=-100` 的空规则为"默认分章规则"兜底，不可改动。

### 1.3 匹配逻辑与流程

1. **判定是否需要重新识别**（`TextFile.kt:80`）：`book.charset == null || book.tocUrl.isBlank() || book.isLocalModified()` 时进入识别流程。
2. **读取文件头 512000 字节**（`bufferSize`，`TextFile.kt:60`），用 `EncodingDetect` 探测编码（`:86`）。
3. **自动选规则**（`getTocRule`，`:442`）：
   - 取全部启用规则，`reversed()` 后遍历（`:443`）；
   - 每条规则 `Pattern.compile(rule, Pattern.MULTILINE)`，语法错误写 `AppLog` 并跳过（`:447-451`）；
   - 在样本上 `matcher.find()`，统计"相邻匹配起始位置相距 > 1000 字符"的次数 `num`（`:454-461`），这个距离阈值用于排除正文里的零散误匹配；
   - 取 `num` 最大者（`num >= maxNum`，`maxNum` 初值 1），结果写回 `book.tocUrl`（`:91`）。
4. **编译最终规则**（`:95-100`）：`Pattern.compile(book.tocUrl, Pattern.MULTILINE)`；编译失败写日志并置空，转而走无规则兜底。
5. **分块扫描**（`analyze(pattern)`，`:152`）：
   - 每块 512KB；块尾若正好在缓冲区末端，回退到最近的换行符 `0x0A`（`:177-185`），避免把一行标题切成两半；
   - 处理 UTF-8 BOM（`:166-171`）；
   - `matcher.find()` 逐个命中：`matcher.group()` 作为章节标题，`chapterStart` 与 `seekPos` 之间的内容作为上一章正文，据此计算 `start`/`end` 字节偏移与字数（`:194-303`）；
   - 首块起始处有内容且无章节时，识别为"前言"（`:232-247`）；
   - 命中内容去掉标题后为空白 → 判定为卷标题 `isVolume`（`:257`、`277`）；
   - 开启"拆分超长章节"且单章 > 102400 字节（`maxLengthWithToc`，`:66`）时，递归调用 `analyze(start, end)` 二次切分，标题追加 `(1)(2)`（`:202-219`、`:315-330`）。
6. **无规则兜底**（`:341`）：每 10KB（`maxLengthWithNoToc`，`:63`）按换行切一章，标题格式 `第N章(M)`。
7. **收尾**（`:102-107`）：统计总字数、回写 `index`、生成章节 `url = md5(originName + index + title)`。

### 1.4 自定义 TXT 目录规则注意事项

1. **语法是 Java 正则**：支持 `(?<=...)` / `(?<!...)` 后行断言、`(?:...)` 非捕获组、`(?im)` 内联标志；不支持某些 PCRE/JS 专有语法。
2. **标题必须限制长度**：默认规则普遍用 `.{0,30}$` 收尾，防止把正文整段吞进标题。
3. **行首空白用后行断言排除**：如 `(?<=[　\s])` 让空白不进入 `matcher.group()`，标题才干净。
4. **不要过度宽松**：像 `^\S.{1,20}$`（顶格标题）这类规则误判率很高，默认处于关闭状态；规则太宽还会在自动选规则时胜出，导致整本书切乱。
5. **语法错误是静默的**：`TextFile.kt:97-100` 与 `:447-451` 只写 `AppLog`，UI 不会提示。规则不生效时先查日志。
6. **保存时校验**：`TxtTocRuleEditDialog.kt:81-86` 用 `Pattern.compile(rule, Pattern.MULTILINE)` 验证，语法错误直接拒绝保存。
7. **生效范围要分清**：规则行的开关 = 对所有 TXT 生效；单选按钮 = 只对当前书籍生效，结果存进 `book.tocUrl`（`TocActivity.kt:179`、`ReadBookActivity.kt:1471`）。
8. **改规则后需要重新识别**：`getChapterList()` 只在 `tocUrl` 为空或文件被修改时重跑选规则（`:80`）。
9. **超长章拆分**需同时满足开关 `book.getSplitLongChapter()` 与 102400 字节阈值。
10. 导入默认规则会**重置被改过的内置规则**，但不会删除用户自定义规则（见 `txtTocRuleHelp.md`）。

---

## 二、网络书源：目录规则 DSL

### 2.1 使用的语言与引擎

规则是一种**自研领域特定语言**，由 Kotlin 解析器 `AnalyzeRule` 分派到 5 种模式（`AnalyzeRule.kt:733-735` 的 `Mode` 枚举）：

| Mode | 语言/语法 | 底层库 | 识别前缀或特征 |
| --- | --- | --- | --- |
| `Default` | CSS 选择器 | jsoup `1.16.2` | 默认；`@@`（强制默认）、`@CSS:` |
| `XPath` | XPath | JsoupXpath `2.5.3`（`org.seimicrawler.xpath.JXDocument`） | `@XPath:` 或以 `/`、`//` 开头 |
| `Json` | JSONPath | jayway json-path `2.10.0` | `@Json:` 或以 `$.`、`$[` 开头；内容本身是 JSON 时自动 |
| `Js` | JavaScript | Mozilla Rhino `1.8.1` | `<js>…</js>` 或 `@js:` |
| `Regex` | Java 正则 | `java.util.regex.Pattern` | 列表规则以 `:` 开头，或出现 `##`、`$1`~`$99` |

版本锁定说明（`gradle/libs.versions.toml`）：
- `jsoup = 1.16.2`（`:45-48`，issue #3811，新版有破坏性变更）；
- `rhino = 1.8.1`（`:54-56`，新版用 Android 8 以下缺失的 `VarHandle.compareAndExchange`）；
- `jsonPath = 2.10.0`（`:17`）、`jsoupxpath = 2.5.3`（`:18`）。

**JS 引擎限制**：Rhino 1.8.1 是较老的 ES 实现，不要写箭头函数、`let`/`const`、模板字符串等现代语法，用 `var` + `function`。

### 2.2 规则字段

`data/entities/rule/TocRule.kt:9-19`：

| 字段 | 含义 |
| --- | --- |
| `chapterList` | 章节列表规则（节点列表） |
| `chapterName` | 章节标题 |
| `chapterUrl` | 章节链接 |
| `isVolume` / `isVip` / `isPay` | 卷 / VIP / 已购标记 |
| `updateTime` | 更新时间 |
| `nextTocUrl` | 目录下一页链接 |
| `formatJs` | 标题格式化 JS（Rhino 逐章执行） |
| `preUpdateJs` | 目录请求前执行的 JS |

### 2.3 核心函数与文件

| 函数 | 位置 | 职责 |
| --- | --- | --- |
| `WebBook.getChapterListAwait` | `WebBook.kt:225` | 目录总入口，准备 URL、请求页面 |
| `BookChapterList.analyzeChapterList` | `BookChapterList.kt:35` | 编排：分页、反序、去重、格式化 |
| `BookChapterList.analyzeChapterList`(private) | `BookChapterList.kt:170` | 解析单页目录 |
| `AnalyzeRule.getElements` | `AnalyzeRule.kt:367` | 按 `chapterList` 取章节节点列表 |
| `AnalyzeRule.getString` / `getStringList` | `AnalyzeRule.kt:250` / `:163` | 按字段规则抽文本 / URL |
| `AnalyzeRule.splitSourceRule` | `AnalyzeRule.kt:485` | 拆 `<js>` / `@js:` 片段，生成 `SourceRule` |
| `AnalyzeRule.SourceRule` | `AnalyzeRule.kt:530` | 识别前缀、拆分 `##`、`$n`、`{{}}`、`@get:` |
| `AnalyzeRule.replaceRegex` | `AnalyzeRule.kt:436` | 处理 `##正则##替换##` |
| `AnalyzeRule.evalJS` | `AnalyzeRule.kt:774` | Rhino 执行 JS，注入 `result` / `book` / `chapter` 等变量 |
| `RuleAnalyzer.splitRule` | `RuleAnalyzer.kt:165` | 拆分 `&&` / `||` / `%%` 组合 |
| `AnalyzeByRegex.getElements` | `AnalyzeByRegex.kt:32` | 正则模式提取列表（多级 `&&`） |

### 2.4 匹配逻辑与流程

1. **准备目录页**（`WebBook.kt:225-266`）：执行 `preUpdateJs`，用 `AnalyzeUrl` 请求 `book.tocUrl`；若命中缓存（`book.tocHtml`）则直接用缓存正文。
2. **识别顺序标记**（`BookChapterList.kt:51-58`）：`chapterList` 以 `-` 开头 → 先反序并去掉该字符；以 `+` 开头 → 只去掉字符。
3. **解析首页**（`:59-64`）：`AnalyzeRule.getElements(listRule)` 得到章节节点；同时用 `nextTocUrl` 取下一页链接（`:192-207`，`isUrl = true` 会自动转绝对 URL 并去重）。
4. **翻页**（`:65-115`）：
   - 0 个下一页：完成；
   - 1 个：串行 `while` 循环，用 `nextUrlList.contains` 防止死循环；
   - ≥2 个：`flow + mapAsync(AppConfig.threadCount)` 并发解析，再统一汇总。
5. **逐节点抽字段**（`:211-255`）：`splitSourceRule` 预编译各字段规则，然后对每个节点 `setContent(item)` + `setChapter(...)`，依次 `getString` 出标题、链接、更新时间、卷、VIP、已购。
   - 链接为空：卷用 `标题 + index` 兜底，普通章节用 `baseUrl`（`:230-244`）；
   - 标题为空的条目直接丢弃（`:245`）。
6. **后处理**（`:116-167`）：
   - 若未标记 `-`，先 `reverse()`（因为多数站点倒序输出）；
   - `LinkedHashSet` 去重（`:124`）；
   - 若书籍未设置倒序阅读 `book.getReverseToc()`，再次 `reverse()`；
   - 重排 `index`；用 Rhino 执行 `formatJs` 格式化标题（`:134-152`）；
   - 应用标题替换规则，更新 `durChapterTitle` / `latestChapterTitle` / `totalChapterNum`；可选继承旧缓存字数（`:272-286`）。
   - 空结果抛 `TocEmptyException`。

### 2.5 自定义书源目录规则注意事项

1. **前缀区分大小写不统一**：`@CSS:` / `@XPath:` / `@Json:` 用 `startsWith(..., true)` 忽略大小写（`AnalyzeRule.kt:548-566`）；但 `@@` 必须精确匹配两个 `@`。
2. **`@@` 用于强制默认模式**：当规则里含 `//`、`$.` 等会被误判为 XPath/JSON 的字符时，用 `@@` 前缀强制走 CSS 选择器。
3. **`<js>` 非贪婪，`@js:` 贪婪**：`AppPattern.JS_PATTERN` 为 `<js>([\w\W]*?)</js>|@js:([\w\W]*)`（`AppPattern.kt:8`），`@js:` 会吞掉后面全部内容，因此 `@js:` 只能放在规则末尾。
4. **`##` 是三段分隔符**：`规则##替换正则##替换文本`，若还有第 4 段（任意内容）则只替换第一个匹配（`AnalyzeRule.kt:708-718`、`:436-460`）。正则里若要用字面量 `##` 会冲突，需改写。
5. **`$1`~`$99` 是捕获组引用**：`regexPattern = \$\d{1,2}`（`AnalyzeRule.kt:883`），在 `makeUpRule` 里按顺序替换；超出捕获组数量时保留原样。
6. **`{{...}}` 内嵌 JS**：`{{js表达式}}` 会在取规则时求值；若内容以 `@`、`$.`、`$[`、`//` 开头则被视为规则递归解析（`isRule`，`:721-726`）。
7. **变量规则**：`@get:{key}` 读取变量，`@put:{"k":"v"}` 写入变量（`:399-431`、`:698-700`）。注意 `bookName` 与 `title` 是保留键，会被覆盖并给出日志警告（`:740-743`）。
8. **组合符 `&&` / `||` / `%%`**：由 `RuleAnalyzer.splitRule` 切分（`RuleAnalyzer.kt:165`），语义分别是"全部串联"、"取第一个非空结果"、"按索引交叉合并"。切分器会跳过 `[...]` / `(...)` 和引号内的分隔符；**括号不平衡会抛"后未平衡"错误**（`:228-230`）。
9. **JS 规则的 HTML 转义**：`getString` 默认对结果做 `StringEscapeUtils.unescapeHtml4`（`:314-318`）；链接模式 `isUrl = true` 会跳过该步并转绝对 URL。
10. **JS 执行环境**：`evalJS` 注入 `result` / `java` / `book` / `chapter` / `title` / `src` / `baseUrl` / `source` / `cookie` / `cache` 等（`:774-788`）；脚本与正则有 16 条 LRU 缓存（`:462`、`:806`）。
11. **调试手段**：开启书源调试后，日志会打印"获取目录列表 → 列表大小 → 首章信息 → 目录总页数 → 目录总数"，规则不生效时按这几步定位。

---

## 三、通用排错清单

| 现象 | 排查方向 |
| --- | --- |
| TXT 章节切不出来 | 查 `AppLog` 是否有"TXT目录规则正则语法错误"；确认规则开关与当前书籍选中项 |
| TXT 把正文切成章节 | 规则太宽，检查是否缺少 `.{0,30}$` 长度上限或行首断言 |
| 书源目录为空 | 看调试日志的"列表大小"：`0` 说明 `chapterList` 规则没匹配到节点 |
| 章节标题带 HTML 实体 | `getString` 默认反转义；若被 `##` 替换规则破坏，检查替换段 |
| 章节顺序颠倒 | `chapterList` 的 `-` / `+` 前缀，以及书籍"倒序阅读"开关 |
| 目录重复章节 | 已在 `:124` 用 `LinkedHashSet` 去重；重复通常来自规则生成了不同 URL 的相同标题 |
| JS 规则报语法错 | Rhino 1.8.1，改用 `var` / `function`，不用 ES6+ 语法 |

## 四、代码索引

- `app/src/main/java/io/legado/app/model/localBook/TextFile.kt`
- `app/src/main/java/io/legado/app/ui/book/toc/rule/TxtTocRuleDialog.kt`
- `app/src/main/java/io/legado/app/ui/book/toc/rule/TxtTocRuleEditDialog.kt`
- `app/src/main/assets/defaultData/txtTocRule.json`
- `app/src/main/assets/web/help/md/txtTocRuleHelp.md`
- `app/src/main/java/io/legado/app/model/webBook/BookChapterList.kt`
- `app/src/main/java/io/legado/app/model/webBook/WebBook.kt`
- `app/src/main/java/io/legado/app/model/analyzeRule/{AnalyzeRule,RuleAnalyzer,AnalyzeByRegex,AnalyzeByJSoup,AnalyzeByXPath,AnalyzeByJSonPath}.kt`
- `app/src/main/java/io/legado/app/constant/AppPattern.kt`
- `app/src/main/java/io/legado/app/data/entities/rule/TocRule.kt`
- `gradle/libs.versions.toml`
