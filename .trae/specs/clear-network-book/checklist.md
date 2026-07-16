# Checklist

## 数据层
- [ ] `BookSource.kt` 实体文件已删除
- [ ] `BookSourcePart.kt` 视图文件已删除
- [ ] `BookSourceDao.kt` DAO文件已删除
- [ ] `RuleSub.kt` 实体文件已删除
- [ ] `BookSourceType.kt` 常量文件已删除
- [ ] `AppDatabase.kt` 中不再注册 `BookSource` entity 和 `BookSourcePart` view
- [ ] `AppDatabase.kt` 中已移除 `bookSourceDao` 抽象字段
- [ ] `AppDatabase.kt` 中 `onOpen()` 已移除 `upBookSourceLoginUiSql` 等书源相关SQL
- [ ] `AppDatabase.kt` 中 `onOpen()` 已移除"网络未分组"（`IdNetNone`）分组创建
- [ ] `DatabaseMigrations.kt` 中书源相关迁移已处理
- [ ] 数据库版本号已更新，新增迁移删除 `book_sources` 表

## 模型层
- [ ] `model/webBook/` 整个目录已删除
- [ ] `model/CacheBook.kt` 已删除
- [ ] `model/CheckSource.kt` 已删除
- [ ] `model/Debug.kt` 中书源相关代码已移除

## 服务层
- [ ] `service/CheckSourceService.kt` 已删除
- [ ] `service/CacheBookService.kt` 已删除或简化

## 帮助类
- [ ] `help/source/SourceHelp.kt` 已删除
- [ ] `help/source/BookSourceExtensions.kt` 已删除
- [ ] `help/config/SourceConfig.kt` 已删除
- [ ] `help/source/BaseSourceExtensions.kt` 已移除 BookSource 分支

## 默认数据
- [ ] `assets/defaultData/bookSources.json` 已删除

## UI层 - 书源管理
- [ ] `ui/book/source/` 整个目录已删除
- [ ] `ui/association/ImportBookSourceDialog.kt` 已删除
- [ ] `ui/association/ImportBookSourceViewModel.kt` 已删除

## UI层 - 换源
- [ ] `ui/book/changesource/` 整个目录已删除

## UI层 - 发现/探索
- [ ] `ui/main/explore/` 整个目录已删除
- [ ] `ui/book/explore/` 整个目录已删除

## UI层 - 书源登录
- [ ] `ui/login/` 目录中书源登录相关文件已删除（如整个目录仅用于书源登录则删除整个目录）

## 布局文件
- [ ] `activity_book_source.xml` 已删除
- [ ] `activity_book_source_edit.xml` 已删除
- [ ] `activity_source_debug.xml` 已删除
- [ ] `activity_source_login.xml` 已删除
- [ ] `activity_explore_show.xml` 已删除
- [ ] `activity_rule_sub.xml` 已删除
- [ ] `fragment_explore.xml` 已删除
- [ ] `fragment_web_view_login.xml` 已删除
- [ ] 所有书源相关 dialog 布局文件已删除
- [ ] 所有书源相关 item 布局文件已删除

## 菜单文件
- [ ] 所有书源相关 menu XML 文件已删除（约20个）

## 图标资源
- [ ] `ic_add_online.xml` 已删除
- [ ] `ic_check_source.xml` 已删除
- [ ] `ic_find_replace.xml` 已删除
- [ ] `ic_network_check.xml` 已删除
- [ ] `ic_web_outline.xml` 已删除
- [ ] `ic_cfg_source.xml` 已删除
- [ ] `ic_bottom_explore` 系列图标已删除

## 阅读引擎
- [ ] `ReadBook.kt` 中 `bookSource` 字段已移除
- [ ] `ReadBook.kt` 中 `upWebBook()` 已简化
- [ ] `ReadBook.kt` 中 `upToc()` 已移除 WebBook 调用
- [ ] `ReadBook.kt` 中 `download()`/`downloadAwait()` 已修改
- [ ] `ReadBook.kt` 不再导入 `BookSource` 和 `WebBook`
- [ ] `ReadManga.kt` 中 `bookSource` 已移除
- [ ] `AudioPlay.kt` 中 `bookSource` 已移除

## 书籍实体
- [ ] `BookExtensions.kt` 中 `getBookSource()` 已移除
- [ ] `BookExtensions.kt` 中 `isLocal` 逻辑已简化
- [ ] `BookType.kt` 中网络书源类型组合已移除
- [ ] `BookHelp.kt` 中 `BookSource` 参数方法已移除

## 书架管理
- [ ] `BookshelfManageActivity` 中网络书源操作已移除
- [ ] `BookshelfManageViewModel` 中网络书源操作已移除
- [ ] `MainViewModel` 中书源数据流已移除
- [ ] `BookshelfViewModel` 中书源数据流已移除

## 书籍信息
- [ ] `BookInfoActivity` 中网络书源操作已移除
- [ ] `BookInfoViewModel` 中网络书源操作已移除
- [ ] `AddToBookshelfDialog` 中网络书源逻辑已移除

## 搜索功能
- [ ] `SearchActivity` 已修改为仅本地搜索
- [ ] `SearchViewModel` 已修改为仅本地搜索
- [ ] `SearchScope` / `SearchScopeDialog` 已删除

## 备份恢复
- [ ] `Backup.kt` 中书源备份逻辑已移除
- [ ] `Restore.kt` 中书源恢复逻辑已移除
- [ ] `ImportOldData.kt` 中旧版书源导入逻辑已移除

## API/WebSocket
- [ ] `BookSourceController.kt` 已删除
- [ ] `BookSourceDebugWebSocket.kt` 已删除
- [ ] `BookSearchWebSocket.kt` 已删除
- [ ] `BookController.kt` 中书源相关接口已移除

## 导航
- [ ] 底部导航"发现"Tab已移除
- [ ] 导航菜单配置已更新

## 其他修改
- [ ] `ImageProvider.kt` 中 `BookSource` 引用已移除
- [ ] `AnalyzeRule.kt` 中 `BookSource` 引用已移除
- [ ] `AnalyzeUrl.kt` 保留HTTP请求能力
- [ ] `ChangeCoverViewModel.kt` 中书源封面获取逻辑已移除
- [ ] `WebViewModel.kt` 中书源操作已移除

## 清单文件
- [ ] `AndroidManifest.xml` 中已删除Activity/Service的注册已移除
- [ ] 不再需要的权限声明已移除

## 资源文件
- [ ] `strings.xml` 中书源相关字符串已删除
- [ ] `arrays.xml` 中书源相关数组已删除
- [ ] `ids.xml` 中书源相关ID已删除

## 数据库Schema
- [ ] 旧版本Schema文件已清理（或保留但不影响编译）

## 编译验证
- [ ] 项目编译通过，无编译错误
- [ ] 本地书籍导入功能正常
- [ ] WebDAV书籍同步功能正常
- [ ] 本地阅读功能正常
- [ ] RSS源功能正常
- [ ] 替换规则功能正常