# 清除网络书源功能 开发说明书

## Why
当前阅读APP（Legado）同时支持网络书源、本地书籍和WebDAV书籍三种阅读来源。需要移除网络书源（BookSource）相关功能，使得APP仅保留本地书籍和WebDAV书籍的阅读能力，同时确保不影响现有本地书籍和WebDAV书籍的正常使用。

## 核心判断逻辑
书籍类型的判断依赖 `Book.origin` 字段：
- `origin == "loc_book"` → 本地书籍
- `origin.startsWith("webDav::")` → WebDAV书籍
- `origin == 其他值（即书源URL）` → 网络书源书籍

`Book.isLocal` 扩展属性的判断逻辑：
```kotlin
val Book.isLocal: Boolean
    get() {
        if (type == 0) {
            return origin == BookType.localTag || origin.startsWith(BookType.webDavTag)
        }
        return isType(BookType.local)
    }
```

## What Changes

### 需要删除的模块
- **BookSource 数据实体与数据库**：删除 `BookSource`、`BookSourcePart` 实体、`BookSourceDao`、数据库表 `book_sources`
- **WebBook 网络书籍模型**：删除整个 `model/webBook/` 目录
- **书源管理UI**：删除书源管理、编辑、调试、导入导出等界面
- **发现/探索功能**：删除基于书源的发现/探索功能
- **换源功能**：删除书籍/章节换源功能
- **书源登录**：删除书源登录相关功能
- **书源校验**：删除书源校验服务
- **Web端书源API**：删除书源相关的API和WebSocket
- **书源订阅规则**：删除 `RuleSub` 相关功能
- **在线搜索**：删除基于书源的在线搜索功能

### 需要修改的模块
- **ReadBook**：移除 `bookSource` 引用，网络内容加载逻辑改为仅支持本地/WebDAV
- **ReadManga**：同上
- **AudioPlay**：同上
- **Book实体**：`origin` 字段仅保留 `loc_book` 和 `webDav::` 两种值
- **BookExtensions**：`isLocal` 判断逻辑简化
- **BookHelp**：移除与 `BookSource` 的交互
- **AppDatabase**：移除 `bookSourceDao`、`BookSource` 实体注册
- **书架管理**：移除网络书源相关筛选和操作
- **书籍信息**：移除网络书源相关操作
- **备份恢复**：移除书源备份恢复逻辑
- **MainViewModel / BookshelfViewModel**：移除书源相关数据流

### 不需要修改的模块
- **LocalBook**：本地书籍导入、解析功能完全保留
- **WebDav**：WebDAV同步功能完全保留
- **RSS源**：RSS源（RssSource）功能保留
- **替换规则**：替换规则功能保留
- **TxtTocRule**：本地TXT目录规则保留
- **HttpTTS**：HTTP TTS功能保留
- **DictRule**：字典规则保留

## Impact
- Affected specs: 网络书源、发现/探索、换源、在线搜索、书源订阅
- Affected code: 约60+个Kotlin文件、30+个XML布局文件、20+个菜单文件、数据库Schema

---

## ADDED Requirements
（本说明书为删除功能，无需新增需求）

## REMOVED Requirements

### Requirement: 网络书源管理
**原功能**：用户可添加、编辑、删除、导入、导出网络书源（BookSource），书源包含搜索URL、发现URL、书籍信息规则、目录规则、正文规则等配置。
**影响范围**：
- 数据实体：`BookSource.kt`、`BookSourcePart.kt`
- DAO：`BookSourceDao.kt`
- 数据库表：`book_sources`、视图 `book_sources_part`
- UI：`BookSourceActivity`、`BookSourceEditActivity`、`BookSourceViewModel`、`BookSourceEditViewModel`
- 导入导出：`ImportBookSourceDialog`、`ImportBookSourceViewModel`
- 帮助类：`SourceHelp.kt`、`BookSourceExtensions.kt`
- 配置：`SourceConfig.kt`
- 默认数据：`bookSources.json`
- 数据库迁移：`DatabaseMigrations.kt` 中书源相关迁移
- 常量：`BookSourceType.kt`

### Requirement: 网络书籍在线搜索
**原功能**：用户可通过网络书源在线搜索网络上的书籍，并添加到书架。
**影响范围**：
- 网络搜索：`WebBook.searchBook()`、`SearchModel.kt`
- UI：`SearchActivity`、`SearchViewModel`、`SearchScope`、`SearchScopeDialog`

### Requirement: 发现/探索功能
**原功能**：基于书源的发现/探索功能，展示书源提供的分类浏览和推荐书籍。
**影响范围**：
- 模型：`WebBook.exploreBook()`、`BookList.kt`
- UI：`ExploreFragment`、`ExploreViewModel`、`ExploreAdapter`、`ExploreShowActivity`、`ExploreShowViewModel`
- 扩展：`BookSourceExtensions.kt` 中 `exploreKinds()` 等方法

### Requirement: 书籍信息在线获取
**原功能**：通过网络书源获取书籍的详细信息（封面、简介、分类等）。
**影响范围**：
- 模型：`WebBook.getBookInfo()`、`BookInfo.kt`
- UI：`BookInfoActivity`、`BookInfoViewModel` 中网络书源相关逻辑

### Requirement: 书籍/章节换源
**原功能**：用户可为网络书籍更换书源，或为单个章节更换书源。
**影响范围**：
- UI：`ChangeBookSourceDialog`、`ChangeBookSourceViewModel`、`ChangeChapterSourceDialog`、`ChangeChapterSourceViewModel`、`SourcePickerDialog`

### Requirement: 书源调试
**原功能**：提供书源规则调试工具，验证书源配置是否正确。
**影响范围**：
- UI：`BookSourceDebugActivity`、`BookSourceDebugModel`
- WebSocket：`BookSourceDebugWebSocket`

### Requirement: 书源登录
**原功能**：支持需要登录的书源，提供WebView登录界面。
**影响范围**：
- UI：`SourceLoginActivity`、`SourceLoginViewModel`、`SourceLoginDialog`
- 实体接口：`BaseSource.kt` 中登录相关方法

### Requirement: 书源校验
**原功能**：后台自动校验书源有效性和响应时间。
**影响范围**：
- 服务：`CheckSourceService`、`CheckSource.kt`

### Requirement: 书源订阅规则
**原功能**：支持订阅远程书源规则（RuleSub），自动更新书源配置。
**影响范围**：
- 实体：`RuleSub.kt`
- UI：`activity_rule_sub.xml`、`dialog_rule_sub_edit.xml`、`item_rule_sub.xml`

### Requirement: 网络书籍内容获取
**原功能**：通过网络书源获取在线书籍的章节目录和正文内容。
**影响范围**：
- 模型：`WebBook.getChapterList()`、`WebBook.getContent()`、`BookChapterList.kt`、`BookContent.kt`
- 缓存：`CacheBook.kt`、`CacheBookService.kt`
- 阅读：`ReadBook.kt` 中 `bookSource` 和 `upWebBook()` 方法

### Requirement: Web端书源API
**原功能**：通过HTTP API管理书源，通过WebSocket调试书源。
**影响范围**：
- API：`BookSourceController.kt`
- WebSocket：`BookSearchWebSocket.kt`

### Requirement: 备份恢复中的书源数据
**原功能**：备份和恢复书源数据。
**影响范围**：
- `Backup.kt`、`Restore.kt`、`ImportOldData.kt`

---

## MODIFIED Requirements

### Requirement: 阅读功能（ReadBook）
**修改**：移除 `bookSource` 引用，`upWebBook()` 方法不再查询书源数据库。网络内容加载失败时直接提示"不支持网络书源"，不再尝试通过书源获取内容。
**关键代码**：
- `ReadBook.kt`：`bookSource` 字段移除，`upWebBook()` 简化
- `ReadManga.kt`：同上
- `AudioPlay.kt`：同上

### Requirement: 书籍实体（Book）
**修改**：`origin` 字段仅保留 `BookType.localTag`（"loc_book"）和 `BookType.webDavTag`（"webDav::"）两种值。
**关键代码**：
- `Book.kt`：`origin` 字段默认值保持 `BookType.localTag`
- `BookExtensions.kt`：`isLocal` 直接返回 `true`（因为所有书籍都是本地或WebDAV），`getBookSource()` 方法移除
- `BookType.kt`：保留 `localTag`、`webDavTag`，移除 `allBookType` 中网络书源相关组合

### Requirement: 书架管理
**修改**：移除网络书源相关的书籍分组（如"网络未分组"分组），移除网络书源筛选和排序功能。
**关键代码**：
- `BookshelfManageActivity`、`BookshelfManageViewModel`：移除网络书源相关操作
- `MainViewModel`、`BookshelfViewModel`：移除书源相关数据流

### Requirement: 数据库
**修改**：移除 `book_sources` 表和 `book_sources_part` 视图，移除 `bookSourceDao`。
**关键代码**：
- `AppDatabase.kt`：移除 `BookSource` entity、`BookSourcePart` view、`bookSourceDao` 字段
- `DatabaseMigrations.kt`：移除书源相关迁移

### Requirement: 存储备份
**修改**：备份恢复时不再处理书源数据。
**关键代码**：
- `Backup.kt`：移除书源备份逻辑
- `Restore.kt`：移除书源恢复逻辑
- `ImportOldData.kt`：移除旧版书源导入逻辑

### Requirement: BaseSource 接口
**修改**：`BaseSource` 接口可保留（RSS源仍使用），但可移除仅网络书源使用的登录相关方法（如果RSS源不使用）。
**注意**：RSS源（RssSource）也实现了 `BaseSource`，需小心处理。

### Requirement: AnalyzeRule / AnalyzeUrl
**修改**：这两个类是书源规则解析的核心，在移除网络书源后，如果仅用于本地书籍和RSS，可简化。但 `AnalyzeUrl` 在 `LocalBook.saveBookFile()` 中用于下载在线文件，需要保留HTTP请求能力。
**关键代码**：
- `AnalyzeRule.kt`：移除 `BookSource` 相关引用，保留RSS相关
- `AnalyzeUrl.kt`：保留HTTP请求能力，移除书源特有的规则解析