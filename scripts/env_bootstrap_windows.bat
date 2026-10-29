@echo off
rem ============================================================
rem env_bootstrap_windows.bat - Windows 构建环境统一引导
rem 被 check_build_env_windows11.bat / build_windows11.bat 调用:
rem     call "%~dp0env_bootstrap_windows.bat"
rem 规则: JDK 17 + Gradle 由 mise 或项目自身提供; SDK 固定 platform 36 + build-tools 36.0.0
rem 注意: 本脚本不使用 setlocal, 变量需回传给调用脚本
rem
rem 两种模式:
rem   默认(构建): 缺什么自动安装
rem   ENV_NO_INSTALL=1(检测): 只检测不安装, 把安装建议写入 ENV_FIX_FILE
rem
rem 导出变量:
rem   JAVA_HOME / JAVA_SPEC        JDK 路径与实际版本(要求 17)
rem   GRADLE_CMD                   Gradle 可执行文件
rem   GRADLE_VERSION / GRADLE_SRC  Gradle 版本与来源: wrapper / mise
rem   GRADLE_CACHED                1=本地已就绪, 0=需要联网下载 distribution
rem   SDK_DIR / BT_VERSION / PROJECT_DIR
rem   ENV_MISSING                  1=存在缺失项
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

if not defined ENV_NO_INSTALL set "ENV_NO_INSTALL=0"

set "MISE_BIN="
set "JAVA_HOME_MISE="
set "JAVA_SPEC="
set "JDK_SRC="
set "GRADLE_CMD="
set "GRADLE_VERSION="
set "GRADLE_SRC="
set "GRADLE_CACHED=0"
set "SDK_DIR="
set "BT_VERSION="
set "ENV_MISSING=0"

call :ensure_mise || exit /b 1
call :ensure_java17 || exit /b 1
call :ensure_gradle || exit /b 1
call :ensure_sdk || exit /b 1
exit /b 0

rem ------------------------------------------------------------
rem 记录安装建议: 仅检查模式下输出到控制台并写入 ENV_FIX_FILE
rem ------------------------------------------------------------
:suggest
if /i not "!ENV_NO_INSTALL!"=="1" exit /b 0
echo [FIX] %~1
if defined ENV_FIX_FILE >>"!ENV_FIX_FILE!" echo %~1
exit /b 0

rem 缺失项统一出口
:missing
set "ENV_MISSING=1"
call :suggest "%~1"
exit /b 1

rem ------------------------------------------------------------
rem 1) mise: 未安装则自动安装 (winget -> 常见路径)
rem ------------------------------------------------------------
:ensure_mise
set "MISE_VER="
where mise.exe >nul 2>nul
if not errorlevel 1 (
  set "MISE_BIN=mise"
  for /f "delims=" %%A in ('mise --version 2^>nul') do if not defined MISE_VER set "MISE_VER=%%A"
  echo [ENV] mise 已存在: !MISE_VER!
  exit /b 0
)
if exist "%LOCALAPPDATA%\Programs\mise\mise.exe" set "MISE_BIN=%LOCALAPPDATA%\Programs\mise\mise.exe"
if not defined MISE_BIN if exist "%USERPROFILE%\scoop\shims\mise.exe" set "MISE_BIN=%USERPROFILE%\scoop\shims\mise.exe"
if not defined MISE_BIN if exist "%USERPROFILE%\.local\bin\mise.exe" set "MISE_BIN=%USERPROFILE%\.local\bin\mise.exe"
if defined MISE_BIN (
  echo [ENV] mise 已存在: !MISE_BIN!
  exit /b 0
)
if /i "!ENV_NO_INSTALL!"=="1" (
  echo [ENV] 未找到 mise
  call :missing "安装 mise（任选其一）: winget install --id jdx.mise -e"
  exit /b 1
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
  echo ERROR: mise 安装失败
  exit /b 1
)
echo [ENV] mise 安装完成: !MISE_BIN!
exit /b 0

rem ------------------------------------------------------------
rem 2) JDK 17: 优先复用合法 JAVA_HOME, 否则用 mise 提供的 JDK 17
rem    版本以 java -version 实际输出为准, 不信任环境变量
rem ------------------------------------------------------------
:ensure_java17
set "JAVA_SPEC="
set "JDK_SRC="

if defined JAVA_HOME (
  call :probe_java "%JAVA_HOME%"
  if "!JAVA_SPEC!"=="%EXPECT_JDK%" set "JDK_SRC=JAVA_HOME"
)

if not defined JDK_SRC (
  set "JAVA_HOME_MISE="
  for /f "delims=" %%A in ('"%MISE_BIN%" where java@%EXPECT_JDK% 2^>nul') do set "JAVA_HOME_MISE=%%A"
  if not exist "!JAVA_HOME_MISE!\bin\java.exe" if /i not "!ENV_NO_INSTALL!"=="1" (
    echo [ENV] mise 未安装 JDK %EXPECT_JDK%, 正在安装 ...
    call "%MISE_BIN%" install java@%EXPECT_JDK%
    set "JAVA_HOME_MISE="
    for /f "delims=" %%A in ('"%MISE_BIN%" where java@%EXPECT_JDK% 2^>nul') do set "JAVA_HOME_MISE=%%A"
  )
  if exist "!JAVA_HOME_MISE!\bin\java.exe" (
    set "JAVA_HOME=!JAVA_HOME_MISE!"
    set "JAVA_SPEC="
    call :probe_java "!JAVA_HOME!"
    set "JDK_SRC=mise"
  )
)

if not "!JAVA_SPEC!"=="%EXPECT_JDK%" (
  echo [ENV] JDK %EXPECT_JDK% 不可用，当前 JAVA_SPEC="!JAVA_SPEC!"，JAVA_HOME=!JAVA_HOME!
  if /i "!ENV_NO_INSTALL!"=="1" (
    call :missing "安装并启用 JDK %EXPECT_JDK%: mise install java@%EXPECT_JDK%  然后  mise use java@%EXPECT_JDK%"
    exit /b 1
  )
  echo ERROR: 缺少 JDK %EXPECT_JDK%
  exit /b 1
)

set "PATH=!JAVA_HOME!\bin;!PATH!"
echo [ENV] JDK !JAVA_SPEC! 就绪: !JAVA_HOME!  来源: !JDK_SRC!
exit /b 0

rem 探测 %1 目录下的 JDK 主版本号 -> JAVA_SPEC
:probe_java
set "JAVA_SPEC="
if not exist "%~1\bin\java.exe" exit /b 1
set "JV_RAW="
set "JV_FILE=%TEMP%\legado_java_ver_%RANDOM%%RANDOM%.txt"
"%~1\bin\java.exe" -version >"!JV_FILE!" 2>&1
for /f "tokens=3" %%A in ('findstr /I /C:"version" "!JV_FILE!"') do if not defined JV_RAW set "JV_RAW=%%~A"
if exist "!JV_FILE!" del "!JV_FILE!" >nul 2>nul
if not defined JV_RAW exit /b 1
for /f "tokens=1 delims=." %%A in ("!JV_RAW!") do set "JAVA_SPEC=%%A"
exit /b 0

rem ------------------------------------------------------------
rem 3) Gradle 选择顺序(全部离线判定, 不启动 JVM):
rem    a. wrapper distribution 已缓存 -> 用 wrapper(与 CI 完全一致)
rem    b. mise 本地已装 gradle 且版本满足 -> 直接用它(免联网下载)
rem    c. wrapper 存在但未缓存 -> 仍用 wrapper(会触发联网下载)
rem    d. 都没有 -> 构建模式 mise 安装, 检查模式给出安装指令
rem    不使用 "mise x ... -- gradle": 引号会让 for /f 命令解析失败
rem ------------------------------------------------------------
:ensure_gradle
set "GRADLE_CMD="
set "GRADLE_VERSION="
set "GRADLE_SRC="
set "GRADLE_CACHED=0"
set "WRAPPER_VER="
set "MISE_GVER="
set "MISE_GBIN="
set "VER_GE=0"

rem a0) 读取 wrapper 期望版本, 并判断其 distribution 是否已缓存
if exist "%PROJECT_DIR%\gradlew.bat" (
  set "WRAPPER_PROPS=%PROJECT_DIR%\gradle\wrapper\gradle-wrapper.properties"
  if exist "!WRAPPER_PROPS!" (
    for /f "tokens=2 delims=-" %%A in ('findstr /R /C:"gradle-[0-9]" "!WRAPPER_PROPS!"') do if not defined WRAPPER_VER set "WRAPPER_VER=%%A"
  )
)
if defined WRAPPER_VER (
  set "GRADLE_VERSION=!WRAPPER_VER!"
  call :wrapper_cached
  if "!GRADLE_CACHED!"=="1" (
    set "GRADLE_CMD=%PROJECT_DIR%\gradlew.bat"
    set "GRADLE_SRC=wrapper"
    echo [ENV] Gradle !GRADLE_VERSION! 就绪，来源: 项目 wrapper，distribution 已缓存
    exit /b 0
  )
)

rem b) mise 本地已装 gradle, 版本满足最低要求则直接复用
for /f "delims=" %%A in ('"%MISE_BIN%" current gradle 2^>nul') do if not defined MISE_GVER set "MISE_GVER=%%A"
if defined MISE_GVER (
  for /f "delims=" %%A in ('"%MISE_BIN%" where gradle@!MISE_GVER! 2^>nul') do set "MISE_GDIR=%%A"
  if defined MISE_GDIR call :find_gradle_bin "!MISE_GDIR!"
  if defined MISE_GBIN (
    call :ver_ge_min "!MISE_GVER!"
    if "!VER_GE!"=="1" (
      set "GRADLE_CMD=!MISE_GBIN!"
      set "GRADLE_VERSION=!MISE_GVER!"
      set "GRADLE_SRC=mise"
      set "GRADLE_CACHED=1"
      echo [ENV] Gradle !GRADLE_VERSION! 就绪，来源: mise 本地安装，无需下载
      if defined WRAPPER_VER if not "!MISE_GVER:~0,4!"=="!WRAPPER_VER:~0,4!" echo [ENV] 提示: mise 的 Gradle !MISE_GVER! 与 wrapper 期望 !WRAPPER_VER! 不同
      exit /b 0
    )
  )
)

rem c) wrapper 存在但 distribution 未缓存, 仍用 wrapper(会联网下载)
if defined WRAPPER_VER (
  set "GRADLE_VERSION=!WRAPPER_VER!"
  set "GRADLE_CMD=%PROJECT_DIR%\gradlew.bat"
  set "GRADLE_SRC=wrapper"
  set "GRADLE_CACHED=0"
  echo [ENV] Gradle !GRADLE_VERSION! 就绪，来源: 项目 wrapper，distribution 需要下载
  call :suggest "网络可达时由 wrapper 自动下载；或先执行下载指令: powershell -Command Invoke-WebRequest https://services.gradle.org/distributions/gradle-!GRADLE_VERSION!-bin.zip -OutFile gradle-!GRADLE_VERSION!-bin.zip"
  exit /b 0
)

rem d) 既无 wrapper 也无 mise 可用版本
set "GRADLE_VERSION="
if defined MISE_GVER set "GRADLE_VERSION=!MISE_GVER!"
if not defined GRADLE_VERSION set "GRADLE_VERSION=%DEFAULT_GRADLE%"
if /i not "!ENV_NO_INSTALL!"=="1" (
  echo [ENV] 未找到可用 Gradle, 正在通过 mise 安装 %GRADLE_VERSION% ...
  call "%MISE_BIN%" install gradle@!GRADLE_VERSION!
  set "MISE_GDIR="
  for /f "delims=" %%A in ('"%MISE_BIN%" where gradle@!GRADLE_VERSION! 2^>nul') do set "MISE_GDIR=%%A"
  if defined MISE_GDIR call :find_gradle_bin "!MISE_GDIR!"
  if defined MISE_GBIN (
    set "GRADLE_CMD=!MISE_GBIN!"
    set "GRADLE_SRC=mise"
    set "GRADLE_CACHED=1"
    echo [ENV] Gradle !GRADLE_VERSION! 就绪，来源: mise
    exit /b 0
  )
  echo ERROR: mise 无法提供可用的 Gradle
  exit /b 1
)
echo [ENV] 未找到可用 Gradle
call :missing "安装 Gradle %GRADLE_VERSION%: mise install gradle@%GRADLE_VERSION%  然后在项目根目录执行  mise use gradle@%GRADLE_VERSION%"
exit /b 1

rem 在 %1 目录下定位 gradle.bat -> MISE_GBIN
:find_gradle_bin
set "MISE_GBIN="
if exist "%~1\bin\gradle.bat" set "MISE_GBIN=%~1\bin\gradle.bat"
if not defined MISE_GBIN (
  for /d %%D in ("%~1\gradle-*") do if not defined MISE_GBIN if exist "%%~fD\bin\gradle.bat" set "MISE_GBIN=%%~fD\bin\gradle.bat"
)
exit /b 0

rem 判断 %1 版本是否 >= EXPECT_GRADLE_MIN -> VER_GE
:ver_ge_min
set "VG_MAJOR="
set "VG_MINOR="
for /f "tokens=1,2 delims=." %%A in ("%~1") do (
  set "VG_MAJOR=%%A"
  set "VG_MINOR=%%B"
)
if not defined VG_MAJOR set "VG_MAJOR=0"
if not defined VG_MINOR set "VG_MINOR=0"
set "VM_MAJOR="
set "VM_MINOR="
for /f "tokens=1,2 delims=." %%A in ("%EXPECT_GRADLE_MIN%") do (
  set "VM_MAJOR=%%A"
  set "VM_MINOR=%%B"
)
if not defined VM_MAJOR set "VM_MAJOR=0"
if not defined VM_MINOR set "VM_MINOR=0"
set "VER_GE=0"
if !VG_MAJOR! GTR !VM_MAJOR! set "VER_GE=1"
if !VG_MAJOR! EQU !VM_MAJOR! if !VG_MINOR! GEQ !VM_MINOR! set "VER_GE=1"
exit /b 0

rem 判断 wrapper distribution 是否已在本地解压 -> GRADLE_CACHED
:wrapper_cached
set "GRADLE_CACHED=0"
set "GRADLE_STORE=%GRADLE_USER_HOME%"
if not defined GRADLE_STORE set "GRADLE_STORE=%USERPROFILE%\.gradle"
set "GDIST_DIR="
for /d %%D in ("!GRADLE_STORE!\wrapper\dists\gradle-!WRAPPER_VER!-bin\*") do if not defined GDIST_DIR set "GDIST_DIR=%%~fD"
if defined GDIST_DIR if exist "!GDIST_DIR!\gradle-!WRAPPER_VER!\bin\gradle.bat" set "GRADLE_CACHED=1"
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
  echo [ENV] 未找到 Android SDK
  if /i "!ENV_NO_INSTALL!"=="1" (
    call :missing "安装 Android SDK 命令行工具并在 local.properties 写入 sdk.dir，或设置环境变量 ANDROID_HOME"
    exit /b 1
  )
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
if /i "!ENV_NO_INSTALL!"=="1" (
  if not exist "!NEED_PLATFORM!" echo [ENV] 缺少 platform android-%EXPECT_COMPILE_SDK%
  if not exist "!NEED_BT!" echo [ENV] 缺少 build-tools %EXPECT_BUILD_TOOLS%
  call :missing "安装 SDK 组件: \"!SDK_DIR!\cmdline-tools\latest\bin\sdkmanager.bat\" \"platforms;android-%EXPECT_COMPILE_SDK%\" \"build-tools;%EXPECT_BUILD_TOOLS%\""
  exit /b 1
)
if not exist "!SDK_DIR!\cmdline-tools\latest\bin\sdkmanager.bat" (
  echo ERROR: 缺少 platform %EXPECT_COMPILE_SDK% 或 build-tools %EXPECT_BUILD_TOOLS%，且未找到 sdkmanager，请手动安装后重试
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
