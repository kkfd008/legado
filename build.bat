@echo off
chcp 65001 >nul 2>nul
goto :legado_main
rem  [解析说明] 第 3 行的 goto 不能删：cmd 在文件内部切换 chcp 后，缓冲区解码会错位，
rem  导致后续注释/命令被从中间截断执行（报「不是内部或外部命令」）。先 goto 让 cmd 用新
rem  代码页重新扫描本文件，是让 UTF-8 中文批处理稳定运行的关键。
rem ============================================================================
rem  legado - build.bat   编译（只编译：不安装任何东西、不改环境）
rem
rem  用法：build.bat [Gradle 任务...]
rem        build.bat                            默认任务 :app:assembleAppDebug
rem        build.bat :app:assembleAppRelease   编 release 包
rem        build.bat clean :app:assembleAppDebug
rem        build.bat --wrapper                  改用仓库 gradlew（默认用 mise 的 gradle）
rem        build.bat --no-color                 关闭彩色输出
rem
rem  前置：check.bat 全绿（或 deploy.bat 补齐环境）。缺东西会快速失败并给出修复命令。
rem  退出码：与 Gradle 一致（0 = 成功）
rem
rem  产物：app\build\outputs\apk\app\debug\legado_app_<versionName>.apk
rem        versionCode = 10000 + git 提交数（见 app/build.gradle）
rem ============================================================================

:legado_main
setlocal EnableExtensions EnableDelayedExpansion
title legado - 编译

set "ESC="

set "PROJECT_DIR=%~dp0"
for %%I in ("%PROJECT_DIR%.") do set "PROJECT_ROOT=%%~fI"
set "REQUIRED_JAVA=17"
set "REQUIRED_SDK=36"
set "REQUIRED_BUILD_TOOLS=36.0.0"
set "EXPECTED_GRADLE=8.13"
set "EXPECTED_AGP=8.13.2"
set "EXPECT_DEBUG_KEY_SHA=FA:BD:39:70:66:08:66:65:96:4A:A7:16:3E:E3:3D:DC:E6:7E:CA:12:4E:9F:5B:23:7B:16:BF:32:28:B1:13:2D"
set "DEFAULT_TASKS=:app:assembleAppDebug"
set "APK_REL=app\build\outputs\apk\app\debug"

rem ---------- 参数：抽出 --no-color / --wrapper，其余原样传给 Gradle ----------
set "USE_COLOR=1"
set "USE_WRAPPER=0"
set "GRADLE_ARGS="
for %%A in (%*) do (
  if /i "%%~A"=="--no-color" (
    set "USE_COLOR=0"
  ) else if /i "%%~A"=="--wrapper" (
    set "USE_WRAPPER=1"
  ) else (
    set "GRADLE_ARGS=!GRADLE_ARGS! %%~A"
  )
)
if not defined GRADLE_ARGS set "GRADLE_ARGS= %DEFAULT_TASKS%"

rem ---------- 颜色 ----------
set "C_RED="
set "C_GRN="
set "C_YEL="
set "C_CYA="
set "C_DIM="
set "C_RST="
if "%USE_COLOR%"=="1" (
  set "C_RED=%ESC%[91m"
  set "C_GRN=%ESC%[92m"
  set "C_YEL=%ESC%[93m"
  set "C_CYA=%ESC%[96m"
  set "C_DIM=%ESC%[2m"
  set "C_RST=%ESC%[0m"
)

echo.
echo %C_CYA%============================================================%C_RST%
echo %C_CYA%  legado 编译%C_RST%
echo %C_CYA%============================================================%C_RST%
echo   项目目录   %PROJECT_DIR%
echo   Gradle 任务 %GRADLE_ARGS%
echo.

pushd "%PROJECT_DIR%" 2>nul
if errorlevel 1 (
  echo %C_RED%[FAIL]%C_RST% 无法进入项目目录：%PROJECT_DIR%
  exit /b 1
)

rem ================= 前置 1：Gradle =================
if "%USE_WRAPPER%"=="0" (
  where mise >nul 2>nul
  if errorlevel 1 (
    echo %C_RED%[FAIL]%C_RST% 未找到 mise：本项目的 JDK 与 Gradle 由 mise 管理
    echo       %C_DIM%→ 运行  deploy.bat  ，或改用  build.bat --wrapper%C_RST%
    popd
    exit /b 1
  )
  set "GRADLE_CUR="
  for /f "tokens=1,2" %%A in ('gradle --version 2^>nul ^| findstr /B /C:"Gradle"') do if not defined GRADLE_CUR set "GRADLE_CUR=%%B"
  if not defined GRADLE_CUR (
    echo %C_RED%[FAIL]%C_RST% gradle 不在 PATH 上，应由 mise 提供 %EXPECTED_GRADLE%
    echo       %C_DIM%→ 运行  deploy.bat  ，或改用  build.bat --wrapper%C_RST%
    popd
    exit /b 1
  )
  if not "!GRADLE_CUR!"=="%EXPECTED_GRADLE%" (
    echo %C_RED%[FAIL]%C_RST% Gradle 版本为 !GRADLE_CUR!，本项目要求 %EXPECTED_GRADLE%
    echo       %C_DIM%→ 运行  deploy.bat  ，或手动  mise use gradle@%EXPECTED_GRADLE%%C_RST%
    popd
    exit /b 1
  )
  echo %C_GRN%[ OK ]%C_RST% Gradle !GRADLE_CUR!（mise 管理）
) else (
  if not exist "%PROJECT_DIR%gradlew.bat" (
    echo %C_RED%[FAIL]%C_RST% 缺少 gradlew.bat：%PROJECT_DIR%gradlew.bat
    popd
    exit /b 1
  )
  echo %C_GRN%[ OK ]%C_RST% 使用仓库 Gradle Wrapper
)

rem ================= 前置 2：JDK 17 =================
where java.exe >nul 2>nul
if errorlevel 1 (
  echo %C_RED%[FAIL]%C_RST% PATH 中没有 java.exe，无法编译
  echo       %C_DIM%→ 运行  deploy.bat  安装 JDK %REQUIRED_JAVA%%C_RST%
  popd
  exit /b 1
)
call :detect_java
if not "!JAVA_MAJOR!"=="%REQUIRED_JAVA%" (
  echo %C_RED%[FAIL]%C_RST% 当前 Java 为 !JAVA_MAJOR!（!JAVA_VER_NUM!），本项目要求 JDK %REQUIRED_JAVA%
  echo       %C_DIM%→ app\build.gradle 固定 jvmToolchain^(%REQUIRED_JAVA%^)，更高版本会报 No matching toolchains%C_RST%
  echo       %C_DIM%→ 运行  deploy.bat  安装并切换 JDK %REQUIRED_JAVA%%C_RST%
  popd
  exit /b 1
)
echo %C_GRN%[ OK ]%C_RST% Java !JAVA_MAJOR!（!JAVA_VER_NUM!）

rem ================= 前置 3：Android SDK =================
call :resolve_sdk
if not defined SDK_DIR (
  echo %C_RED%[FAIL]%C_RST% 未定位到 Android SDK（local.properties / ANDROID_HOME / ANDROID_SDK_ROOT 均为空）
  echo       %C_DIM%→ 运行  deploy.bat%C_RST%
  popd
  exit /b 1
)
if not exist "%SDK_DIR%\" (
  echo %C_RED%[FAIL]%C_RST% Android SDK 目录不存在：%SDK_DIR%
  echo       %C_DIM%→ 运行  deploy.bat%C_RST%
  popd
  exit /b 1
)
echo %C_GRN%[ OK ]%C_RST% Android SDK：%SDK_DIR%

call :find_platform
if not defined PLAT_DIR (
  echo %C_RED%[FAIL]%C_RST% 缺少 SDK Platform %REQUIRED_SDK%（compileSdk = %REQUIRED_SDK%）
  echo       %C_DIM%→ 运行  deploy.bat%C_RST%
  popd
  exit /b 1
)
echo %C_GRN%[ OK ]%C_RST% platforms\!PLAT_DIR!

call :find_build_tools
if not defined BT_VERSION (
  echo %C_RED%[FAIL]%C_RST% 未找到 build-tools（AGP %EXPECTED_AGP% 需要 %REQUIRED_BUILD_TOOLS%）
  echo       %C_DIM%→ 运行  deploy.bat%C_RST%
  popd
  exit /b 1
)
if not "!BT_VERSION!"=="%REQUIRED_BUILD_TOOLS%" (
  echo %C_YEL%[WARN]%C_RST% build-tools 使用 !BT_VERSION!（期望 %REQUIRED_BUILD_TOOLS%）
) else (
  echo %C_GRN%[ OK ]%C_RST% build-tools !BT_VERSION!
)

rem ================= 前置 4：签名与 Firebase 配置 =================
if not exist "%PROJECT_DIR%app\debug.keystore" (
  echo %C_RED%[FAIL]%C_RST% 缺少 app\debug.keystore，debug 包无法签名
  echo       %C_DIM%→ git checkout -- app/debug.keystore%C_RST%
  popd
  exit /b 1
)
if not exist "%PROJECT_DIR%app\google-services.json" (
  echo %C_RED%[FAIL]%C_RST% 缺少 app\google-services.json，Firebase 插件会在配置阶段失败
  echo       %C_DIM%→ git checkout -- app/google-services.json%C_RST%
  popd
  exit /b 1
)
echo %C_GRN%[ OK ]%C_RST% debug.keystore / google-services.json 就位

rem ================= 前置 5：versionCode 预告 =================
where git.exe >nul 2>nul
if not errorlevel 1 (
  set "SHALLOW="
  for /f %%A in ('git -C "%PROJECT_ROOT%" rev-parse --is-shallow-repository 2^>nul') do set "SHALLOW=%%A"
  set "COMMIT_COUNT="
  for /f %%A in ('git -C "%PROJECT_ROOT%" rev-list HEAD --count 2^>nul') do set "COMMIT_COUNT=%%A"
  if defined COMMIT_COUNT set /a VERSION_CODE=10000+!COMMIT_COUNT!
  if "!SHALLOW!"=="true" (
    echo %C_YEL%[WARN]%C_RST% 浅克隆仓库，versionCode 会偏小（可能无法覆盖安装），建议 git fetch --unshallow
  ) else if defined COMMIT_COUNT (
    echo %C_GRN%[ OK ]%C_RST% versionCode 预计 = !VERSION_CODE!（10000 + !COMMIT_COUNT! 次提交）
  )
)

rem ================= 编译 =================
echo.
echo %C_CYA%------------------------------------------------------------%C_RST%
echo   开始编译，请稍候（首次构建需下载依赖，可能耗时较久）...
echo %C_CYA%------------------------------------------------------------%C_RST%
echo.

if "%USE_WRAPPER%"=="1" (
  call "%PROJECT_DIR%gradlew.bat" %GRADLE_ARGS%
) else (
  gradle %GRADLE_ARGS%
)
set "BUILD_EXIT=!ERRORLEVEL!"

if "!BUILD_EXIT!"=="0" (
  echo.
  echo %C_CYA%============================================================%C_RST%
  echo   %C_GRN%编译成功%C_RST%
  echo %C_CYA%============================================================%C_RST%
  if /i "%GRADLE_ARGS%"==" %DEFAULT_TASKS%" call :report_apk
) else (
  echo.
  echo %C_CYA%============================================================%C_RST%
  echo   %C_RED%编译失败，Gradle 退出码：!BUILD_EXIT!%C_RST%
  echo %C_CYA%============================================================%C_RST%
  echo   %C_DIM%若为环境缺失导致，运行  check.bat  查看，再运行  deploy.bat  补齐%C_RST%
)
echo.
popd
endlocal & exit /b %BUILD_EXIT%

rem ============================ Subroutines ============================

:report_apk
rem 只在默认任务（assembleAppDebug）后做产物检查
set "APK="
for %%F in ("%PROJECT_DIR%%APK_REL%\legado_app_*.apk") do if exist "%%~fF" set "APK=%%~fF"
if not defined APK (
  call :warn "未找到 %APK_REL%\legado_app_*.apk，请检查 Gradle 任务是否真正产出 APK"
  exit /b 0
)
for %%F in ("!APK!") do (
  set "APK_SIZE=%%~zF"
  set "APK_NAME=%%~nxF"
)
set /a APK_MB=!APK_SIZE!/1048576
echo   APK 输出目录：%PROJECT_DIR%%APK_REL%
echo     %C_GRN%!APK_NAME!%C_RST%  ^(!APK_SIZE! 字节 ≈ !APK_MB! MB^)
echo   %C_DIM%!APK!%C_RST%
echo.

rem 用 aapt2 读出版本信息（versionCode 是否与 git 提交数一致，肉眼可验）
set "BADGING=%TEMP%\legado-badging-%RANDOM%.txt"
"%SDK_DIR%\build-tools\!BT_VERSION!\aapt2.exe" dump badging "!APK!" >"!BADGING!" 2>nul
if exist "!BADGING!" (
  for /f "delims=" %%L in ('findstr /B /C:"package: name=" "!BADGING!"') do echo   %C_DIM%%%L%C_RST%
  del /q "!BADGING!" >nul 2>&1
)

rem 校验 APK 实际签名指纹，确认能覆盖安装旧版本
set "CERTS=%TEMP%\legado-certs-%RANDOM%.txt"
call "%SDK_DIR%\build-tools\!BT_VERSION!\apksigner.bat" verify --print-certs "!APK!" >"!CERTS!" 2>&1
if exist "!CERTS!" (
  set "APK_SHA="
  rem apksigner 输出：Signer #1 certificate SHA-256 digest: fabd39...（小写、无冒号）
  for /f "tokens=6" %%A in ('findstr /C:"SHA-256 digest" "!CERTS!" 2^>nul') do if not defined APK_SHA set "APK_SHA=%%A"
  set "EXPECT_SHA_NORM=%EXPECT_DEBUG_KEY_SHA::=%"
  if defined APK_SHA (
    if /i "!APK_SHA!"=="!EXPECT_SHA_NORM!" (
      echo   %C_GRN%签名指纹与官方 debug key 一致，可直接覆盖安装%C_RST%
    ) else (
      echo   %C_YEL%签名指纹与官方 debug key 不一致，安装时可能需要先卸载旧版%C_RST%
      echo   %C_DIM%当前：!APK_SHA!%C_RST%
    )
  )
  del /q "!CERTS!" >nul 2>&1
)
echo.
echo   安装到设备：%C_DIM%adb install -r "!APK!"%C_RST%
exit /b 0

:detect_java
set "JAVA_VER_LINE="
for /f "delims=" %%L in ('java -version 2^>^&1') do if not defined JAVA_VER_LINE set "JAVA_VER_LINE=%%L"
set "JAVA_FULL=%JAVA_VER_LINE:"=%"
set "JAVA_VER_NUM="
for /f "tokens=1,2,3" %%A in ("%JAVA_FULL%") do (
  if /i "%%B"=="version" if not defined JAVA_VER_NUM set "JAVA_VER_NUM=%%C"
)
if not defined JAVA_VER_NUM for /f "tokens=1,2" %%A in ("%JAVA_FULL%") do (
  if /i "%%A"=="version" if not defined JAVA_VER_NUM set "JAVA_VER_NUM=%%B"
)
set "JAVA_MAJOR="
for /f "tokens=1 delims=.-" %%M in ("%JAVA_VER_NUM%") do set "JAVA_MAJOR=%%M"
exit /b 0

:resolve_sdk
set "SDK_DIR="
if exist "%PROJECT_DIR%local.properties" (
  for /f "usebackq tokens=1,* delims==" %%A in ("%PROJECT_DIR%local.properties") do (
    if /i "%%A"=="sdk.dir" set "SDK_DIR=%%B"
  )
)
if not defined SDK_DIR if defined ANDROID_HOME set "SDK_DIR=%ANDROID_HOME%"
if not defined SDK_DIR if defined ANDROID_SDK_ROOT set "SDK_DIR=%ANDROID_SDK_ROOT%"
if not defined SDK_DIR if exist "%LOCALAPPDATA%\Android\Sdk" set "SDK_DIR=%LOCALAPPDATA%\Android\Sdk"
if defined SDK_DIR (
  set "SDK_DIR=%SDK_DIR:/=\%"
  if "%SDK_DIR:~-1%"=="\" set "SDK_DIR=%SDK_DIR:~0,-1%"
)
exit /b 0

:find_platform
set "PLAT_DIR="
if exist "%SDK_DIR%\platforms\android-%REQUIRED_SDK%\android.jar" set "PLAT_DIR=android-%REQUIRED_SDK%"
if not defined PLAT_DIR (
  for /f "delims=" %%P in ('dir /b "%SDK_DIR%\platforms" 2^>nul ^| findstr /R /C:"^android-%REQUIRED_SDK%$" /C:"^android-%REQUIRED_SDK%\."') do if not defined PLAT_DIR set "PLAT_DIR=%%P"
)
exit /b 0

:find_build_tools
rem 优先用期望版本，否则取已安装的最高版本（仅用于 aapt2 / apksigner 检查）
set "BT_VERSION="
if exist "%SDK_DIR%\build-tools\%REQUIRED_BUILD_TOOLS%\aapt2.exe" set "BT_VERSION=%REQUIRED_BUILD_TOOLS%"
if not defined BT_VERSION (
  for /f "delims=" %%B in ('dir /b /a:d /o:n "%SDK_DIR%\build-tools" 2^>nul') do if exist "%SDK_DIR%\build-tools\%%B\aapt2.exe" set "BT_VERSION=%%B"
)
exit /b 0

:warn
set "_M=%~1"
echo %C_YEL%[WARN]%C_RST% !_M!
exit /b 0
