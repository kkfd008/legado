@echo off
rem ============================================================
rem env_bootstrap_windows.bat - Windows 构建环境统一引导
rem 由 check_build_env_windows11.bat / build_windows11.bat 调用:
rem     call "%~dp0env_bootstrap_windows.bat"
rem 规则: mise 管理 JDK 17 与 Gradle; Android SDK 固定 platform 36 + build-tools 36.0.0
rem 注意: 本脚本不使用 setlocal, 变量需回传给调用脚本
rem ============================================================

set "EXPECT_JDK=17"
set "EXPECT_COMPILE_SDK=36"
set "EXPECT_BUILD_TOOLS=36.0.0"
set "EXPECT_GRADLE_MIN=8.13"
set "DEFAULT_GRADLE=8.14"
set "EXPECT_AGP=8.13.2"
set "EXPECT_KOTLIN=2.3.0"
set "EXPECT_DEBUG_KEY_SHA=FA:BD:39:70:66:08:66:65:96:4A:A7:16:3E:E3:3D:DC:E6:7E:CA:12:4E:9F:5B:23:7B:16:BF:32:28:B1:13:2D"

for %%I in ("%~dp0..") do set "PROJECT_DIR=%%~fI"

set "MISE_BIN="
set "GRADLE_PREFIX="
set "GRADLE_VERSION="
set "JAVA_HOME_MISE="
set "SDK_DIR="
set "BT_VERSION="

call :ensure_mise || exit /b 1
call :ensure_java17 || exit /b 1
call :ensure_gradle || exit /b 1
call :ensure_sdk || exit /b 1
exit /b 0

rem ------------------------------------------------------------
rem 1) mise: 未安装则自动安装 (winget -> scoop -> 常见路径)
rem ------------------------------------------------------------
:ensure_mise
where mise.exe >nul 2>nul
if not errorlevel 1 (
  set "MISE_BIN=mise"
  for /f "delims=" %%A in ('mise --version 2^>nul') do echo [ENV] mise 已存在: %%A
  exit /b 0
)
if exist "%LOCALAPPDATA%\Programs\mise\mise.exe" set "MISE_BIN=%LOCALAPPDATA%\Programs\mise\mise.exe"
if not defined MISE_BIN if exist "%USERPROFILE%\scoop\shims\mise.exe" set "MISE_BIN=%USERPROFILE%\scoop\shims\mise.exe"
if not defined MISE_BIN if exist "%USERPROFILE%\.local\bin\mise.exe" set "MISE_BIN=%USERPROFILE%\.local\bin\mise.exe"
if defined MISE_BIN (
  echo [ENV] mise 已存在: !MISE_BIN!
  exit /b 0
)
echo [ENV] 未找到 mise, 正在安装 ...
where winget.exe >nul 2>nul
if not errorlevel 1 (
  winget install --id jdx.mise -e --accept-source-agreements --accept-package-agreements
)
where mise.exe >nul 2>nul
if not errorlevel 1 (
  set "MISE_BIN=mise"
  exit /b 0
)
if not defined MISE_BIN if exist "%LOCALAPPDATA%\Programs\mise\mise.exe" set "MISE_BIN=%LOCALAPPDATA%\Programs\mise\mise.exe"
if not defined MISE_BIN if exist "%USERPROFILE%\scoop\shims\mise.exe" set "MISE_BIN=%USERPROFILE%\scoop\shims\mise.exe"
if not defined MISE_BIN (
  echo ERROR: mise 安装失败。请手动执行: winget install jdx.mise
  exit /b 1
)
echo [ENV] mise 安装完成: !MISE_BIN!
exit /b 0

rem ------------------------------------------------------------
rem 2) JDK 17: 由 mise 安装并导出 JAVA_HOME
rem ------------------------------------------------------------
:ensure_java17
set "JAVA_HOME_MISE="
for /f "delims=" %%A in ('"%MISE_BIN%" where java@%EXPECT_JDK% 2^>nul') do set "JAVA_HOME_MISE=%%A"
if not exist "!JAVA_HOME_MISE!\bin\java.exe" (
  echo [ENV] mise 中未安装 JDK %EXPECT_JDK%, 正在安装 ...
  call "%MISE_BIN%" install java@%EXPECT_JDK%
  set "JAVA_HOME_MISE="
  for /f "delims=" %%A in ('"%MISE_BIN%" where java@%EXPECT_JDK% 2^>nul') do set "JAVA_HOME_MISE=%%A"
)
if not exist "!JAVA_HOME_MISE!\bin\java.exe" (
  echo ERROR: mise 未能提供 JDK %EXPECT_JDK%
  exit /b 1
)
set "JAVA_HOME=!JAVA_HOME_MISE!"
set "PATH=!JAVA_HOME!\bin;!PATH!"
echo [ENV] JAVA_HOME=!JAVA_HOME!
exit /b 0

rem ------------------------------------------------------------
rem 3) Gradle: 优先 mise 指定版本, 缺失则通过 mise 安装
rem    mise 仓库最低提供 8.14, AGP 8.13.2 要求 >= 8.13
rem ------------------------------------------------------------
:ensure_gradle
set "GRADLE_CURRENT="
for /f "delims=" %%A in ('"%MISE_BIN%" current gradle 2^>nul') do if not defined GRADLE_CURRENT set "GRADLE_CURRENT=%%A"
set "GRADLE_VERSION=!GRADLE_CURRENT!"
if not defined GRADLE_VERSION set "GRADLE_VERSION=%DEFAULT_GRADLE%"
set "GRADLE_DIR_MISE="
for /f "delims=" %%A in ('"%MISE_BIN%" where gradle@!GRADLE_VERSION! 2^>nul') do set "GRADLE_DIR_MISE=%%A"
if not defined GRADLE_DIR_MISE (
  echo [ENV] mise 中未安装 Gradle !GRADLE_VERSION!, 正在安装 ...
  call "%MISE_BIN%" install gradle@!GRADLE_VERSION!
  set "GRADLE_DIR_MISE="
  for /f "delims=" %%A in ('"%MISE_BIN%" where gradle@!GRADLE_VERSION! 2^>nul') do set "GRADLE_DIR_MISE=%%A"
)
if not defined GRADLE_DIR_MISE (
  echo ERROR: mise 无法安装 Gradle !GRADLE_VERSION!
  exit /b 1
)
set "GRADLE_PREFIX="%MISE_BIN%" x gradle@!GRADLE_VERSION! -- gradle"
if defined GRADLE_CURRENT (
  echo [ENV] 使用 mise 指定的 Gradle !GRADLE_VERSION!
) else (
  echo [ENV] mise 未指定 Gradle, 使用默认 !GRADLE_VERSION!
)
exit /b 0

rem ------------------------------------------------------------
rem 4) Android SDK: 固定 platform 36 + build-tools 36.0.0
rem ------------------------------------------------------------
:ensure_sdk
set "SDK_DIR=%ANDROID_HOME%"
if not defined SDK_DIR set "SDK_DIR=%ANDROID_SDK_ROOT%"
if not defined SDK_DIR if exist "%PROJECT_DIR%\local.properties" (
  for /f "usebackq tokens=1,* delims==" %%A in ("%PROJECT_DIR%\local.properties") do (
    if /i "%%A"=="sdk.dir" set "SDK_DIR=%%B"
  )
)
if not defined SDK_DIR if exist "%LOCALAPPDATA%\Android\Sdk" set "SDK_DIR=%LOCALAPPDATA%\Android\Sdk"
if not defined SDK_DIR (
  echo ERROR: 未找到 Android SDK, 请设置 ANDROID_HOME 或 local.properties
  exit /b 1
)
set "SDK_DIR=!SDK_DIR:/=\!"
if "!SDK_DIR:~-1!"=="\" set "SDK_DIR=!SDK_DIR:~0,-1!"
echo [ENV] Android SDK=!SDK_DIR!

set "NEED_PLATFORM=!SDK_DIR!\platforms\android-%EXPECT_COMPILE_SDK%\android.jar"
set "NEED_BT=!SDK_DIR!\build-tools\%EXPECT_BUILD_TOOLS%"
if not exist "!NEED_PLATFORM!" goto :sdk_install
if not exist "!NEED_BT!" goto :sdk_install
goto :sdk_ready

:sdk_install
if not exist "!SDK_DIR!\cmdline-tools\latest\bin\sdkmanager.bat" (
  echo ERROR: 缺少 platform %EXPECT_COMPILE_SDK% 或 build-tools %EXPECT_BUILD_TOOLS%
  echo        且未找到 sdkmanager, 请手动安装后重试。
  exit /b 1
)
echo [ENV] SDK 缺少固定版本组件, 正在安装 platform %EXPECT_COMPILE_SDK% + build-tools %EXPECT_BUILD_TOOLS% ...
call :run_sdkmanager
if not exist "!NEED_PLATFORM!" (
  echo ERROR: platform android-%EXPECT_COMPILE_SDK% 安装失败
  exit /b 1
)
if not exist "!NEED_BT!" (
  echo ERROR: build-tools %EXPECT_BUILD_TOOLS% 安装失败
  exit /b 1
)

:sdk_ready
set "BT_VERSION=%EXPECT_BUILD_TOOLS%"
set "ANDROID_HOME=!SDK_DIR!"
set "ANDROID_SDK_ROOT=!SDK_DIR!"
echo [ENV] SDK OK: platform %EXPECT_COMPILE_SDK% + build-tools %EXPECT_BUILD_TOOLS%
exit /b 0

:run_sdkmanager
rem 用子进程喂入 y 以自动接受许可
(
  for /l %%i in (1,1,40) do @echo y
) | call "!SDK_DIR!\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-%EXPECT_COMPILE_SDK%" "build-tools;%EXPECT_BUILD_TOOLS%"
exit /b 0
