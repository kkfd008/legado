# Tasks

## 第1阶段：数据层清理（无外部依赖，可并行）

- [x] Task 1: 删除 BookSource 数据实体和DAO
  - [x] 删除 `data/entities/BookSource.kt`
  - [x] 删除 `data/entities/BookSourcePart.kt`
  - [x] 删除 `data/dao/BookSourceDao.kt`
  - [x] 删除 `data/entities/RuleSub.kt`（书源订阅规则实体）
  - [x] 删除 `data/dao/RuleSubDao.kt`（如果存在）
  - [x] 删除 `constant/BookSourceType.kt`

- [x] Task 2: 修改 AppDatabase 数据库配置
  - [x] 从 `entities` 列表中移除 `BookSource::class`
  - [x] 从 `views` 列表中移除 `BookSourcePart::class`
  - [x] 移除 `bookSourceDao` 抽象字段
  - [x] 移除 `ruleSubDao` 抽象字段
  - [x] 移除 `onOpen()` 中书源相关的SQL语句（`upBookSourceLoginUiSql`）
  - [x] 移除 `BOOK_SOURCE_TABLE_NAME` 常量
  - [x] 移除"网络未分组"分组创建

- [x] Task 3: 删除 WebBook 网络书籍模型
  - [x] 删除 `model/webBook/` 整个目录（含 `WebBook.kt`、`BookList.kt`、`BookInfo.kt`、`BookContent.kt`、`BookChapterList.kt`、`SearchModel.kt`）

- [x] Task 4: 删除缓存和校验相关模型
  - [x] 删除 `model/CacheBook.kt`
  - [x] 删除 `model/CheckSource.kt`
  - [x] 删除 `model/Debug.kt`

- [x] Task 5: 删除服务层
  - [x] 删除 `service/CheckSourceService.kt`
  - [x] 删除 `service/CacheBookService.kt`

- [x] Task 6: 删除帮助类中书源相关代码
  - [x] 删除 `help/source/SourceHelp.kt`
  - [x] 删除 `help/source/BookSourceExtensions.kt`
  - [x] 删除 `help/config/SourceConfig.kt`
  - [x] 修改 `help/source/BaseSourceExtensions.kt`（移除 BookSource 相关分支）

- [x] Task 7: 删除默认数据
  - [x] 删除 `assets/defaultData/bookSources.json`

## 第2阶段：UI层清理（依赖第1阶段）

- [x] Task 8: 删除书源管理UI
  - [x] 删除 `ui/book/source/` 整个目录（含 `manage/`、`edit/`、`debug/` 子目录）
  - [x] 删除 `ui/association/ImportBookSourceDialog.kt`
  - [x] 删除 `ui/association/ImportBookSourceViewModel.kt`

- [x] Task 9: 删除换源UI
  - [x] 删除 `ui/book/changesource/` 整个目录

- [x] Task 10: 删除发现/探索UI
  - [x] 删除 `ui/main/explore/` 整个目录
  - [x] 删除 `ui/book/explore/` 整个目录

- [x] Task 11: 删除书源登录UI
  - [x] 删除 `ui/login/` 整个目录

- [x] Task 12: 删除书源管理相关布局文件
  - [x] 删除所有书源相关布局文件（24个）

- [x] Task 13: 删除书源管理相关菜单文件
  - [x] 删除所有书源相关菜单文件（20个）

- [x] Task 14: 删除/修改书源相关图标资源
  - [x] 删除所有书源相关图标（9个）

## 第3阶段：业务逻辑修改（依赖第1、2阶段）

- [x] Task 15: 修改 ReadBook 阅读引擎
  - [x] 移除 `ReadBook.kt` 中 `bookSource` 字段
  - [x] 简化 `upWebBook()` 方法
  - [x] 修改 `upToc()` 方法
  - [x] 修改 `download()` / `downloadAwait()` 方法
  - [x] 移除 `import io.legado.app.data.entities.BookSource` 和 `import io.legado.app.model.webBook.WebBook`

- [x] Task 16: 修改 ReadManga 漫画阅读
  - [x] 移除 `ReadManga.kt` 中 `bookSource` 字段和相关引用
  - [x] 移除 WebBook 导入

- [x] Task 17: 修改 AudioPlay 音频播放
  - [x] 移除 `AudioPlay.kt` 中 `bookSource` 字段和相关引用
  - [x] 移除 WebBook 导入

- [x] Task 18: 修改 Book 实体和相关扩展
  - [x] 移除 `BookExtensions.kt` 中 `getBookSource()` 方法
  - [x] 调整 `isLocal` 逻辑
  - [x] 移除 `BookType.kt` 中 `allBookType` 常量
  - [x] 修改 `BookHelp.kt` 中 `BookSource` 参数为 `BaseSource`

- [x] Task 19: 修改书架管理
  - [x] 修改 `BookshelfManageActivity.kt`：移除网络书源相关操作
  - [x] 修改 `BookshelfManageViewModel.kt`：移除书源相关操作
  - [x] 修改 `MainViewModel.kt`：移除书源相关数据流

- [x] Task 20: 修改书籍信息页
  - [x] 修改 `BookInfoActivity.kt`：移除网络书源相关操作按钮
  - [x] 修改 `BookInfoViewModel.kt`：移除书源相关操作

- [x] Task 21: 修改搜索功能
  - [x] 修改 `SearchActivity.kt`：移除在线搜索，仅保留本地搜索
  - [x] 修改 `SearchViewModel.kt`：移除在线搜索逻辑
  - [x] 删除 `SearchScope.kt` / `SearchScopeDialog.kt`

- [x] Task 22: 修改备份恢复
  - [x] 修改 `Backup.kt`：移除书源备份逻辑
  - [x] 修改 `Restore.kt`：移除书源恢复逻辑
  - [x] 修改 `ImportOldData.kt`：移除旧版书源导入逻辑

- [x] Task 23: 修改API和WebSocket
  - [x] 删除 `api/controller/BookSourceController.kt`
  - [x] 删除 `web/socket/BookSourceDebugWebSocket.kt`
  - [x] 删除 `web/socket/BookSearchWebSocket.kt`
  - [x] 修改 `api/controller/BookController.kt`：移除书源相关接口

- [x] Task 24: 修改底部导航
  - [x] 修改 `MainActivity`：移除"发现"Tab
  - [x] 修改导航菜单配置

- [x] Task 25: 修改 ImageProvider
  - [x] ImageProvider.kt 不存在，跳过

- [x] Task 26: 修改 AnalyzeRule / AnalyzeUrl
  - [x] 移除 `AnalyzeRule.kt` 中 `BookSource` 相关引用
  - [x] `AnalyzeUrl.kt` 无需修改（原本未引用 BookSource）

- [x] Task 27: 修改 ChangeCover 换封面
  - [x] 修改 `ChangeCoverViewModel.kt`：移除书源相关封面获取逻辑

- [x] Task 28: 修改 WebViewModel
  - [x] WebViewModel.kt 不存在，跳过

## 第4阶段：清理与验证

- [x] Task 29: 清理 AndroidManifest.xml
  - [x] 移除已删除Activity/Service的注册声明（7个）
  - [x] 移除不再需要的权限声明

- [x] Task 30: 清理资源文件引用
  - [x] 删除 `strings.xml` 中书源相关字符串（70+个）
  - [x] 删除 `arrays.xml` 中书源相关数组
  - [x] 清理 `ids.xml` 中书源相关ID（无需清理）

- [x] Task 31: 删除数据库Schema文件
  - [x] 删除所有包含 `book_sources` 表的旧版本Schema文件（79个）

- [ ] Task 32: 编译验证
  - [ ] 确保项目编译通过，无编译错误
  - [ ] 确保本地书籍导入可用
  - [ ] 确保WebDAV书籍同步可用
  - [ ] 确保本地阅读功能正常

# Task Dependencies

- Task 2 依赖 Task 1（需要删除实体后才能修改 Database）
- Task 8-14 依赖 Task 1-7（UI层依赖数据层清理）
- Task 15-28 依赖 Task 1-14（业务逻辑修改依赖数据和UI层清理）
- Task 29-32 依赖 Task 1-28（清理验证依赖所有修改完成）
- 同阶段内无依赖关系的Task可并行执行