#!/usr/bin/env bash
# Legado 云端(Linux)构建 Debug APK —— 与 Windows 本地脚本使用同一套版本/签名约束
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# ==== 与 Windows 脚本保持一致的期望值 ====
EXPECT_JDK=17
EXPECT_COMPILE_SDK=36
EXPECT_DEBUG_KEY_SHA="FA:BD:39:70:66:08:66:65:96:4A:A7:16:3E:E3:3D:DC:E6:7E:CA:12:4E:9F:5B:23:7B:16:BF:32:28:B1:13:2D"

die() { printf '\033[31mERROR: %s\033[0m\n' "$1"; exit 1; }

echo
echo "=========================================="
echo "      Legado 云端 Linux Debug Build"
echo "=========================================="
echo

echo "[1/6] JDK"
JAVA_BIN=""
[ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ] && JAVA_BIN="${JAVA_HOME}/bin/java"
[ -z "${JAVA_BIN}" ] && JAVA_BIN="java"
JAVA_SPEC=$("${JAVA_BIN}" -XshowSettings:properties -version 2>&1 | grep 'java.specification.version' | awk -F'= ' '{print $2}')
[ "${JAVA_SPEC}" = "${EXPECT_JDK}" ] || die "需要 JDK ${EXPECT_JDK}（Gradle 实际使用 ${JAVA_BIN}，当前: ${JAVA_SPEC:-未找到}），否则与本地编译环境不一致"
echo "JDK: ${JAVA_SPEC} OK (${JAVA_BIN})"

echo
echo "[2/6] Android SDK"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "${SDK_DIR}" ] && [ -f "${PROJECT_DIR}/local.properties" ]; then
  SDK_DIR=$(grep -E '^sdk\.dir=' "${PROJECT_DIR}/local.properties" | head -1 | cut -d= -f2-)
fi
[ -n "${SDK_DIR}" ] || die "未找到 Android SDK，请设置 ANDROID_HOME/ANDROID_SDK_ROOT 或写入 local.properties"
[ -f "${SDK_DIR}/platforms/android-${EXPECT_COMPILE_SDK}/android.jar" ] || die "缺少 ${SDK_DIR}/platforms/android-${EXPECT_COMPILE_SDK}/android.jar"
BT_VERSION=""
[ -x "${SDK_DIR}/build-tools/36.0.0/aapt2" ] && BT_VERSION="36.0.0"
[ -z "${BT_VERSION}" ] && [ -x "${SDK_DIR}/build-tools/35.0.0/aapt2" ] && BT_VERSION="35.0.0"
[ -n "${BT_VERSION}" ] || die "未找到 build-tools 36.0.0 / 35.0.0"
echo "SDK OK (platform ${EXPECT_COMPILE_SDK}, build-tools ${BT_VERSION}, sdk.dir=${SDK_DIR})"

echo
echo "[3/6] 签名一致性校验"
[ -f "${PROJECT_DIR}/app/debug.keystore" ] || die "缺少 app/debug.keystore，云端与本地签名将不一致"
KEYTOOL=""
[ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/keytool" ] && KEYTOOL="${JAVA_HOME}/bin/keytool"
[ -z "${KEYTOOL}" ] && KEYTOOL="keytool"
KEY_SHA=$("${KEYTOOL}" -list -v -keystore "${PROJECT_DIR}/app/debug.keystore" -storepass android -alias androiddebugkey 2>/dev/null | grep 'SHA256:' | awk '{print $2}')
if [ "${KEY_SHA}" != "${EXPECT_DEBUG_KEY_SHA}" ]; then
  die "debug.keystore 指纹与本地不一致：当前=${KEY_SHA:-读取失败}，期望=${EXPECT_DEBUG_KEY_SHA}"
fi
echo "签名 OK: ${KEY_SHA}"

echo
echo "[4/6] 与本地代码同步（versionCode = 10000 + git 提交数）"
if command -v git >/dev/null 2>&1; then
  # 浅克隆会导致提交数与本地完整克隆不一致，versionCode 随之不同，必须补齐历史
  if [ "$(git -C "${PROJECT_DIR}" rev-parse --is-shallow-repository 2>/dev/null)" = "true" ]; then
    echo "检测到浅克隆，正在补齐历史: git fetch --unshallow ..."
    git -C "${PROJECT_DIR}" fetch --unshallow origin || die "git fetch --unshallow 失败，无法保证 versionCode 与本地一致"
  fi
  if [ -z "$(git -C "${PROJECT_DIR}" status --porcelain 2>/dev/null)" ]; then
    echo "正在拉取 origin/master ..."
    git -C "${PROJECT_DIR}" pull --rebase origin master || die "git pull 失败，请先处理冲突"
  else
    echo "WARNING: 存在未提交改动，跳过自动拉取，请确认与本地在同一提交。"
  fi
  LOCAL_SHA=$(git -C "${PROJECT_DIR}" rev-parse --short HEAD)
  REMOTE_SHA=$(git -C "${PROJECT_DIR}" rev-parse --short origin/master)
  if [ "${LOCAL_SHA}" != "${REMOTE_SHA}" ]; then
    echo "WARNING: 本地 HEAD (${LOCAL_SHA}) 与 origin/master (${REMOTE_SHA}) 不一致，两端 versionCode 会不同。"
  fi
  LOCAL_COUNT=$(git -C "${PROJECT_DIR}" rev-list HEAD --count)
  echo "versionCode 预计: $((10000 + LOCAL_COUNT))（提交数 ${LOCAL_COUNT}，HEAD ${LOCAL_SHA}）"
else
  echo "WARNING: 未找到 git，跳过同步校验。"
fi

echo
echo "[5/6] 构建 Debug APK"
cd "${PROJECT_DIR}" || die "无法进入项目目录"
# 沙箱内存有限，默认小堆；如需调整可用环境变量覆盖
GRADLE_MEM="${GRADLE_MEM:--Xmx1280m -Xms256m -XX:MaxMetaspaceSize=384m}"
./gradlew --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs="-XX:+UseParallelGC ${GRADLE_MEM} -Dfile.encoding=UTF-8" \
  -Dkotlin.daemon.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=256m" \
  assembleAppDebug || die "Gradle 构建失败"

APK=$(ls -1 app/build/outputs/apk/app/debug/legado_app_*.apk 2>/dev/null | tail -1)
[ -n "${APK}" ] || die "未找到构建产物 app/build/outputs/apk/app/debug/legado_app_*.apk"

echo
echo "[6/6] 产物校验（签名 / versionCode）"
"${SDK_DIR}/build-tools/${BT_VERSION}/aapt2" dump badging "${APK}" | grep -E "^package:" || true
CERT=$("${SDK_DIR}/build-tools/${BT_VERSION}/apksigner" verify --print-certs "${APK}" 2>&1 | grep 'SHA-256')
echo "${CERT}"
if echo "${CERT}" | grep -qi "${EXPECT_DEBUG_KEY_SHA}"; then
  echo "产物签名与本地一致 OK"
else
  echo "WARNING: 产物签名指纹与期望值不一致，请勿与本地已装应用互相覆盖安装。"
fi

echo
echo "=========================================="
echo "BUILD SUCCESS"
echo "APK: ${PROJECT_DIR}/${APK}"
echo "提示: 覆盖安装需 versionCode 不低于已装版本，且签名一致。"
echo "=========================================="
