# AI 交接文档（会话记忆压缩）

> 用途：供后续 AI 快速接续工作。本文档记录项目现状、已完成工作、关键技术细节与构建方式。
> 最近更新：2026-11-21（同步标签分组 / 流式布局 / v81 数据库 / 本地书籍编辑到当前代码）

## 1. 项目概览

- 项目：Legado（开源 Android 电子书阅读器，Kotlin + MVVM + Room）
- 仓库：`https://github.com/kkfd008/legado.git`（分支 `master`）
- 本地路径：`D:/liuwen/dev/project/legado`（另有 worktree：`C:/Users/Administrator/WorkBuddy/Worktrees/LegadoPlus/main-f251f0f2`）
- 数据库：Room，版本 81，24 表 + 1 视图（`book_sources_part`）
- 文档：`PRD.md`（产品需求/功能，v1.6）、`TECH_DOC.md`（技术实现，v1.4）、`DB_DOC.md`（v81）、`api.md`、`English.md`

## 2. 构建环境

### 2.1 版本约束（Windows / Linux 一致）

| 项 | 要求 |
|----|------|
| JDK | 17（`jvmToolchain 17`） |
| Gradle | 8.13（`mise.toml` + wrapper 双重固定） |
| AGP / Kotlin | 8.13.2 / 2.3.0 |
| compileSdk / minSdk | 36 / 21 |
| SDK Platform / Build-Tools | android-36 / 36.0.0 |
| 签名 | `app/debug.keystore` 指纹必须与官方一致，否则无法覆盖安装 |

### 2.2 脚本（根目录三件套）

```bash
check.bat            # 只读环境检测，退出码=未通过项数量
deploy.bat --yes     # 只装缺失项：mise / JDK17 / Gradle8.13 / cmdline-tools / Platform36 / Build-Tools36 / licenses / local.properties
build.bat            # 默认 :app:assembleAppDebug
build.bat :app:assembleAppRelease
build.bat clean :app:assembleAppDebug
build.bat --wrapper  # 改用仓库 gradlew（默认走 mise 的 gradle）
build.bat --open-dir # 编译成功后打开 APK 所在目录并选中 APK
```

Linux 云端：`scripts/check_build_env_linux.sh`、`scripts/build_linux.sh`（公共函数在 `scripts/env_common.sh`）。

> `.bat` 首部必须保留 `chcp 65001` + `goto :legado_main`：cmd 在文件内切换代码页后会重新扫描文件，去掉 goto 会导致中文注释被截断执行。

### 2.3 APK 产物

```
app/build/outputs/apk/app/debug/legado_app_<versionName>.apk
```

`versionCode = 10000 + git 提交数`（见 `app/build.gradle`）。`build.bat` 编译成功后会打印 `file:///D:/dev/project/legado/app/build/outputs/apk/app/debug/<apk名>` 形式的可点击地址；加 `--open-dir` 可直接打开所在目录并选中 APK。

### 2.4 Git

- 身份：`kkfd008 <kkfd008@github.com>`
- 流程约定：功能分支完成后先合并回 `master`，再在 `master` 上编译 debug APK 验证后汇报
- `master` 可能被另一个 worktree 检出，`git push . <branch>:master` 会被拒绝（refusing to update checked out branch），需在 master 所在 worktree 里 merge

## 3. 已完成的功能（累计）

1. 文档体系：`PRD.md`、`DB_DOC.md`、`TECH_DOC.md`，entities/dao/model/service 中文注释。
2. 在线服务新增「TXT 目录规则」模块：
   - 后端 `app/src/main/java/io/legado/app/api/controller/TxtTocRuleController.kt`
   - 路由 `app/src/main/java/io/legado/app/web/HttpServer.kt`
   - 前端 `app/src/main/assets/web/txtTocRule/index.html`
3. 书架优化：右上角菜单现名「本地同步」（原「更新本地书籍」）；本地页底部按钮（删除/放入书架/放入标签/放入分组 + 删除确认）。
4. 修复「本地同步」后已移动目录的本地书未从书架移除（`LocalBook.getLastModified` 增加 `content://` 校验；`MainViewModel.upLocalBook` 改遍历 `bookDao.all`）。
5. 「显示书源」开关（默认关闭）：隐藏我的页面「书源管理」、书架菜单「添加网址」。
6. 修复阅读页内容关键字搜索缓存 bug（返回结果列表变空白）。
7. 「WebDAV」总开关（默认关闭）：隐藏「备份与恢复」「添加远程书籍」，并短路阅读进度同步。
8. 标签分组：新增 `tag_group` 表与分组管理页，标签按分组次序展示、未分组置底；管理/选择/配置三页统一「分组标题 + 组内标签」分区展示；删除标签同步清除书籍标签位，并提供「清理标签」菜单。
9. 搜索页输入帮助面板：搜索历史 + 「热门标签」标题 + 流式换行标签列表；回车在关闭书源时保留面板。
10. 构建脚本统一为 `check.bat` / `deploy.bat` / `build.bat` + `scripts/*.sh`，mise 统一管理 JDK 17 / Gradle 8.13，SDK 固定 36。
11. 目录页新增「重载书籍目录」（位于「TXT 目录规则」下方），本地重解析 / 网络重请求书源目录。
12. 阅读菜单新增「编辑本地书籍」（本地 TXT 且可写时显示）：编辑框展示当前页对应的文件原文片段，保存时只替换该片段并重建目录；定位失败自动退化为整章编辑。
13. 标签管理/选择页取消固定每行数量，改为按文字宽度自动换行的 Flexbox 流式布局；「标签管理每行标签数」设置项已删除。

## 4. 最近几项功能的关键实现

### 4.1 功能开关（能力级门控）

| 开关 | PreferKey | 默认 | 门控 |
|------|-----------|------|------|
| 显示书源 | `showBookSource` | false | `MyFragment`「书源管理」、`BaseBookshelfFragment`「添加网址」、搜索页回车是否收起输入帮助 |
| WebDAV | `webDavEnabled` | false | `MyFragment`「备份与恢复」(`web_dav_setting`)、`BaseBookshelfFragment`「添加远程书籍」(`menu_remote`)、`AppConfig.syncBookProgress` / `syncBookProgressPlus` |

已移除：`tagManageSpanCount`（原「标签管理每行标签数」），标签页现使用 `FlexboxLayoutManager` 自动换行。

- 入口显隐在 `onCreatePreferences` + `onResume` 里刷 `isVisible`，无需 EventBus。
- 新增此类开关：`PreferKey` → `AppConfig` getter（必要时在派生属性上 `&&`）→ `pref_*.xml` 控件 → Fragment 里设置可见性。

### 4.2 标签分组、流式布局与倒序排序

- 数据：`BookTag.groupId`（v81，默认 0 = 未分组）+ `BookTagGroup`（表 `tag_group`，groupId 普通递增，0 保留给未分组）；`BookTagGroupDao.deleteAndClearTags()` 删分组时把组内标签 groupId 置 0。备份导出/恢复包含 `tagGroup.json`。
- 三个页面（管理 / 选择 / 配置）共用 `TagListItem`（`Group` / `Tag`）与 `TagSort.toTagGroupItems()` 生成「分组标题 + 组内标签」列表；分组按 order 倒序、组内标签按 order 倒序、未分组置底，空分组不显示标题。配置页的分组标题独占整行，选择页的分组标题是行首加粗灰底块、与首个标签同一行；搜索页热门标签只用 `sortByTagGroup()` 排序，不显示标题。
- 布局：`TagManageDialog` / `TagSelectDialog` 用 `FlexboxLayoutManager`（ROW + WRAP），按文字宽度自动换行，无固定列数；`TagConfigActivity` 保持单列列表。`ItemTouchCallback` 的 `canDrag` 过滤 Group 行，`dragFlagsForOtherLayout` 让 Flexbox 下拖拽生效；分组标题不可拖、标签不可跨组。
- 排序约定：按 `order` **倒序**展示，写回用 `order = 总数 - 序号`（`size - index`）并 `copy()` 生成新对象，避免污染 DiffUtil 旧列表；配置页拖拽期间用 `adapter.isDragging` 跳过 Flow 刷新。
- 选择页样式：`item_tag_select.xml` 只有 `tv_group_title` 与 `cb_tag`，14sp；分组名灰底圆角不带冒号，标签选中为 `md_red_500` 红字 + 灰底圆角；`layout_margin=2dp`、上下 `padding=2dp`、左右 `padding=3dp`。编辑标签请到管理/配置页。
- 样式设置入口已迁到「标签配置 → 选择标签样式」菜单：`TagConfigActivity` 菜单 id `menu_tag_select_style` → `ConfigActivity`（`ConfigTag.TAG_SELECT_STYLE_CONFIG`）→ `TagSelectStyleFragment` + `pref_tag_select_style.xml`；「其它设置」里不再有该分类。
- 共 13 项可覆盖上述静态值（仅作用于 `TagSelectDialog`）：`showTagGroupTitle`（默认 true，关闭时过滤 Group 项）、`tagGroupTextSize` / `tagTextSize`（14sp，8–30sp）、`tagGroupBackgroundColor` / `tagSelectedBackgroundColor`（默认跟随主题 `background_menu`）、`tagNameBackgroundColor`（默认透明）、`tagBoxPaddingHorizontal`（3dp）/ `tagBoxPaddingVertical`（2dp）（0–20dp）、`tagBoxCornerRadius`（12dp，0–30dp）、`tagBoxBorder`（0dp，0–10dp）、`tagBoxMargin`（2dp，0–20dp）、`tagDividerMarginTop` / `tagDividerMarginBottom`（0dp，0–30dp）。`TagSelectDialog.textBoxBackground()` 动态生成 `GradientDrawable` 应用圆角/边框/底色（`bg_divider_line` 描边），点击切换选中时同步换背景；分组项与标签项 margin 保持一致，分割线只用上下 margin 在两行空隙里定位（超出空隙时贴分组标题），避免同行分组名与标签名上下错位；注意 `setPadding` 顺序是 `(左右, 上下, 左右, 上下)`。
- 标签配置页工具栏：`menu_clear_invalid`（清理标签）与 `menu_tag_select_style` 都在三点菜单；`menu_add_group` 是加号左侧的带圈加号（`ic_add_circle`），点击弹 `TagGroupEditDialog()` 新增分组。分组标题行右侧的 `tv_group_edit` / `tv_group_del` 只在 `groupId != 0L` 时显示；编辑/删除先 IO 取 `tagGroupDao.getById()` 完整实体再回主线程，避免丢失 `order`。
- 删除标签：`BookTagDao.deleteAndClearBooks()` 在同一事务里 `update books set tags = tags - :tagId where tags & :tagId > 0`，避免复用 tagId 时旧书「继承」标签；「清理标签」菜单用 `clearInvalidTags()` 清理残留位。
- TXT 目录规则列表页显示每条规则匹配的书籍数：`combine(observeAll(), bookDao.flowAll())` 按本地书 `book.tocUrl == rule.rule` 统计；数量不进 Room 实体，用 `TxtTocRuleAdapter` 内的 `Map<ruleId, count>` + `upCount` payload 局部刷新。

### 4.3 搜索页输入帮助面板

- 布局 `activity_book_search.xml`：`ll_input_help` 内自上而下 `tv_tag_title` + 搜索历史 `rv_history_key` + `rv_tag_suggest`。
- 热门标签数据：`bookTagDao.flowHot()`（`book_tags.hot = 1`，按 order 倒序；默认非热门，不再按排序取前 10），`FlexboxLayoutManager` 流式换行，点击以 `#标签名` 搜索；热门开关在「我的 - 标签配置」页设置。
- `onQueryTextSubmit` 中 `if (AppConfig.showBookSource) visibleInputHelp(false)`，关闭书源时保留面板避免空白结果页。

### 4.4 阅读页关键字搜索缓存

- 根因：`SearchContentActivity.openSearchResult` 用非粘性 `postEvent` 回填，`ReadBookActivity` 处于 STOPPED 收不到，导致 `ReadBookViewModel.searchResultList` 一直为 null。
- 修复：`ReadBookActivity.searchContentActivity` 回调里补 `viewModel.searchResultList = searchResultList`。

### 4.5 编辑本地书籍与重载目录

- 「编辑本地书籍」入口在 `menu/book_read.xml` 的 `menu_edit_content` 下方，仅 `book.isLocalTxt && LocalBook.canWrite(book)` 时显示。
- 对话框 `ui/book/read/LocalBookEditDialog.kt` 从 `ReadBook.curTextChapter.getPageByReadPos(durChapterPos)` 取当前页文字用于定位；`findPageRange()` 支持「整段精确 → 忽略空白 → 首尾 12 字锚点」，编辑框显示的是**文件原文片段**而非屏幕文字（屏幕文字带缩进/替换规则）。全部失败时退化为整章编辑并 toast；正文可清空。
- 写回 `LocalBook.saveChapterRawText()` 只替换该页在原文中的区间（或整章模式替换除标题行外内容），`replaceChapterSegment()` 流式重写文件；标题改动替换章节首行；写后 `latestChapterTime` 置当前时间避免被判外部改动。
- 写后 `LocalBook.getChapterList()` 重解析全本 → 整表替换 `chapters` → 保持阅读位置 → `ReadBook.onChapterListUpdated(book)` 刷新正文。
- 「重载书籍目录」在 `TocActivity.onMenuOpened()` 里对 `menu_reload_toc` 单独放开可见性（章节页签所有书籍），`TocViewModel.reloadToc()` 本地重解析 / 网络重请求书源目录，两条路都 `bookData.postValue(book)`。

## 5. 文档维护约定

- 新增功能后同步四处：`PRD.md`（功能与版本历史）、`TECH_DOC.md`（实现要点）、`DB_DOC.md`（表结构/迁移）、本文件（交接）。
- `PRD.md` 数据表清单需与 `AppDatabase.kt` 的 `entities = [...]` 对齐（当前 24 张表 + 1 视图）；`TECH_DOC.md` 第 3 章 DAO（24 个）/ 实体（24 + 1 视图）清单、`DB_DOC.md` 头部计数与迁移历史同理。
- 文档头部与尾部分别维护「文档版本 / 更新日期」。

## 6. 常用代码位置速查

- 偏好键：`app/src/main/java/io/legado/app/constant/PreferKey.kt`
- 配置封装：`app/src/main/java/io/legado/app/help/config/AppConfig.kt`
- 其他设置页：`app/src/main/java/io/legado/app/ui/config/OtherConfigFragment.kt` / `res/xml/pref_config_other.xml`
- 我的页面：`app/src/main/java/io/legado/app/ui/main/my/MyFragment.kt` / `res/xml/pref_main.xml`
- 书架基类：`app/src/main/java/io/legado/app/ui/main/bookshelf/BaseBookshelfFragment.kt` / `res/menu/main_bookshelf.xml`
- 标签：`app/src/main/java/io/legado/app/ui/book/tag/`（TagManageDialog / TagSelectDialog / TagConfigActivity / TagViewModel / TagEditDialog / TagGroupManageDialog / TagGroupEditDialog / TagSort / TagListItem）；数据层 `data/entities/BookTagGroup.kt`、`data/dao/BookTagGroupDao.kt`
- 本地书籍编辑：`ui/book/read/LocalBookEditDialog.kt`、`model/localBook/LocalBook.kt`（`getChapterRawText` / `saveChapterRawText`）、`res/layout/dialog_local_book_edit.xml`
- 目录页：`ui/book/toc/TocActivity.kt` / `TocViewModel.kt`、`res/menu/book_toc.xml`
- 搜索：`app/src/main/java/io/legado/app/ui/book/search/SearchActivity.kt` / `res/layout/activity_book_search.xml`
- 阅读与内容搜索：`ui/book/read/ReadBookActivity.kt`、`ReadBookViewModel.kt`、`ui/book/searchContent/`
- WebDAV：`help/AppWebDav.kt`、`model/remote/`（RemoteBook / RemoteBookManager / RemoteBookWebDav）、`ui/book/import/remote/`、`res/xml/pref_config_backup.xml`
- 构建脚本：根目录 `check.bat` / `deploy.bat` / `build.bat`、`scripts/*.sh`、`mise.toml`
