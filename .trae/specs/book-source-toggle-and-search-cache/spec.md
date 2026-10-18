# 显示书源开关与阅读搜索缓存修复 Spec

## Why
1. 用户希望在「其他设置」中增加「显示书源」开关（默认关闭），关闭时隐藏「我的页面-书源管理」和「书架菜单-添加网址」入口，停用书源相关功能。
2. 阅读页内容关键字搜索存在缓存 bug：首次搜索出结果列表，点击进入阅读点后返回结果列表变成空白（无缓存）。需要按书保留搜索缓存、更新关键字时覆盖、退出阅读页时清理。

## What Changes
- 新增「显示书源」偏好开关：`PreferKey.showBookSource` + `AppConfig.showBookSource` + `pref_config_other.xml` 开关项 + 中英文文案
- 「我的页面」根据开关隐藏「书源管理」入口
- 「书架菜单」根据开关隐藏「添加网址」入口
- 修复阅读页内容关键字搜索缓存：搜索结果回填到 `ReadBookViewModel.searchResultList`

## Impact
- Affected specs: 无（新创建）
- Affected code:
  - `app/src/main/java/io/legado/app/constant/PreferKey.kt` - 新增 `showBookSource`
  - `app/src/main/java/io/legado/app/help/config/AppConfig.kt` - 新增 `showBookSource`
  - `app/src/main/res/xml/pref_config_other.xml` - 新增开关项
  - `app/src/main/res/values/strings.xml` - 新增英文文案
  - `app/src/main/res/values-zh/strings.xml` - 新增中文文案
  - `app/src/main/java/io/legado/app/ui/main/my/MyFragment.kt` - 隐藏「书源管理」
  - `app/src/main/java/io/legado/app/ui/main/bookshelf/BaseBookshelfFragment.kt` - 隐藏「添加网址」
  - `app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt` - 修复搜索缓存

## ADDED Requirements

### Requirement: 显示书源开关
系统 SHALL 在「其他设置」中提供「显示书源」开关，默认关闭；关闭时「我的页面-书源管理」和「书架菜单-添加网址」不显示。

#### Scenario: 默认关闭状态
- **WHEN** 用户首次安装或未手动开启开关
- **THEN** 我的页面不显示「书源管理」，书架菜单不显示「添加网址」

#### Scenario: 开启后显示
- **WHEN** 用户在「其他设置」开启「显示书源」
- **THEN** 我的页面显示「书源管理」，书架菜单显示「添加网址」

### Requirement: 阅读内容搜索缓存
系统 SHALL 按书籍保留内容关键字搜索缓存；更新关键字时覆盖缓存；退出阅读页时清理缓存。

#### Scenario: 搜索后返回结果列表
- **WHEN** 用户搜索关键字、点击结果进入阅读点后返回
- **THEN** 搜索结果列表仍保留，不出现空白

#### Scenario: 更新关键字
- **WHEN** 用户在保留缓存的基础上用新关键字搜索
- **THEN** 新搜索结果覆盖旧缓存

#### Scenario: 退出阅读页
- **WHEN** 用户退出当前书籍阅读页
- **THEN** 该书籍的搜索缓存被清理

## 代码审查结论

- 「显示书源」实现正确、最小化；通过 `onResume` 刷新实现开关即时生效。
- 搜索缓存 bug 根因定位准确：`LiveEventBus` 非粘性事件在 `ReadBookActivity` 处于 STOPPED 状态时 `post`，观察者收不到，缓存无法回填。修复为在 `registerForActivityResult` 回调中直接回填 `viewModel.searchResultList`。
- 遗留建议（非阻塞）：`EventBus.SEARCH_RESULT` 的 `postEvent` 与 `observeEvent` 在当前修复后已变为冗余，可保留但不影响功能；「显示书源」当前仅隐藏两个明确入口，其余书源相关入口（如书架菜单「添加远程书籍」）如需一并隐藏，需另行确认范围。