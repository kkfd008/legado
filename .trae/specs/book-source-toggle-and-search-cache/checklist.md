# Checklist

## 显示书源开关
- [x] 1. `PreferKey` 新增 `showBookSource`
- [x] 2. `AppConfig` 新增 `showBookSource`（默认 false）
- [x] 3. `pref_config_other.xml` 新增开关项（默认 false）
- [x] 4. 中英文文案齐全（show_book_source / 显示书源）
- [x] 5. 我的页面按开关隐藏/显示「书源管理」
- [x] 6. 书架菜单按开关隐藏/显示「添加网址」
- [x] 7. `onResume` 刷新确保开关切换即时生效

## 搜索缓存修复
- [x] 8. 搜索结果回填到 `ReadBookViewModel.searchResultList`（非仅依赖 EventBus）
- [x] 9. 搜索后返回结果列表保留、不空白
- [x] 10. 更新关键字覆盖缓存
- [x] 11. 退出阅读页清理缓存（依赖 ViewModel 生命周期）

## 构建
- [x] 12. `assembleAppDebug` 构建通过