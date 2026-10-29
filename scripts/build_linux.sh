#!/usr/bin/env bash
# 云端(Linux)构建 Debug APK —— 与 Windows 本地脚本同一套环境约束
#   mise 管理 JDK 17 + Gradle；Android SDK 固定 platform 36 + build-tools 36.0.0
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=env_common.sh
source "${SCRIPT_DIR}/env_common.sh"

die() { printf '\033[31mERROR: %s\033[0m\n' "$1"; exit 1; }

echo
echo "=========================================="
echo "      Legado 云端 Linux Debug Build"
echo "=========================================="
echo

echo "[1/7] mise + JDK ${EXPECT_JDK}"
ensure_java17 || die "mise 无法提供 JDK ${EXPECT_JDK}"
JAVA_SPEC=$("${JAVA_HOME}/bin/java" -XshowSettings:properties -version 2>&1 | grep 'java.specification.version' | awk -F'= ' '{print $2}')
[ "${JAVA_SPEC}" = "${EXPECT_JDK}" ] || die "JDK 版本为 ${JAVA_SPEC}，要求 ${EXPECT_JDK}"
echo "JDK ${JAVA_SPEC} OK (${JAVA_HOME})"

echo
echo "[2/7] Gradle"
ensure_gradle || die "mise 无法提供 Gradle"
GRADLE_VER=$(run_gradle --version 2>/dev/null | grep -E '^Gradle ' | awk '{print $2}')
echo "Gradle ${GRADLE_VER:-未知} OK (mise ${GRADLE_VERSION})"
[ -n "${GRADLE_VER}" ] || die "无法运行 mise 管理的 Gradle"
[ "$(printf '%s\n%s\n' "${EXPECT_GRADLE_MIN}" "${GRADLE_VER}" | sort -V | head -1)" = "${EXPECT_GRADLE_MIN}" ] \
  || die "Gradle ${GRADLE_VER} 低于 AGP ${EXPECT_AGP} 要求的 ${EXPECT_GRADLE_MIN}"

echo
echo "[3/7] Android SDK（固定 36）"
ensure_sdk || die "SDK 不满足 platform ${EXPECT_COMPILE_SDK} + build-tools ${EXPECT_BUILD_TOOLS}"
echo "SDK OK (${SDK_DIR}, platform ${EXPECT_COMPILE_SDK}, build-tools ${BT_VERSION})"

echo
echo "[4/7] 签名一致性校验"
[ -f "${PROJECT_DIR}/app/debug.keystore" ] || die "缺少 app/debug.keystore"
KEYTOOL=""
[ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/keytool" ] && KEYTOOL="${JAVA_HOME}/bin/keytool"
[ -z "${KEYTOOL}" ] && KEYTOOL="keytool"
KEY_SHA=$("${KEYTOOL}" -list -v -keystore "${PROJECT_DIR}/app/debug.keystore" -storepass android -alias androiddebugkey 2>/dev/null | grep 'SHA256:' | awk '{print $2}')
[ "${KEY_SHA}" = "${EXPECT_DEBUG_KEY_SHA}" ] || die "debug.keystore 指纹不一致：当前=${KEY_SHA:-读取失败}"
echo "签名 OK: ${KEY_SHA}"

echo
echo "[5/7] 与本地代码同步（versionCode = 10000 + git 提交数）"
if command -v git >/dev/null 2>&1; then
  if git_is_shallow; then
    echo "检测到浅克隆，正在补齐历史: git fetch --unshallow ..."
    git -C "${PROJECT_DIR}" fetch --unshallow origin || die "git fetch --unshallow 失败，无法保证 versionCode 与本地一致"
  fi
  if [ -z "$(git -C "${PROJECT_DIR}" status --porcelain 2>/dev/null)" ]; then
    echo "正在拉取 origin/master ..."
    git -C "${PROJECT_DIR}" pull --rebase origin master || die "git pull 失败，请先处理冲突"
  else
    echo "WARNING: 存在未提交改动，跳过自动拉取。"
  fi
  LOCAL_SHA=$(git -C "${PROJECT_DIR}" rev-parse --short HEAD)
  REMOTE_SHA=$(git -C "${PROJECT_DIR}" rev-parse --short origin/master)
  if [ "${LOCAL_SHA}" != "${REMOTE_SHA}" ]; then
    echo "WARNING: 本地 HEAD (${LOCAL_SHA}) 与 origin/master (${REMOTE_SHA}) 不一致，两端 versionCode 会不同。"
  fi
  LOCAL_COUNT=$(git_commit_count)
  echo "versionCode 预计: $((10000 + LOCAL_COUNT))（提交数 ${LOCAL_COUNT}，HEAD ${LOCAL_SHA}）"
else
  echo "WARNING: 未找到 git，跳过同步校验。"
fi

echo
echo "[6/7] 构建 Debug APK"
cd "${PROJECT_DIR}" || die "无法进入项目目录"
GRADLE_MEM="${GRADLE_MEM:--Xmx1280m -Xms256m -XX:MaxMetaspaceSize=384m}"
run_gradle --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs="-XX:+UseParallelGC ${GRADLE_MEM} -Dfile.encoding=UTF-8" \
  -Dkotlin.daemon.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=256m" \
  assembleAppDebug || die "Gradle 构建失败"

APK=$(ls -1 app/build/outputs/apk/app/debug/legado_app_*.apk 2>/dev/null | tail -1)
[ -n "${APK}" ] || die "未找到构建产物 app/build/outputs/apk/app/debug/legado_app_*.apk"

echo
echo "[7/7] 产物校验（签名 / versionCode）"
"${SDK_DIR}/build-tools/${BT_VERSION}/aapt2" dump badging "${APK}" | grep -E "^package:" || true
CERT=$("${SDK_DIR}/build-tools/${BT_VERSION}/apksigner" verify --print-certs "${APK}" 2>&1 | grep 'SHA-256')
echo "${CERT}"
if echo "${CERT}" | grep -qi "${EXPECT_DEBUG_KEY_SHA}"; then
  echo "产物签名与本地一致 OK"
else
  echo "WARNING: 产物签名指纹与期望值不一致。"
fi

echo
echo "=========================================="
echo "BUILD SUCCESS"
echo "APK: ${PROJECT_DIR}/${APK}"
echo "提示: 覆盖安装需 versionCode 不低于已装版本，且签名一致。"
echo "=========================================="
