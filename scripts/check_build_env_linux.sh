#!/usr/bin/env bash
# 云端(Linux)构建环境检查 —— 与 Windows 本地脚本同一套规则
#   mise 管理 JDK 17 + Gradle；Android SDK 固定 platform 36 + build-tools 36.0.0
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=env_common.sh
source "${SCRIPT_DIR}/env_common.sh"

PASS=0
WARN=0
FAIL=0
REPORT="${PROJECT_DIR}/build_env_check_report.txt"

pass() { PASS=$((PASS + 1)); printf '\033[32m[PASS] %s\033[0m\n' "$1"; echo "[PASS] $1" >>"$REPORT"; }
warn() { WARN=$((WARN + 1)); printf '\033[33m[WARN] %s\033[0m\n' "$1"; echo "[WARN] $1" >>"$REPORT"; }
fail() { FAIL=$((FAIL + 1)); printf '\033[31m[FAIL] %s\033[0m\n' "$1"; echo "[FAIL] $1" >>"$REPORT"; }
info() { echo "[INFO] $1"; echo "[INFO] $1" >>"$REPORT"; }

{
  echo "Legado Linux Build Environment Check"
  echo "Date: $(date)"
  echo "Project: ${PROJECT_DIR}"
  echo
} >"$REPORT"

echo "=============================================="
echo "  Legado - 云端 Linux 构建环境检查"
echo "=============================================="
echo "Project: ${PROJECT_DIR}"
echo "Report:  ${REPORT}"
echo

echo "[1/8] mise (统一管理 JDK 与 Gradle)"
if ensure_mise; then
  pass "mise 可用: ${MISE_BIN} ($("${MISE_BIN}" --version 2>/dev/null | awk '{print $1}'))"
else
  fail "mise 不可用，无法统一 JDK / Gradle 版本"
fi

echo
echo "[2/8] JDK (mise 提供 JDK ${EXPECT_JDK})"
if ensure_java17; then
  JAVA_SPEC=$("${JAVA_HOME}/bin/java" -XshowSettings:properties -version 2>&1 | grep 'java.specification.version' | awk -F'= ' '{print $2}')
  info "java: $( "${JAVA_HOME}/bin/java" -version 2>&1 | head -1 )"
  if [ "${JAVA_SPEC}" = "${EXPECT_JDK}" ]; then
    pass "JDK 版本正确: ${JAVA_SPEC}（mise 安装路径 ${JAVA_HOME}）"
  else
    fail "mise 提供的 JDK 为 ${JAVA_SPEC}，要求 ${EXPECT_JDK}"
  fi
else
  fail "mise 未能提供 JDK ${EXPECT_JDK}"
fi

echo
echo "[3/8] Gradle (mise 指定版本优先，缺失则 mise 安装；要求 >= ${EXPECT_GRADLE_MIN})"
if ensure_gradle; then
  GRADLE_VER=$(run_gradle --version 2>/dev/null | grep -E '^Gradle ' | awk '{print $2}')
  if [ -n "${GRADLE_VER}" ] && [ "$(printf '%s\n%s\n' "${EXPECT_GRADLE_MIN}" "${GRADLE_VER}" | sort -V | head -1)" = "${EXPECT_GRADLE_MIN}" ]; then
    pass "Gradle ${GRADLE_VER} 可用，且满足 AGP ${EXPECT_AGP} 要求（>= ${EXPECT_GRADLE_MIN}）"
  else
    fail "Gradle 版本为 ${GRADLE_VER:-未知}，低于 AGP ${EXPECT_AGP} 要求的 ${EXPECT_GRADLE_MIN}"
  fi
  WRAPPER_URL=$(grep -E '^distributionUrl=' "${PROJECT_DIR}/gradle/wrapper/gradle-wrapper.properties" | head -1)
  info "项目 wrapper: ${WRAPPER_URL##*gradle-}"
else
  fail "mise 未能提供 Gradle（${MISE_BIN}）"
fi

echo
echo "[4/8] Android SDK（固定 platform ${EXPECT_COMPILE_SDK} + build-tools ${EXPECT_BUILD_TOOLS}）"
if ensure_sdk; then
  pass "platform android-${EXPECT_COMPILE_SDK} 已安装: ${SDK_DIR}/platforms/android-${EXPECT_COMPILE_SDK}"
  pass "build-tools ${EXPECT_BUILD_TOOLS} 已安装: ${SDK_DIR}/build-tools/${EXPECT_BUILD_TOOLS}"
  if [ -x "${SDK_DIR}/platform-tools/adb" ]; then
    pass "platform-tools (adb) 已安装"
  else
    warn "未找到 adb（不影响编译）"
  fi
else
  fail "Android SDK 不满足固定版本要求（platform ${EXPECT_COMPILE_SDK} + build-tools ${EXPECT_BUILD_TOOLS}）"
fi

echo
echo "[5/8] 项目版本配置（AGP / Kotlin / compileSdk / targetSdk）"
AGP_VER=$(grep -E '^agp = ' "${PROJECT_DIR}/gradle/libs.versions.toml" | head -1 | cut -d'"' -f2)
[ "${AGP_VER}" = "${EXPECT_AGP}" ] && pass "AGP 版本正确: ${AGP_VER}" || fail "AGP 为 ${AGP_VER}，期望 ${EXPECT_AGP}"
KOTLIN_VER=$(grep -E '^kotlin = ' "${PROJECT_DIR}/gradle/libs.versions.toml" | head -1 | cut -d'"' -f2)
[ "${KOTLIN_VER}" = "${EXPECT_KOTLIN}" ] && pass "Kotlin 版本正确: ${KOTLIN_VER}" || warn "Kotlin 为 ${KOTLIN_VER}，期望 ${EXPECT_KOTLIN}"
COMPILE_SDK=$(grep -E 'compile_sdk_version = ' "${PROJECT_DIR}/build.gradle" | head -1 | awk -F'= ' '{print $2}')
[ "${COMPILE_SDK}" = "${EXPECT_COMPILE_SDK}" ] && pass "compileSdk 正确: ${COMPILE_SDK}" || fail "compileSdk 为 ${COMPILE_SDK}，期望 ${EXPECT_COMPILE_SDK}"
TARGET_SDK=$(grep -E '^[[:space:]]*targetSdk ' "${PROJECT_DIR}/app/build.gradle" | head -1 | awk '{print $2}')
[ "${TARGET_SDK}" = "${EXPECT_COMPILE_SDK}" ] && pass "targetSdk 正确: ${TARGET_SDK}" || warn "targetSdk 为 ${TARGET_SDK}，期望 ${EXPECT_COMPILE_SDK}"

echo
echo "[6/8] 签名（两端共用仓库内 app/debug.keystore）"
if [ -f "${PROJECT_DIR}/app/debug.keystore" ]; then
  pass "签名文件存在: app/debug.keystore"
  KEYTOOL=""
  [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/keytool" ] && KEYTOOL="${JAVA_HOME}/bin/keytool"
  [ -z "${KEYTOOL}" ] && command -v keytool >/dev/null 2>&1 && KEYTOOL="keytool"
  if [ -n "${KEYTOOL}" ]; then
    KEY_SHA=$("${KEYTOOL}" -list -v -keystore "${PROJECT_DIR}/app/debug.keystore" -storepass android -alias androiddebugkey 2>/dev/null | grep 'SHA256:' | awk '{print $2}')
    info "debug.keystore SHA256: ${KEY_SHA:-读取失败}"
    if [ "${KEY_SHA}" = "${EXPECT_DEBUG_KEY_SHA}" ]; then
      pass "签名指纹与 Windows 本地一致（覆盖安装不会报签名冲突）"
    else
      fail "签名指纹与本地不一致，覆盖安装会失败"
    fi
  else
    warn "未找到 keytool，无法校验签名"
  fi
else
  fail "缺少 app/debug.keystore"
fi

echo
echo "[7/8] 代码同步（versionCode = 10000 + git 提交数）"
if command -v git >/dev/null 2>&1; then
  if git_is_shallow; then
    fail "浅克隆仓库，提交数与 Windows 完整克隆不同，versionCode 会不一致。请执行: git fetch --unshallow"
  else
    pass "完整克隆，提交数可作 versionCode 基数"
  fi
  git -C "${PROJECT_DIR}" fetch origin master --quiet >/dev/null 2>&1
  LOCAL_SHA=$(git -C "${PROJECT_DIR}" rev-parse --short HEAD 2>/dev/null)
  REMOTE_SHA=$(git -C "${PROJECT_DIR}" rev-parse --short origin/master 2>/dev/null)
  LOCAL_COUNT=$(git_commit_count)
  info "本地提交数: ${LOCAL_COUNT}，预计 versionCode=$((10000 + ${LOCAL_COUNT:-0}))"
  info "本地 HEAD: ${LOCAL_SHA} / origin/master: ${REMOTE_SHA}"
  [ "${LOCAL_SHA}" = "${REMOTE_SHA}" ] && pass "与 origin/master 同提交，versionCode 与本地一致" || fail "与 origin/master 不同提交，两端 versionCode 会不同"
  [ -n "$(git -C "${PROJECT_DIR}" status --porcelain 2>/dev/null)" ] && warn "存在未提交改动，同 versionCode 下代码可能不一致" || pass "工作区干净"
else
  warn "未找到 git，跳过 versionCode 校验"
fi

echo
echo "[8/8] Gradle 缓存与写入权限"
CACHE_DIR="${GRADLE_USER_HOME:-${HOME}/.gradle}"
mkdir -p "${CACHE_DIR}" 2>/dev/null
[ -w "${CACHE_DIR}" ] && pass "Gradle 缓存目录可写: ${CACHE_DIR}" || fail "Gradle 缓存目录不可写: ${CACHE_DIR}"
[ -w "${PROJECT_DIR}" ] && pass "项目目录可写" || fail "项目目录不可写"

echo
echo "=============================================="
echo "  检查汇总"
echo "=============================================="
echo "PASS: ${PASS}"
echo "WARN: ${WARN}"
echo "FAIL: ${FAIL}"
echo "Summary: PASS=${PASS} WARN=${WARN} FAIL=${FAIL}" >>"$REPORT"
echo "完整报告: ${REPORT}"
echo
if [ "${FAIL}" -gt 0 ]; then
  echo "结论: 存在阻断项，请先修复 FAIL。"
  exit 1
elif [ "${WARN}" -gt 0 ]; then
  echo "结论: 无阻断项，请关注 WARN。"
else
  echo "结论: 环境与 Windows 本地一致，可以构建。"
fi
