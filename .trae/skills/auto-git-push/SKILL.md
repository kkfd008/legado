---
name: "auto-git-push"
description: "每次完成需求后自动将代码推送到远程仓库。Invoke when user completes a task or requirement, or after successful debug APK compilation."
---

# Auto Git Push

每次完成用户需求后，自动执行以下步骤将代码推送到远程仓库。

## 执行步骤

### 1. 检查 Git 状态

```bash
git status
```

确认是否有未提交的变更。

### 2. 暂存所有变更

```bash
git add -A
```

### 3. 生成提交信息并提交

根据本次完成的需求内容，生成简洁的中文提交信息，格式为：

```
<type>: <简短描述>
```

type 类型：
- `feat`: 新功能
- `fix`: 修复 bug
- `refactor`: 重构
- `style`: UI 样式调整
- `chore`: 构建/配置相关

示例：`feat: 标签选择弹窗添加确认/取消按钮`

### 4. 推送到远程仓库

```bash
git push
```

如果推送失败（如 token 权限不足），告知用户具体错误原因。

## 注意事项

- 如果 `git status` 显示无变更，跳过提交和推送，直接告知用户"无变更需要推送"
- 如果推送被远程拒绝（如 token 缺少 `workflow` 权限），提示用户更新 GitHub Personal Access Token 权限
- 不要提交 `.env`、`credentials.json` 等敏感文件
- 如果远程有新的提交，先执行 `git pull --rebase` 再推送