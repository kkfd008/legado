#!/usr/bin/env bash
# env_common.sh —— 本地/云端构建环境统一引导
# 由 check_build_env_linux.sh / build_linux.sh source 引入
# 规则: mise 管理 JDK 17 与 Gradle；Android SDK 固定 platform 36 + build-tools 36.0.0
set -u

COMMON_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${COMMON_DIR}/.." && pwd)"

# ==== 与 Windows 脚本 env_bootstrap_windows.bat 保持一致 ====
EXPECT_JDK=17
EXPECT_COMPILE_SDK=36
EXPECT_BUILD_TOOLS=36.0.0
EXPECT_GRADLE_MIN=8.13
DEFAULT_GRADLE=8.14
EXPECT_AGP=8.13.2
EXPECT_KOTLIN=2.3.0
EXPECT_DEBUG_KEY_SHA="FA:BD:39:70:66:08:66:65:96:4A:A7:16:3E:E3:3D:DC:E6:7E:CA:12:4E:9F:5B:23:7B:16:BF:32:28:B1:13:2D"

MISE_BIN=""
GRADLE_VERSION=""
SDK_DIR=""
BT_VERSION=""

env_msg() { printf '[ENV] %s\n' "$1"; }

# 1) 安装 mise（未安装时）
ensure_mise() {
  if command -v mise >/dev/null 2>&1; then
    MISE_BIN="$(command -v mise)"
    env_msg "mise 已存在: $("${MISE_BIN}" --version 2>/dev/null)"
    return 0
  fi
  for candidate in "${HOME}/.local/bin/mise" "/usr/local/bin/mise" "/usr/bin/mise"; do
    if [ -x "${candidate}" ]; then
      MISE_BIN="${candidate}"
      env_msg "mise 已存在: ${MISE_BIN}"
      return 0
    fi
  done
  command -v curl >/dev/null 2>&1 || { env_msg "未找到 curl，无法自动安装 mise"; return 1; }
  env_msg "未找到 mise，正在安装 (https://mise.run) ..."
  curl -fsSL https://mise.run | sh || { env_msg "mise 安装失败"; return 1; }
  MISE_BIN="${HOME}/.local/bin/mise"
  [ -x "${MISE_BIN}" ] || { env_msg "mise 安装后未找到 ${MISE_BIN}"; return 1; }
  env_msg "mise 安装完成: $("${MISE_BIN}" --version 2>/dev/null)"
}

# 2) JDK 17 由 mise 提供
ensure_java17() {
  ensure_mise || return 1
  local dir
  dir="$("${MISE_BIN}" where "java@${EXPECT_JDK}" 2>/dev/null)"
  if [ -z "${dir}" ] || [ ! -x "${dir}/bin/java" ]; then
    env_msg "mise 中未安装 JDK ${EXPECT_JDK}，正在安装 ..."
    "${MISE_BIN}" install "java@${EXPECT_JDK}" || { env_msg "JDK ${EXPECT_JDK} 安装失败"; return 1; }
    dir="$("${MISE_BIN}" where "java@${EXPECT_JDK}" 2>/dev/null)"
  fi
  if [ -z "${dir}" ] || [ ! -x "${dir}/bin/java" ]; then
    env_msg "mise 未能提供 JDK ${EXPECT_JDK}"
    return 1
  fi
  JAVA_HOME="${dir}"
  PATH="${JAVA_HOME}/bin:${PATH}"
  env_msg "JAVA_HOME=${JAVA_HOME}"
}

# 3) Gradle: 优先使用 mise 指定的版本，未安装则通过 mise 安装
#    注: mise 仓库最低提供 8.14，AGP 8.13.2 要求 >= 8.13，两者兼容
ensure_gradle() {
  ensure_mise || return 1
  local ver current
  current="$("${MISE_BIN}" current gradle 2>/dev/null | head -1)"
  ver="${current:-${DEFAULT_GRADLE}}"
  if [ -z "$("${MISE_BIN}" where "gradle@${ver}" 2>/dev/null)" ]; then
    env_msg "mise 中未安装 Gradle ${ver}，正在安装 ..."
    "${MISE_BIN}" install "gradle@${ver}" || { env_msg "Gradle ${ver} 安装失败"; return 1; }
  fi
  GRADLE_VERSION="${ver}"
  if [ -n "${current}" ]; then
    env_msg "使用 mise 指定的 Gradle ${ver}"
  else
    env_msg "mise 未指定 Gradle，使用默认 ${ver}"
  fi
}

# 通过 mise 运行 Gradle，保证与 Windows 本地使用同一版本
run_gradle() {
  "${MISE_BIN}" x "gradle@${GRADLE_VERSION}" -- gradle "$@"
}

# 4) Android SDK：固定 platform 36 + build-tools 36.0.0，缺失则自动安装
ensure_sdk() {
  SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [ -z "${SDK_DIR}" ] && [ -f "${PROJECT_DIR}/local.properties" ]; then
    SDK_DIR=$(grep -E '^sdk\.dir=' "${PROJECT_DIR}/local.properties" 2>/dev/null | head -1 | cut -d= -f2-)
  fi
  if [ -z "${SDK_DIR}" ]; then
    for candidate in "${HOME}/android-sdk" "${HOME}/.android-sdk" "/usr/local/android-sdk" "/opt/android-sdk"; do
      if [ -d "${candidate}/platforms" ]; then
        SDK_DIR="${candidate}"
        break
      fi
    done
  fi
  [ -n "${SDK_DIR}" ] || { env_msg "未找到 Android SDK，请设置 ANDROID_HOME 或 local.properties"; return 1; }
  env_msg "Android SDK=${SDK_DIR}"

  local sdkmanager="${SDK_DIR}/cmdline-tools/latest/bin/sdkmanager"
  local need_platform="${SDK_DIR}/platforms/android-${EXPECT_COMPILE_SDK}/android.jar"
  local need_bt="${SDK_DIR}/build-tools/${EXPECT_BUILD_TOOLS}"

  if [ ! -f "${need_platform}" ] || [ ! -d "${need_bt}" ]; then
    if [ -x "${sdkmanager}" ]; then
      env_msg "SDK 缺少 platform ${EXPECT_COMPILE_SDK} 或 build-tools ${EXPECT_BUILD_TOOLS}，正在安装 ..."
      (yes | "${sdkmanager}" "platforms;android-${EXPECT_COMPILE_SDK}" "build-tools;${EXPECT_BUILD_TOOLS}" >/dev/null 2>&1) \
        || env_msg "sdkmanager 安装失败，请手动安装 platform ${EXPECT_COMPILE_SDK} + build-tools ${EXPECT_BUILD_TOOLS}"
    else
      env_msg "未找到 sdkmanager，无法自动安装 SDK 组件"
    fi
  fi

  [ -f "${need_platform}" ] || { env_msg "缺少 platform android-${EXPECT_COMPILE_SDK}"; return 1; }
  [ -d "${need_bt}" ] || { env_msg "缺少 build-tools ${EXPECT_BUILD_TOOLS}"; return 1; }
  BT_VERSION="${EXPECT_BUILD_TOOLS}"
  export ANDROID_HOME="${SDK_DIR}"
  export ANDROID_SDK_ROOT="${SDK_DIR}"
}

# 5) versionCode 基数：要求完整克隆且与 origin/master 同提交
git_commit_count() {
  git -C "${PROJECT_DIR}" rev-list HEAD --count 2>/dev/null
}
git_is_shallow() {
  [ "$(git -C "${PROJECT_DIR}" rev-parse --is-shallow-repository 2>/dev/null)" = "true" ]
}
