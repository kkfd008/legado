# Tasks

## Task 1: 新增「显示书源」偏好开关
- [x] 1.1 `PreferKey.kt` 新增 `showBookSource` 常量
- [x] 1.2 `AppConfig.kt` 新增 `showBookSource` 属性（默认 false）
- [x] 1.3 `pref_config_other.xml` 新增 `SwitchPreference`（key=`showBookSource`，默认 false）
- [x] 1.4 `strings.xml` / `values-zh/strings.xml` 新增 `show_book_source` 文案

## Task 2: 按开关控制书源相关入口显隐
- [x] 2.1 `MyFragment` 根据 `showBookSource` 隐藏「书源管理」
- [x] 2.2 `BaseBookshelfFragment` 根据 `showBookSource` 隐藏「添加网址」
- [x] 2.3 `onResume` 中刷新显隐状态，支持开关即时生效

## Task 3: 修复阅读内容搜索缓存 bug
- [x] 3.1 `ReadBookActivity` 搜索结果回调回填 `viewModel.searchResultList`

## Task 4: 构建验证
- [x] 4.1 `assembleAppDebug` 构建成功，产出 debug APK

# Task Dependencies
- Task 2 依赖 Task 1
- Task 3 独立
- Task 4 依赖 Task 1、2、3
- Task 1 内 1.1~1.4 可并行