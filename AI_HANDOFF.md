# AI 交接文档（会话记忆压缩）

> 用途：供后续 AI 快速接续工作。本文档记录项目现状、已完成工作、关键技术细节与构建方式。
> 最近更新：2026-11-05（同步 PRD / TECH_DOC 到当前代码）

## 1. 项目概览

- 项目：Legado（开源 Android 电子书阅读器，Kotlin + MVVM + Room）
- 仓库：`https://github.com/kkfd008/legado.git`（分支 `master`）
- 本地路径：`D:/liuwen/dev/project/legado`（另有 worktree：`C:/Users/Administrator/WorkBuddy/Worktrees/LegadoPlus/main-f251f0f2`）
- 数据库：Room，版本 79，23 表 + 1 视图（`book_sources_part`）
- 文档：`PRD.md`（产品需求/功能，v1.3）、`TECH_DOC.md`（技术实现，v1.1）、`DB_DOC.md`、`api.md`、`English.md`

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
3. 书架优化：右上角菜单「更新本地书籍」；本地页底部按钮（删除/放入书架/放入标签/放入分组 + 删除确认）。
4. 修复「更新本地书籍」后已移动目录的本地书未从书架移除（`LocalBook.getLastModified` 增加 `content://` 校验；`MainViewModel.upLocalBook` 改遍历 `bookDao.all`）。
5. 「显示书源」开关（默认关闭）：隐藏我的页面「书源管理」、书架菜单「添加网址」。
6. 修复阅读页内容关键字搜索缓存 bug（返回结果列表变空白）。
7. 「WebDAV」总开关（默认关闭）：隐藏「备份与恢复」「添加远程书籍」，并短路阅读进度同步。
8. 标签管理页 / 标签选择弹窗改网格布局，新增「标签管理每行标签数」（1-4，默认1），标签文字靠右、按 order 倒序、拖拽排序不回退反转。
9. 搜索页输入帮助面板：搜索历史 + 「热门标签」标题 + 流式换行标签列表；回车在关闭书源时保留面板。
10. 构建脚本统一为 `check.bat` / `deploy.bat` / `build.bat` + `scripts/*.sh`，mise 统一管理 JDK 17 / Gradle 8.13，SDK 固定 36。

## 4. 最近几项功能的关键实现

### 4.1 功能开关（能力级门控）

| 开关 | PreferKey | 默认 | 门控 |
|------|-----------|------|------|
| 显示书源 | `showBookSource` | false | `MyFragment`「书源管理」、`BaseBookshelfFragment`「添加网址」、搜索页回车是否收起输入帮助 |
| WebDAV | `webDavEnabled` | false | `MyFragment`「备份与恢复」(`web_dav_setting`)、`BaseBookshelfFragment`「添加远程书籍」(`menu_remote`)、`AppConfig.syncBookProgress` / `syncBookProgressPlus` |
| 标签每行数 | `tagManageSpanCount` | 1 | `AppConfig.tagManageSpanCount`（`coerceIn(1,4)`）→ 两个标签弹窗的 `GridLayoutManager` |

- 入口显隐在 `onCreatePreferences` + `onResume` 里刷 `isVisible`，无需 EventBus。
- 新增此类开关：`PreferKey` → `AppConfig` getter（必要时在派生属性上 `&&`）→ `pref_*.xml` 控件 → Fragment 里设置可见性。

### 4.2 标签网格与倒序排序

- `TagManageDialog` / `TagSelectDialog` 都用 `GridLayoutManager(ctx, AppConfig.tagManageSpanCount)`。
- 列表按 `order` **倒序**展示：`sortedByDescending { it.order }`。
- 拖拽写回必须用 `order = size - index`（`TagManageDialog.onClearView`、`TagViewModel.upOrder`、`TagConfigAdapter.onClearView`），否则倒序展示会把拖拽结果整体反转。
- 「标签配置」页（`TagConfigActivity`）同样支持长按拖拽排序，参考 `TxtTocRuleActivity` 的 `ItemTouchCallback` 接法；拖拽期间用 `adapter.isDragging` 跳过 Flow 刷新，松手用 `copy` 生成新对象后经 `BookTagDao.update(vararg)` 单事务批量回写，避免污染 DiffUtil 旧列表。
- TXT 目录规则列表页显示每条规则匹配的书籍数：`combine(observeAll(), bookDao.flowAll())` 按本地书 `book.tocUrl == rule.rule` 统计；数量不进 Room 实体，用 `TxtTocRuleAdapter` 内的 `Map<ruleId, count>` + `upCount` payload 局部刷新，避免数据库迁移与导出 JSON 被污染。
- `TagSelectDialog` 取消复选框，改为「点击整行切换选中 + activated 背景填充 colorAccent + 文字转白」。
- 相关样式：`res/drawable/selector_tag_select_bg.xml`、`shape_fillet_btn_selected.xml`；布局 `item_tag_select.xml`、`item_book_tag_manage.xml`。

### 4.3 搜索页输入帮助面板

- 布局 `activity_book_search.xml`：`ll_input_help` 内自上而下 `tv_tag_title` + 搜索历史 `rv_history_key` + `rv_tag_suggest`。
- 热门标签数据：`bookTagDao.flowHot()`（`book_tags.hot = 1`，按 order 倒序；默认非热门，不再按排序取前 10），`FlexboxLayoutManager` 流式换行，点击以 `#标签名` 搜索；热门开关在「我的 - 标签配置」页设置。
- `onQueryTextSubmit` 中 `if (AppConfig.showBookSource) visibleInputHelp(false)`，关闭书源时保留面板避免空白结果页。

### 4.4 阅读页关键字搜索缓存

- 根因：`SearchContentActivity.openSearchResult` 用非粘性 `postEvent` 回填，`ReadBookActivity` 处于 STOPPED 收不到，导致 `ReadBookViewModel.searchResultList` 一直为 null。
- 修复：`ReadBookActivity.searchContentActivity` 回调里补 `viewModel.searchResultList = searchResultList`。

## 5. 文档维护约定

- 新增功能后同步三处：`PRD.md`（功能与版本历史）、`TECH_DOC.md`（实现要点）、本文件（交接）。
- `PRD.md` 数据表清单需与 `AppDatabase.kt` 的 `entities = [...]` 对齐（当前 23 张表 + 1 视图）；`TECH_DOC.md` 第 3 章 DAO / 实体清单同理。
- 文档头部与尾部分别维护「文档版本 / 更新日期」。

## 6. 常用代码位置速查

- 偏好键：`app/src/main/java/io/legado/app/constant/PreferKey.kt`
- 配置封装：`app/src/main/java/io/legado/app/help/config/AppConfig.kt`
- 其他设置页：`app/src/main/java/io/legado/app/ui/config/OtherConfigFragment.kt` / `res/xml/pref_config_other.xml`
- 我的页面：`app/src/main/java/io/legado/app/ui/main/my/MyFragment.kt` / `res/xml/pref_main.xml`
- 书架基类：`app/src/main/java/io/legado/app/ui/main/bookshelf/BaseBookshelfFragment.kt` / `res/menu/main_bookshelf.xml`
- 标签：`app/src/main/java/io/legado/app/ui/book/tag/`（TagManageDialog / TagSelectDialog / TagViewModel / TagEditDialog）
- 搜索：`app/src/main/java/io/legado/app/ui/book/search/SearchActivity.kt` / `res/layout/activity_book_search.xml`
- 阅读与内容搜索：`ui/book/read/ReadBookActivity.kt`、`ReadBookViewModel.kt`、`ui/book/searchContent/`
- WebDAV：`help/AppWebDav.kt`、`model/remote/`（RemoteBook / RemoteBookManager / RemoteBookWebDav）、`ui/book/import/remote/`、`res/xml/pref_config_backup.xml`
- 构建脚本：根目录 `check.bat` / `deploy.bat` / `build.bat`、`scripts/*.sh`、`mise.toml`
