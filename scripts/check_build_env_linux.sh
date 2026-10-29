#!/usr/bin/env bash
# Legado 云端(Linux)构建环境检查 —— 与 Windows 本地检查项保持一致
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# ==== 与 Windows 脚本保持一致的期望值（改动项目版本时请同步修改四个脚本）====
EXPECT_JDK=17
EXPECT_COMPILE_SDK=36
EXPECT_GRADLE=8.13
EXPECT_AGP=8.13.2
EXPECT_KOTLIN=2.3.0
EXPECT_DEBUG_KEY_SHA="FA:BD:39:70:66:08:66:65:96:4A:A7:16:3E:E3:3D:DC:E6:7E:CA:12:4E:9F:5B:23:7B:16:BF:32:28:B1:13:2D"

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

echo "[1/7] Java / JDK (需与 Windows 本地一致: JDK ${EXPECT_JDK})"
# 优先使用 JAVA_HOME 的 java（Gradle 实际使用的编译器），避免 PATH 上的高版本 JDK 干扰
JAVA_BIN=""
[ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ] && JAVA_BIN="${JAVA_HOME}/bin/java"
[ -z "${JAVA_BIN}" ] && command -v java >/dev/null 2>&1 && JAVA_BIN="java"
if [ -z "${JAVA_BIN}" ]; then
  fail "未找到 java，请安装 JDK ${EXPECT_JDK}"
else
  JAVA_SPEC=$("${JAVA_BIN}" -XshowSettings:properties -version 2>&1 | grep 'java.specification.version' | awk -F'= ' '{print $2}')
  info "使用 java: ${JAVA_BIN}"
  info "Java 版本: $("${JAVA_BIN}" -version 2>&1 | head -1)"
  if [ "${JAVA_SPEC}" = "${EXPECT_JDK}" ]; then
    pass "JDK 版本正确: java.specification.version=${JAVA_SPEC}"
  else
    fail "Gradle 将使用的 JDK 为 ${JAVA_SPEC}，项目要求 JDK ${EXPECT_JDK}"
  fi
  if [ -x "${JAVA_HOME:-/nonexistent}/bin/javac" ] || command -v javac >/dev/null 2>&1; then
    pass "javac 可用（完整 JDK）"
  else
    warn "未找到 javac，当前可能是 JRE"
  fi
  if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/javac" ]; then
    pass "JAVA_HOME 指向有效 JDK: ${JAVA_HOME}"
  else
    warn "JAVA_HOME 未设置或无效（建议显式指定 JDK ${EXPECT_JDK} 目录）"
  fi
fi

echo
echo "[2/7] Android SDK (需与 Windows 本地一致: Platform ${EXPECT_COMPILE_SDK})"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "${SDK_DIR}" ] && [ -f "${PROJECT_DIR}/local.properties" ]; then
  SDK_DIR=$(grep -E '^sdk\.dir=' "${PROJECT_DIR}/local.properties" | head -1 | cut -d= -f2-)
fi
if [ -z "${SDK_DIR}" ]; then
  fail "未找到 Android SDK，请设置 ANDROID_HOME / ANDROID_SDK_ROOT 或配置 local.properties"
else
  info "Android SDK: ${SDK_DIR}"
  if [ -f "${SDK_DIR}/platforms/android-${EXPECT_COMPILE_SDK}/android.jar" ]; then
    pass "Android SDK Platform ${EXPECT_COMPILE_SDK} 已安装"
  else
    fail "缺少 ${SDK_DIR}/platforms/android-${EXPECT_COMPILE_SDK}/android.jar"
  fi
  BT_VERSION=""
  [ -x "${SDK_DIR}/build-tools/36.0.0/aapt2" ] && BT_VERSION="36.0.0"
  [ -z "${BT_VERSION}" ] && [ -x "${SDK_DIR}/build-tools/35.0.0/aapt2" ] && BT_VERSION="35.0.0"
  if [ -n "${BT_VERSION}" ]; then
    pass "Build Tools 可用: ${BT_VERSION}"
  else
    fail "未找到 build-tools 36.0.0 / 35.0.0"
  fi
  if [ -x "${SDK_DIR}/platform-tools/adb" ]; then
    pass "platform-tools (adb) 已安装"
  else
    warn "未找到 adb（不影响编译）"
  fi
fi

echo
echo "[3/7] Gradle Wrapper (需为 Gradle ${EXPECT_GRADLE})"
if [ -x "${PROJECT_DIR}/gradlew" ]; then
  pass "使用项目内 Gradle Wrapper (gradlew)，保证与 Windows 本地 Gradle ${EXPECT_GRADLE} 一致"
  WRAPPER_URL=$(grep -E '^distributionUrl=' "${PROJECT_DIR}/gradle/wrapper/gradle-wrapper.properties" | head -1)
  if echo "${WRAPPER_URL}" | grep -q "gradle-${EXPECT_GRADLE}-"; then
    pass "Wrapper 分发包版本为 Gradle ${EXPECT_GRADLE}"
  else
    warn "gradle-wrapper.properties 未指向 Gradle ${EXPECT_GRADLE}: ${WRAPPER_URL}"
  fi
else
  fail "缺少 gradlew（或不可执行），无法保证 Gradle 版本一致"
fi

echo
echo "[4/7] 项目版本配置（AGP / Kotlin / compileSdk / targetSdk）"
AGP_VER=$(grep -E '^agp = ' "${PROJECT_DIR}/gradle/libs.versions.toml" | head -1 | cut -d'"' -f2)
if [ "${AGP_VER}" = "${EXPECT_AGP}" ]; then
  pass "AGP 版本正确: ${AGP_VER}"
else
  fail "AGP 为 ${AGP_VER}，云端期望 ${EXPECT_AGP}"
fi
KOTLIN_VER=$(grep -E '^kotlin = ' "${PROJECT_DIR}/gradle/libs.versions.toml" | head -1 | cut -d'"' -f2)
if [ "${KOTLIN_VER}" = "${EXPECT_KOTLIN}" ]; then
  pass "Kotlin 版本正确: ${KOTLIN_VER}"
else
  warn "Kotlin 为 ${KOTLIN_VER}，期望 ${EXPECT_KOTLIN}"
fi
COMPILE_SDK=$(grep -E 'compile_sdk_version = ' "${PROJECT_DIR}/build.gradle" | head -1 | awk -F'= ' '{print $2}')
if [ "${COMPILE_SDK}" = "${EXPECT_COMPILE_SDK}" ]; then
  pass "compileSdk 正确: ${COMPILE_SDK}"
else
  fail "compileSdk 为 ${COMPILE_SDK}，期望 ${EXPECT_COMPILE_SDK}"
fi
TARGET_SDK=$(grep -E '^[[:space:]]*targetSdk ' "${PROJECT_DIR}/app/build.gradle" | head -1 | awk '{print $2}')
if [ "${TARGET_SDK}" = "${EXPECT_COMPILE_SDK}" ]; then
  pass "targetSdk 正确: ${TARGET_SDK}"
else
  warn "targetSdk 为 ${TARGET_SDK}，期望 ${EXPECT_COMPILE_SDK}"
fi

echo
echo "[5/7] 签名配置（与 Windows 本地共用仓库内 debug.keystore）"
if [ -f "${PROJECT_DIR}/app/debug.keystore" ]; then
  pass "签名文件存在: app/debug.keystore"
  KEYTOOL=""
  [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/keytool" ] && KEYTOOL="${JAVA_HOME}/bin/keytool"
  [ -z "${KEYTOOL}" ] && command -v keytool >/dev/null 2>&1 && KEYTOOL="keytool"
  if [ -n "${KEYTOOL}" ]; then
    KEY_SHA=$("${KEYTOOL}" -list -v -keystore "${PROJECT_DIR}/app/debug.keystore" -storepass android -alias androiddebugkey 2>/dev/null | grep 'SHA256:' | awk '{print $2}')
    if [ -n "${KEY_SHA}" ]; then
      info "debug.keystore SHA256: ${KEY_SHA}"
      if [ "${KEY_SHA}" = "${EXPECT_DEBUG_KEY_SHA}" ]; then
        pass "签名指纹与 Windows 本地一致（覆盖安装不会报签名冲突）"
      else
        fail "签名指纹与本地不一致！覆盖安装会失败"
      fi
    else
      warn "无法读取 debug.keystore 指纹"
    fi
  else
    warn "未找到 keytool，无法校验签名指纹"
  fi
else
  fail "缺少 app/debug.keystore，云端与本地签名将不一致"
fi

echo
echo "[6/7] 代码同步状态（决定 versionCode 是否一致）"
if ! command -v git >/dev/null 2>&1; then
  warn "未找到 git，无法校验 versionCode 基数"
else
  SHALLOW=$(git -C "${PROJECT_DIR}" rev-parse --is-shallow-repository 2>/dev/null)
  if [ "${SHALLOW}" = "true" ]; then
    fail "当前为浅克隆仓库，git 提交数与 Windows 完整克隆不一致，versionCode 会不同。请执行: git fetch --unshallow"
  else
    pass "仓库为完整克隆（git 提交数可作为 versionCode 基数）"
  fi
  git -C "${PROJECT_DIR}" fetch origin master --quiet >/dev/null 2>&1
  LOCAL_COUNT=$(git -C "${PROJECT_DIR}" rev-list HEAD --count 2>/dev/null)
  LOCAL_SHA=$(git -C "${PROJECT_DIR}" rev-parse --short HEAD 2>/dev/null)
  REMOTE_SHA=$(git -C "${PROJECT_DIR}" rev-parse --short origin/master 2>/dev/null)
  if [ -n "${LOCAL_COUNT}" ]; then
    info "本地提交数: ${LOCAL_COUNT}，预计 versionCode=$((10000 + LOCAL_COUNT))"
    info "本地 HEAD: ${LOCAL_SHA} / origin/master: ${REMOTE_SHA}"
    if [ "${LOCAL_SHA}" = "${REMOTE_SHA}" ]; then
      pass "本地与 origin/master 在同一提交，versionCode 与 Windows 本地一致"
    else
      fail "本地 HEAD (${LOCAL_SHA}) 与 origin/master (${REMOTE_SHA}) 不一致，两端 versionCode 会不同，请先 pull/push 对齐"
    fi
  else
    warn "无法读取本地提交数"
  fi
  if [ -n "$(git -C "${PROJECT_DIR}" status --porcelain 2>/dev/null)" ]; then
    warn "存在未提交改动；同一 versionCode 下代码可能不一致"
  else
    pass "工作区干净"
  fi
fi

echo
echo "[7/7] Gradle 缓存与写入权限"
CACHE_DIR="${GRADLE_USER_HOME:-${HOME}/.gradle}"
mkdir -p "${CACHE_DIR}" 2>/dev/null
if [ -w "${CACHE_DIR}" ]; then
  pass "Gradle 缓存目录可写: ${CACHE_DIR}"
else
  fail "Gradle 缓存目录不可写: ${CACHE_DIR}"
fi
if [ -w "${PROJECT_DIR}" ]; then
  pass "项目目录可写"
else
  fail "项目目录不可写，无法生成构建产物"
fi

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
  echo "结论: 存在阻断项，请先修复 FAIL 再构建。"
  exit 1
elif [ "${WARN}" -gt 0 ]; then
  echo "结论: 无阻断项，请关注 WARN 项。"
  exit 0
else
  echo "结论: 环境与 Windows 本地一致，可以构建。"
  exit 0
fi
