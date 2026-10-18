# AI 交接文档（会话记忆压缩）

> 用途：供后续 AI 快速接续工作。本文档记录项目现状、已完成工作、关键技术细节与构建方式。

## 1. 项目概览

- 项目：Legado（开源 Android 电子书阅读器，Kotlin + MVVM + Room）
- 仓库：`https://github.com/kkfd008/legado.git`（分支 `master`）
- 工作目录：`/workspace`
- 数据库：Room，版本 79，22 表 + 1 视图
- 已有文档：`PRD.md`、`DB_DOC.md`、`TECH_DOC.md`、`api.md`、`English.md`

## 2. 构建环境（重要）

- Java 17：`/root/.local/share/mise/installs/java/17.0.2`
- Android SDK：`/root/android-sdk`（本会话已安装）
  - `platforms/android-36`
  - `build-tools/36.0.0`、`build-tools/35.0.0`
  - `platform-tools`
- Gradle：8.13（wrapper 已下载）
- 代理：`127.0.0.1:18080`
- 编译 SDK：36，AGP 8.13.2，Kotlin JVM 17

### 构建命令（可直接复用）

```bash
export JAVA_HOME=/root/.local/share/mise/installs/java/17.0.2
export ANDROID_HOME=/root/android-sdk
export ANDROID_SDK_ROOT=/root/android-sdk
export GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=18080 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=18080 -Dorg.gradle.jvmargs=\"-XX:+UseParallelGC -Xmx2g -Xms256m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8\""
./gradlew assembleAppDebug --no-daemon --max-workers=2
```

> 注意：默认 `gradle.properties` 里 `-Xmx6g` 会因沙箱内存不足导致 Gradle daemon 被 OOM 杀死（报 "daemon disappeared"）。务必按上面用 `-Dorg.gradle.jvmargs` 覆盖为 `-Xmx2g`，并加 `--max-workers=2`。

### APK 产物路径

```
/workspace/app/build/outputs/apk/app/debug/legado_app_3.26.10181407.apk
```

### Git 相关

- 身份已配置：`kkfd008 <kkfd008@github.com>`
- 远程：`https://github.com/kkfd008/legado.git`
- 此前 push 使用了用户在对话中提供的 token（勿将 token 明文写入文档；需要 push 时向用户索取或用已保存的凭据）。

## 3. 本会话之前已完成的工作

1. 更新 `PRD.md`，生成 `DB_DOC.md`、`TECH_DOC.md`，为大量 entities/dao/model/service 添加中文注释。
2. 在线服务新增「TXT 目录规则」功能模块：
   - 后端：`app/src/main/java/io/legado/app/api/controller/TxtTocRuleController.kt`
   - 路由：`app/src/main/java/io/legado/app/web/HttpServer.kt`
   - 前端：`app/src/main/assets/web/txtTocRule/index.html`
3. 书架优化：
   - 右上角菜单新增「更新本地书籍」`menu_update_local_book`。
   - 本地页底部按钮调整（删除/放入书架/放入标签/放入分组 + 删除确认弹窗）。
4. 修复「更新本地书籍」bug：本地书籍移动目录后未从书架移除。
   - `app/src/main/java/io/legado/app/model/localBook/LocalBook.kt` 的 `getLastModified` 增加 `content://` 的 `exists()` 校验。
   - `app/src/main/java/io/legado/app/ui/main/MainViewModel.kt` 的 `upLocalBook()` 改用 `appDb.bookDao.all` 遍历。

## 4. 最近完成的两个功能（本次需求）

### 功能1：新增「显示书源」开关（默认关闭）

关闭状态下：
- 我的页面「书源管理」不显示
- 书架页面弹窗菜单「添加网址」不显示

涉及文件与改动：
- `app/src/main/java/io/legado/app/constant/PreferKey.kt`
  - 新增 `const val showBookSource = "showBookSource"`（在 `showRss` 后）。
- `app/src/main/java/io/legado/app/help/config/AppConfig.kt`
  - 新增 `val showBookSource: Boolean get() = appCtx.getPrefBoolean(PreferKey.showBookSource, false)`。
- `app/src/main/res/xml/pref_config_other.xml`
  - 在 `other_setting` 分类顶部新增 `SwitchPreference`（key=`showBookSource`，defaultValue=`false`，title=`@string/show_book_source`）。
- 文案：`values/strings.xml`、`values-zh/strings.xml` 新增 `show_book_source` / `显示书源`。
- `app/src/main/java/io/legado/app/ui/main/my/MyFragment.kt`
  - `onCreatePreferences` 与 `onResume` 中：`findPreference<Preference>("bookSourceManage")?.isVisible = AppConfig.showBookSource`。
- `app/src/main/java/io/legado/app/ui/main/bookshelf/BaseBookshelfFragment.kt`
  - `onCompatCreateOptionsMenu` 与新增 `onResume` 中：`menu.findItem(R.id.menu_add_url)?.isVisible = AppConfig.showBookSource`（`onResume` 用 `supportToolbar?.menu`）。

> 动态刷新依赖 `MyFragment.onResume` 与 `BaseBookshelfFragment.onResume`，无需额外 EventBus。

### 功能2：修复阅读页内容关键字搜索缓存 bug

现象：首次搜索出结果列表 → 点进某个阅读点 → 返回搜索结果列表时变成空白（无缓存）。

根因：
- `viewModel.searchResultList`（`ReadBookViewModel`）原本只通过 `observeEvent<List<SearchResult>>(EventBus.SEARCH_RESULT)` 回填。
- 该事件在 `SearchContentActivity.openSearchResult` 里用 `postEvent(...)`（LiveEventBus 非粘性）发出，此时 `ReadBookActivity` 处于 STOPPED 状态，非活跃观察者收不到，导致缓存始终为 null。
- 第二次打开搜索时 `openSearchActivity` 里 `viewModel.searchResultList?.first()?.let {...}` 命中不了，无法把缓存传给 `SearchContentActivity`，列表变空。

修复：
- `app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt` 的 `searchContentActivity` 回调中，新增一行：
  `viewModel.searchResultList = searchResultList`（在 `viewModel.searchContentQuery = searchResult.query` 之后）。

缓存生命周期（满足需求）：
- 按书保留：`ReadBookViewModel` 与 `ReadBookActivity` 一一对应（每本书一个实例）。
- 更新关键字覆盖：新搜索返回的 list 会覆盖 `searchResultList` 与 `searchContentQuery`。
- 退出阅读页清理：Activity finish 时 ViewModel 随 ViewModelStore 清理，无需显式清除。

关键相关文件：
- `app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt`（`searchContentActivity` 回调、`openSearchActivity`、`exitSearchMenu`）
- `app/src/main/java/io/legado/app/ui/book/read/ReadBookViewModel.kt`（`searchContentQuery` / `searchResultList` / `searchResultIndex`）
- `app/src/main/java/io/legado/app/ui/book/searchContent/SearchContentActivity.kt`
- `app/src/main/java/io/legado/app/ui/book/searchContent/SearchContentViewModel.kt`
- `app/src/main/java/io/legado/app/ui/book/read/SearchMenu.kt`
- `app/src/main/java/io/legado/app/model/ReadBook.kt`（`clearSearchResult` 等）

## 5. 尚未完成 / 可能的下一步

- 本次两个功能的改动**尚未提交并 push 到远程**（如需 push，请向用户确认并索取/复用 token）。
- 建议在真机/模拟器上验证：
  1. 其他设置里关闭「显示书源」后，我的页面书源管理、书架菜单添加网址均隐藏；打开后恢复显示。
  2. 阅读页关键字搜索 → 点结果 → 返回列表仍保留结果；换关键字覆盖；退出书籍后缓存清理。

## 6. 常用代码位置速查

- 偏好键常量：`app/src/main/java/io/legado/app/constant/PreferKey.kt`
- AppConfig 映射：`app/src/main/java/io/legado/app/help/config/AppConfig.kt`
- 其他设置页：`app/src/main/java/io/legado/app/ui/config/OtherConfigFragment.kt`
- 我的页面：`app/src/main/java/io/legado/app/ui/main/my/MyFragment.kt`
- 书架基类：`app/src/main/java/io/legado/app/ui/main/bookshelf/BaseBookshelfFragment.kt`
- 阅读页 Activity/ViewModel：`app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt` / `ReadBookViewModel.kt`
- 内容搜索：`app/src/main/java/io/legado/app/ui/book/searchContent/`
