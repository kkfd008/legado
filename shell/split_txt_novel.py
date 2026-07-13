#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
TXT小说章节分割工具
基于 Legado 的 txtTocRule 规则，将 TXT 小说按章节分割成多个文件。

用法：
    python split_txt_novel.py <小说文件.txt>

输出：
    在小说文件同级目录下创建以小说文件名命名的文件夹，
    每个章节保存为一个独立的 .txt 文件。
"""

import os
import re
import sys
import argparse
from pathlib import Path

# ============================================================
# 所有 TXT 目录规则（来自 Legado 默认规则）
# ============================================================
TXT_TOC_RULES = [
    {
        "id": -1, "enable": True, "name": "目录(去空白)",
        "rule": r'(?<=[　\s])(?:序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|第\s{0,4}[\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+?\s{0,4}(?:章|节(?!课)|卷|集(?![合和]))).{0,30}$'
    },
    {
        "id": -2, "enable": True, "name": "目录",
        "rule": r'^[ 　\t]{0,4}(?:序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|第\s{0,4}[\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+?\s{0,4}(?:章|节(?!课)|卷|集(?![合和])|部(?![分赛游])|篇(?!张))).{0,30}$'
    },
    {
        "id": -3, "enable": False, "name": "目录(匹配简介)",
        "rule": r'(?<=[　\s])(?:(?:内容|文章)?简介|文案|前言|序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|第\s{0,4}[\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+?\s{0,4}(?:章|节(?!课)|卷|集(?![合和])|部(?![分赛游])|回(?![合来事去])|场(?![和合比电是])|篇(?!张))).{0,30}$'
    },
    {
        "id": -4, "enable": False, "name": "目录(古典、轻小说备用)",
        "rule": r'^[ 　\t]{0,4}(?:序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|第\s{0,4}[\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+?\s{0,4}(?:章|节(?!课)|卷|集(?![合和])|部(?![分赛游])|回(?![合来事去])|场(?![和合比电是])|话|篇(?!张))).{0,30}$'
    },
    {
        "id": -5, "enable": False, "name": "数字(纯数字标题)",
        "rule": r'(?<=[　\s])\d+\.?[ 　\t]{0,4}$'
    },
    {
        "id": -6, "enable": False, "name": "大写数字(纯数字标题)",
        "rule": r'(?<=[　\s])[零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,12}[ 　\t]{0,4}$'
    },
    {
        "id": -7, "enable": False, "name": "数字混合(纯数字标题)",
        "rule": r'(?<=[　\s])[零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟\d]{1,12}[ 　\t]{0,4}$'
    },
    {
        "id": -8, "enable": True, "name": "数字 分隔符 标题名称",
        "rule": r'^[ 　\t]{0,4}\d{1,5}[:：,.， 、_—\-].{1,30}$'
    },
    {
        "id": -9, "enable": True, "name": "大写数字 分隔符 标题名称",
        "rule": r'^[ 　\t]{0,4}(?:序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|[零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}章?)[ 、_—\-].{1,30}$'
    },
    {
        "id": -10, "enable": False, "name": "数字混合 分隔符 标题名称",
        "rule": r'^[ 　\t]{0,4}(?:序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|[零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}章?[ 、_—\-]|\d{1,5}章?[:：,.， 、_—\-]).{0,30}$'
    },
    {
        "id": -11, "enable": True, "name": "正文 标题/序号",
        "rule": r'^[ 　\t]{0,4}正文[ 　]{1,4}.{0,20}$'
    },
    {
        "id": -12, "enable": True, "name": "Chapter/Section/Part/Episode 序号 标题",
        "rule": r'^[ 　\t]{0,4}(?:[Cc]hapter|[Ss]ection|[Pp]art|ＰＡＲＴ|[Nn][oO][.、]|[Ee]pisode|(?:内容|文章)?简介|文案|前言|序章|楔子|正文(?!完|结)|终章|后记|尾声|番外)\s{0,4}\d{1,4}.{0,30}$'
    },
    {
        "id": -13, "enable": False, "name": "Chapter(去简介)",
        "rule": r'^[ 　\t]{0,4}(?:[Cc]hapter|[Ss]ection|[Pp]art|ＰＡＲＴ|[Nn][Oo]\.|[Ee]pisode)\s{0,4}\d{1,4}.{0,30}$'
    },
    {
        "id": -14, "enable": True, "name": "特殊符号 序号 标题",
        "rule": r'(?<=[\s　])[【〔〖「『〈［\[](?:第|[Cc]hapter)[\d零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,10}[章节].{0,20}$'
    },
    {
        "id": -15, "enable": False, "name": "特殊符号 标题(成对)",
        "rule": r'(?<=[\s　]{0,4})(?:[\[〈「『〖〔《（【\(].{1,30}[\)】）》〕〗』」〉\]]?|(?:内容|文章)?简介|文案|前言|序章|楔子|正文(?!完|结)|终章|后记|尾声|番外)[ 　]{0,4}$'
    },
    {
        "id": -16, "enable": True, "name": "特殊符号 标题(单个)",
        "rule": r'(?<=[\s　]{0,4})(?:[☆★✦✧].{1,30}|(?:内容|文章)?简介|文案|前言|序章|楔子|正文(?!完|结)|终章|后记|尾声|番外)[ 　]{0,4}$'
    },
    {
        "id": -17, "enable": True, "name": "章/卷 序号 标题",
        "rule": r'^[ \t　]{0,4}(?:(?:内容|文章)?简介|文案|前言|序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|[卷章][\d零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8})[ 　]{0,4}.{0,30}$'
    },
    {
        "id": -18, "enable": False, "name": "顶格标题",
        "rule": r'^\S.{1,20}$'
    },
    {
        "id": -19, "enable": False, "name": "双标题(前向)",
        "rule": r'(?m)(?<=[ \t　]{0,4})第[\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}章.{0,30}$(?=[\s　]{0,8}第[\d零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}章)'
    },
    {
        "id": -20, "enable": False, "name": "双标题(后向)",
        "rule": r'(?m)(?<=[ \t　]{0,4}第[\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}章.{0,30}$[\s　]{0,8})第[\d零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}章.{0,30}$'
    },
    {
        "id": -21, "enable": True, "name": "书名 括号 序号",
        "rule": r'^[一-龥]{1,20}[ 　\t]{0,4}[(（][\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}[)）][ 　\t]{0,4}$'
    },
    {
        "id": -22, "enable": True, "name": "书名 序号",
        "rule": r'^[一-龥]{1,20}[ 　\t]{0,4}[\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}[ 　\t]{0,4}$'
    },
    {
        "id": -23, "enable": False, "name": "特定字符 标题 特定符号",
        "rule": r'(?<=\={3,6}).{1,40}?(?=\=)'
    },
    {
        "id": -24, "enable": True, "name": "字数分割 分节阅读",
        "rule": r'(?<=[ 　\t]{0,4})(?:.{0,15}分[页节章段]阅读[-_ ]|第\s{0,4}[\d零一二两三四五六七八九十百千万]{1,6}\s{0,4}[页节]).{0,30}$'
    },
    {
        "id": -25, "enable": False, "name": "通用规则",
        "rule": r'(?im)^.{0,6}(?:[引楔]子|正文(?!完|结)|[引序前]言|[序终]章|扉页|[上中下][部篇卷]|卷首语|后记|尾声|番外|={2,4}|第\s{0,4}[\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+?\s{0,4}(?:章|节(?!课)|卷|页[、 　]|集(?![合和])|部(?![分是门落])|篇(?!张))).{0,40}$|^.{0,6}[\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟a-z]{1,8}[、. 　].{0,20}$'
    },
]


def get_best_pattern(content: str, sample_size: int = 100000):
    """
    遍历所有启用的规则，找出匹配章节数最多的规则。
    与 Legado TextFile.getTocRule() 逻辑一致。
    """
    sample = content[:sample_size]
    rules = [r for r in TXT_TOC_RULES if r["enable"]]
    rules.reverse()

    max_num = 1
    best_pattern = None

    for toc_rule in rules:
        try:
            pattern = re.compile(toc_rule["rule"], re.MULTILINE)
        except re.error as e:
            print(f"  规则 [{toc_rule['name']}] 正则语法错误，跳过: {e}")
            continue

        matches = list(pattern.finditer(sample))
        start = 0
        num = 0
        for m in matches:
            if start == 0 or m.start() - start > 1000:
                num += 1
                start = m.end()

        if num >= max_num:
            max_num = num
            best_pattern = pattern

    return best_pattern


def split_chapters(content: str, pattern):
    """
    使用正则 pattern 分割章节。
    返回 [(章节标题, 章节内容), ...] 列表。
    """
    chapters = []
    matches = list(pattern.finditer(content))

    if not matches:
        chapters.append(("全文", content.strip()))
        return chapters

    first_match = matches[0]
    if first_match.start() > 0:
        pre_content = content[:first_match.start()].strip()
        if pre_content:
            chapters.append(("前言", pre_content))

    for i, match in enumerate(matches):
        title = match.group().strip()
        start = match.start()
        if i + 1 < len(matches):
            end = matches[i + 1].start()
        else:
            end = len(content)
        chapter_content = content[start:end]
        body = chapter_content[len(match.group()):].strip()
        chapters.append((title, body))

    return chapters


def sanitize_filename(name: str) -> str:
    """清理文件名中的非法字符"""
    illegal_chars = r'[<>:"/\\|?*\n\r]'
    name = re.sub(illegal_chars, '_', name)
    name = name.strip('. ')
    if len(name) > 100:
        name = name[:80] + '...' + name[-17:]
    if not name:
        name = "无标题"
    return name


def split_by_fixed_length(content: str, max_len: int = 10000) -> list:
    """按固定长度分割章节（兜底方案）"""
    chapters = []
    lines = content.split('\n')
    current_lines = []
    current_len = 0
    chapter_num = 1

    for line in lines:
        current_lines.append(line)
        current_len += len(line) + 1
        if current_len >= max_len:
            title = f"第{chapter_num}章"
            body = '\n'.join(current_lines)
            chapters.append((title, body))
            current_lines = []
            current_len = 0
            chapter_num += 1

    if current_lines:
        title = f"第{chapter_num}章"
        body = '\n'.join(current_lines)
        chapters.append((title, body))

    return chapters


def split_txt_novel(filepath: str, output_dir: str = None):
    """
    将 TXT 小说分割成多个章节文件。
    """
    filepath = Path(filepath).resolve()
    if not filepath.exists():
        print(f"错误：文件不存在 - {filepath}")
        return

    novel_name = filepath.stem

    if output_dir is None:
        output_dir = filepath.parent / novel_name
    else:
        output_dir = Path(output_dir)

    print(f"读取文件: {filepath}")
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
    except UnicodeDecodeError:
        try:
            with open(filepath, 'r', encoding='gbk') as f:
                content = f.read()
            print("  使用 GBK 编码读取")
        except UnicodeDecodeError:
            with open(filepath, 'r', encoding='gb18030') as f:
                content = f.read()
            print("  使用 GB18030 编码读取")

    print(f"  文件大小: {len(content)} 字符")

    if content and content[0] == '\ufeff':
        content = content[1:]

    print("正在自动选择最佳目录规则...")
    pattern = get_best_pattern(content)
    if pattern is None:
        print("  未找到合适的规则，使用默认分章（按固定长度分割）")
        chapters = split_by_fixed_length(content)
        rule_name = "固定长度分割"
    else:
        rule_name = "未知"
        for rule in TXT_TOC_RULES:
            if rule["enable"] and rule["rule"] == pattern.pattern:
                rule_name = rule["name"]
                break
        print(f"  选中规则: {rule_name}")
        chapters = split_chapters(content, pattern)

    print(f"  共识别 {len(chapters)} 个章节")

    output_dir.mkdir(parents=True, exist_ok=True)
    print(f"输出目录: {output_dir}")

    print("\n正在生成章节文件...")
    for i, (title, body) in enumerate(chapters):
        safe_title = sanitize_filename(title)
        filename = f"{i + 1:04d}_{safe_title}.txt"
        filepath_out = output_dir / filename
        full_content = f"{title}\n\n{body}"

        with open(filepath_out, 'w', encoding='utf-8') as f:
            f.write(full_content)

        if (i + 1) % 50 == 0 or i == len(chapters) - 1:
            print(f"  已生成 {i + 1}/{len(chapters)}: {filename}")

    print(f"\n完成！共生成 {len(chapters)} 个章节文件到：")
    print(f"  {output_dir}")

    summary_path = output_dir / "_章节列表.txt"
    with open(summary_path, 'w', encoding='utf-8') as f:
        f.write(f"小说: {novel_name}\n")
        f.write(f"总章节数: {len(chapters)}\n")
        f.write(f"总字符数: {len(content)}\n")
        f.write(f"匹配规则: {rule_name}\n")
        f.write("=" * 60 + "\n\n")
        for i, (title, body) in enumerate(chapters):
            f.write(f"{i + 1:04d}. {title}  ({len(body)} 字)\n")
    print(f"  章节列表已保存到: {summary_path}")


def list_rules():
    """列出所有规则及其启用状态"""
    print("=" * 80)
    print(f"{'ID':>5}  {'启用':>4}  {'名称':<30}  规则预览")
    print("=" * 80)
    for rule in TXT_TOC_RULES:
        enabled = "是" if rule["enable"] else "否"
        rule_preview = rule["rule"][:45] + "..." if len(rule["rule"]) > 45 else rule["rule"]
        print(f"{rule['id']:>5}  {enabled:>4}  {rule['name']:<30}  {rule_preview}")
    print("=" * 80)


def main():
    parser = argparse.ArgumentParser(
        description="TXT 小说章节分割工具（基于 Legado 目录规则）",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python split_txt_novel.py 小说.txt
  python split_txt_novel.py 小说.txt -o ./output
  python split_txt_novel.py --list-rules
        """
    )
    parser.add_argument("file", nargs="?", help="TXT 小说文件路径")
    parser.add_argument("-o", "--output", help="输出目录（默认在小说文件旁创建同名目录）")
    parser.add_argument("--list-rules", action="store_true", help="列出所有内置规则")
    parser.add_argument("--rule", type=int, help="指定使用某个规则 ID（覆盖自动选择）")
    parser.add_argument("--rule-name", type=str, help="指定使用某个规则名称（覆盖自动选择）")

    args = parser.parse_args()

    if args.list_rules:
        list_rules()
        return

    if not args.file:
        parser.print_help()
        return

    if args.rule is not None or args.rule_name is not None:
        for rule in TXT_TOC_RULES:
            if args.rule is not None and rule["id"] == args.rule:
                rule["enable"] = True
            elif args.rule_name is not None and rule["name"] == args.rule_name:
                rule["enable"] = True
            else:
                rule["enable"] = False

    split_txt_novel(args.file, args.output)


if __name__ == "__main__":
    main()