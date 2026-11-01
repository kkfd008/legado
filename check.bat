@echo off
chcp 65001 >nul 2>nul
goto :legado_main
rem  [解析说明] 第 3 行的 goto 不能删：cmd 在文件内部切换 chcp 后，缓冲区解码会错位，
rem  导致后续注释/命令被从中间截断执行（报「不是内部或外部命令」）。先 goto 让 cmd 用新
rem  代码页重新扫描本文件，是让 UTF-8 中文批处理稳定运行的关键。
rem ============================================================================
rem  legado - check.bat   构建环境检测（只读：不安装、不写文件、不改配置）
rem
rem  用法：check.bat [--no-color] [--no-net]
rem        --no-color   关闭彩色输出
rem        --no-net     跳过网络连通性检测
rem  退出码：0 = 环境就绪；N 大于 0 表示未通过（FAIL）项的数量
rem
rem  本项目硬性要求（与 build.gradle / gradle\libs.versions.toml 对齐）：
rem    JDK 17        app/build.gradle 用 jvmToolchain 17，高版本会报 No matching toolchains
rem    Gradle 8.13   mise.toml 与 gradle\wrapper 双重固定
rem    AGP 8.13.2    Kotlin 2.3.0   compileSdk 36   minSdk 21
rem    SDK Platform 36 + Build-Tools 36.0.0
rem    app\debug.keystore  debug 签名指纹必须与官方一致，否则无法覆盖安装
rem ============================================================================

:legado_main
setlocal EnableExtensions EnableDelayedExpansion
title legado - 构建环境检测（read-only）

set "ESC="

set "PROJECT_DIR=%~dp0"
for %%I in ("%PROJECT_DIR%.") do set "PROJECT_ROOT=%%~fI"

rem ---------- 期望版本（改版本时同步改这里）----------
set "REQUIRED_JAVA=17"
set "REQUIRED_SDK=36"
set "REQUIRED_BUILD_TOOLS=36.0.0"
set "EXPECTED_GRADLE=8.13"
set "EXPECTED_AGP=8.13.2"
set "EXPECTED_KOTLIN=2.3.0"
rem 官方 legado debug.keystore 的 SHA-256 指纹，不一致会导致新包无法覆盖旧包安装
set "EXPECT_DEBUG_KEY_SHA=FA:BD:39:70:66:08:66:65:96:4A:A7:16:3E:E3:3D:DC:E6:7E:CA:12:4E:9F:5B:23:7B:16:BF:32:28:B1:13:2D"
set "GRADLE_USER_HOME_DEFAULT=%USERPROFILE%\.gradle"
set "APK_REL=app\build\outputs\apk\app\debug"

rem ---------- 参数 ----------
set "USE_COLOR=1"
set "CHECK_NET=1"
for %%A in (%*) do (
  if /i "%%~A"=="--no-color" set "USE_COLOR=0"
  if /i "%%~A"=="--no-net"   set "CHECK_NET=0"
)

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

rem ---------- 计数器 ----------
set /a N_OK=0
set /a N_WARN=0
set /a N_FAIL=0
set "MISSING="

echo.
echo %C_CYA%============================================================%C_RST%
echo %C_CYA%  legado 构建环境检测（只读 · 不修改任何内容）%C_RST%
echo %C_CYA%============================================================%C_RST%
echo   项目目录   %PROJECT_DIR%
echo   要求       JDK %REQUIRED_JAVA% ^| Platform %REQUIRED_SDK% ^| Build-Tools %REQUIRED_BUILD_TOOLS% ^| Gradle %EXPECTED_GRADLE%
echo   构建目标   :app:assembleAppDebug  →  %APK_REL%\legado_app_*.apk
echo.

rem =========================================================================
echo [1/16] mise（JDK 与 Gradle 的版本管理器 · 必需）
rem =========================================================================
where mise >nul 2>nul
if errorlevel 1 (
  call :fail "未找到 mise：本项目的 JDK 与 Gradle 版本由 mise.toml 统一管理"
  call :hint "安装：winget install jdx.mise"
  call :hint "或参考 https://mise.jdx.dev/installing-mise.html"
  set "MISSING=!MISSING! mise"
) else (
  for /f "delims=" %%V in ('mise --version 2^>nul') do if not defined MISE_VER set "MISE_VER=%%V"
  call :ok "mise 可用（!MISE_VER!）"
  if exist "%PROJECT_DIR%mise.toml" (
    set "TOML_JAVA="
    set "TOML_GRADLE="
    for /f "usebackq tokens=1,2 delims== " %%A in ("%PROJECT_DIR%mise.toml") do (
      if /i "%%A"=="java"   set "TOML_JAVA=%%B"
      if /i "%%A"=="gradle" set "TOML_GRADLE=%%B"
    )
    set "TOML_JAVA=!TOML_JAVA:"=!"
    set "TOML_GRADLE=!TOML_GRADLE:"=!"
    echo       %C_DIM%mise.toml：java=!TOML_JAVA!  gradle=!TOML_GRADLE!%C_RST%
  ) else (
    call :warn "缺少 mise.toml，JDK / Gradle 版本将不受项目约束"
  )
)

rem =========================================================================
echo.
echo [2/16] JDK %REQUIRED_JAVA%（jvmToolchain 固定版本）
rem =========================================================================
where java.exe >nul 2>nul
if errorlevel 1 (
  call :fail "PATH 中没有 java.exe（mise 的 shim 未生效或 JDK 未安装）"
  call :hint "修复：运行  deploy.bat  ，或手动  mise use java@%REQUIRED_JAVA%"
  set "MISSING=!MISSING! jdk%REQUIRED_JAVA%"
) else (
  call :detect_java
  if "!JAVA_MAJOR!"=="" (
    call :fail "无法解析 Java 版本，原始输出：!JAVA_VER_LINE!"
    set "MISSING=!MISSING! jdk%REQUIRED_JAVA%"
  ) else if not "!JAVA_MAJOR!"=="%REQUIRED_JAVA%" (
    call :fail "当前 Java 为 !JAVA_MAJOR!（!JAVA_VER_NUM!），本项目要求 JDK %REQUIRED_JAVA%"
    call :hint "app\build.gradle 固定 jvmToolchain(%REQUIRED_JAVA%)，且未启用 toolchain 自动下载"
    call :hint "JDK 更高版本会报：No matching toolchains found for requested specification"
    call :hint "修复：运行  deploy.bat  ，或手动  mise use java@%REQUIRED_JAVA%"
    set "MISSING=!MISSING! jdk%REQUIRED_JAVA%"
  ) else (
    call :ok "Java !JAVA_MAJOR!（!JAVA_VER_NUM!）符合 JDK %REQUIRED_JAVA% 要求"
    if defined JAVA_HOME echo       %C_DIM%JAVA_HOME=!JAVA_HOME!%C_RST%
  )
)

rem =========================================================================
echo.
echo [3/16] Gradle %EXPECTED_GRADLE%（mise 提供）
rem =========================================================================
set "GRADLE_CUR="
for /f "tokens=1,2" %%A in ('gradle --version 2^>nul ^| findstr /B /C:"Gradle"') do if not defined GRADLE_CUR set "GRADLE_CUR=%%B"
if not defined GRADLE_CUR (
  call :fail "gradle 不在 PATH 上，应由 mise 提供 %EXPECTED_GRADLE%"
  call :hint "修复：运行  deploy.bat  ，或手动  mise use gradle@%EXPECTED_GRADLE%"
  set "MISSING=!MISSING! gradle%EXPECTED_GRADLE%"
) else if not "!GRADLE_CUR!"=="%EXPECTED_GRADLE%" (
  call :fail "Gradle 版本为 !GRADLE_CUR!，本项目要求 %EXPECTED_GRADLE%"
  call :hint "修复：运行  deploy.bat  ，或手动  mise use gradle@%EXPECTED_GRADLE%"
  set "MISSING=!MISSING! gradle%EXPECTED_GRADLE%"
) else (
  call :ok "Gradle !GRADLE_CUR! 符合要求（mise 管理）"
)
rem 顺带核对 AGP / Kotlin 版本，确认脚本里的期望值没有过期
if exist "%PROJECT_DIR%gradle\libs.versions.toml" (
  set "TOML_AGP="
  set "TOML_KOTLIN="
  for /f "usebackq tokens=1,* delims== " %%A in ("%PROJECT_DIR%gradle\libs.versions.toml") do (
    if /i "%%A"=="agp"    set "TOML_AGP=%%B"
    if /i "%%A"=="kotlin" set "TOML_KOTLIN=%%B"
  )
  set "TOML_AGP=!TOML_AGP:"=!"
  set "TOML_KOTLIN=!TOML_KOTLIN:"=!"
  if /i "!TOML_AGP!"=="%EXPECTED_AGP%" (
    call :ok "AGP !TOML_AGP!（匹配脚本期望值）"
  ) else (
    call :warn "libs.versions.toml 中 AGP 为 !TOML_AGP!，脚本期望 %EXPECTED_AGP%（脚本常量可能已过期）"
  )
  if /i "!TOML_KOTLIN!"=="%EXPECTED_KOTLIN%" (
    call :ok "Kotlin !TOML_KOTLIN!（匹配脚本期望值）"
  ) else (
    call :warn "libs.versions.toml 中 Kotlin 为 !TOML_KOTLIN!，脚本期望 %EXPECTED_KOTLIN%（脚本常量可能已过期）"
  )
)

rem =========================================================================
echo.
echo [4/16] Android SDK 根目录
rem =========================================================================
call :resolve_sdk
if not defined SDK_DIR (
  call :fail "未定位到 Android SDK 目录"
  call :hint "已尝试：local.properties 的 sdk.dir、ANDROID_HOME、ANDROID_SDK_ROOT、默认路径"
  call :hint "修复：运行  deploy.bat"
  set "MISSING=!MISSING! sdk-root"
) else if not exist "%SDK_DIR%\" (
  call :fail "SDK 目录不存在：%SDK_DIR%"
  set "MISSING=!MISSING! sdk-root"
) else (
  call :ok "Android SDK：%SDK_DIR%"
)

rem =========================================================================
echo.
echo [5/16] SDK Platform %REQUIRED_SDK%（compileSdk / targetSdk = %REQUIRED_SDK%）
rem =========================================================================
if not defined SDK_DIR (
  call :skip "跳过：SDK 根目录未定位"
) else (
  call :find_platform
  if defined PLAT_DIR (
    call :ok "已安装 platforms\!PLAT_DIR!（满足 compileSdk %REQUIRED_SDK%）"
  ) else (
    call :fail "缺少 SDK Platform %REQUIRED_SDK%（compileSdk = %REQUIRED_SDK%）"
    call :hint "本机已安装的 platform："
    for /f "delims=" %%P in ('dir /b "%SDK_DIR%\platforms" 2^>nul') do echo       %C_DIM%%%P%C_RST%
    call :hint "修复：运行  deploy.bat"
    set "MISSING=!MISSING! platform-%REQUIRED_SDK%"
  )
)

rem =========================================================================
echo.
echo [6/16] Build-Tools %REQUIRED_BUILD_TOOLS%
rem =========================================================================
if not defined SDK_DIR (
  call :skip "跳过：SDK 根目录未定位"
) else if exist "%SDK_DIR%\build-tools\%REQUIRED_BUILD_TOOLS%\" (
  call :ok "已安装 build-tools\%REQUIRED_BUILD_TOOLS%"
) else (
  set "HAS_BT="
  for /f "delims=" %%B in ('dir /b "%SDK_DIR%\build-tools" 2^>nul') do set "HAS_BT=!HAS_BT! %%B"
  if defined HAS_BT (
    call :warn "没有 build-tools\%REQUIRED_BUILD_TOOLS%，已装其他版本：!HAS_BT!"
    call :hint "AGP 会尝试自动补装；若构建报 Failed to find Build Tools，运行  deploy.bat"
  ) else (
    call :fail "未安装任何 build-tools"
    call :hint "修复：运行  deploy.bat"
    set "MISSING=!MISSING! build-tools"
  )
)

rem =========================================================================
echo.
echo [7/16] Platform-Tools（编译非必需 · 安装/调试 APK 需要）
rem =========================================================================
if not defined SDK_DIR (
  call :skip "跳过：SDK 根目录未定位"
) else if exist "%SDK_DIR%\platform-tools\adb.exe" (
  call :ok "已安装 platform-tools（adb.exe）"
) else (
  call :warn "缺少 platform-tools，不影响编译，但无法用 adb 安装/调试"
  call :hint "修复：运行  deploy.bat"
  set "MISSING=!MISSING! platform-tools"
)

rem =========================================================================
echo.
echo [8/16] sdkmanager（cmdline-tools · deploy.bat 安装 SDK 组件依赖它）
rem =========================================================================
call :find_sdkmanager
if defined SDKMGR (
  call :ok "sdkmanager：%SDKMGR%"
) else (
  call :warn "未找到 sdkmanager（cmdline-tools），deploy.bat 将无法自动安装 SDK 组件"
  call :hint "deploy.bat 会下载官方 commandlinetools 补装（Android SDK 不由 mise 管理）"
  call :hint "手动方案：Android Studio / SDK Manager / SDK Tools 勾选 Command-line Tools"
  set "MISSING=!MISSING! sdkmanager"
)

rem =========================================================================
echo.
echo [9/16] Gradle Wrapper
rem =========================================================================
if not exist "%PROJECT_DIR%gradlew.bat" (
  call :fail "缺少 gradlew.bat：%PROJECT_DIR%gradlew.bat"
  set "MISSING=!MISSING! gradle-wrapper"
) else (
  findstr /C:"gradle-%EXPECTED_GRADLE%-bin.zip" "%PROJECT_DIR%gradle\wrapper\gradle-wrapper.properties" >nul 2>nul
  if errorlevel 1 (
    call :warn "wrapper 指向的 Gradle 不是 %EXPECTED_GRADLE%（以仓库配置为准）"
  ) else (
    call :ok "wrapper 已固定 Gradle %EXPECTED_GRADLE%，与 mise 管理的版本一致"
  )
)

rem =========================================================================
echo.
echo [10/16] Gradle 发行版本地缓存
rem =========================================================================
set "GRADLE_HOME_DIR=%GRADLE_USER_HOME%"
if not defined GRADLE_HOME_DIR set "GRADLE_HOME_DIR=%GRADLE_USER_HOME_DEFAULT%"
if exist "%GRADLE_HOME_DIR%\wrapper\dists\gradle-%EXPECTED_GRADLE%-bin" (
  call :ok "Gradle %EXPECTED_GRADLE% 已缓存于 %GRADLE_HOME_DIR%\wrapper\dists"
) else (
  call :warn "本地未缓存 Gradle %EXPECTED_GRADLE%，首次构建需联网下载（services.gradle.org）"
)

rem =========================================================================
echo.
echo [11/16] GRADLE_USER_HOME 盘符（与项目同盘，否则 KSP 崩溃）
rem =========================================================================
set "PROJ_DRIVE=%PROJECT_DIR:~0,1%"
set "GH_DRIVE=%GRADLE_HOME_DIR:~0,1%"
if /i "!PROJ_DRIVE!"=="!GH_DRIVE!" (
  call :ok "GRADLE_USER_HOME 与项目同在 !GH_DRIVE!: 盘（!GRADLE_HOME_DIR!）"
) else (
  call :fail "GRADLE_USER_HOME 在 !GH_DRIVE!: 盘，项目在 !PROJ_DRIVE!: 盘 —— 不同盘符会让 KSP 崩"
  call :hint "报错原文：java.lang.IllegalArgumentException: this and base files have different roots"
  call :hint "修复：设置用户环境变量 GRADLE_USER_HOME=!PROJ_DRIVE!:\dev\env\gradle-home（重开终端生效）"
  set "MISSING=!MISSING! gradle-home-drive"
)

rem =========================================================================
echo.
echo [12/16] local.properties（sdk.dir）
rem =========================================================================
if exist "%PROJECT_DIR%local.properties" (
  set "LP_SDK="
  for /f "usebackq tokens=1,* delims==" %%A in ("%PROJECT_DIR%local.properties") do if /i "%%A"=="sdk.dir" set "LP_SDK=%%B"
  if defined LP_SDK (
    call :ok "local.properties 存在，sdk.dir=!LP_SDK!"
  ) else (
    call :warn "local.properties 存在但没有 sdk.dir"
    call :hint "修复：运行  deploy.bat  自动写入"
  )
) else (
  if defined SDK_DIR (
    call :warn "local.properties 不存在；当前依赖 ANDROID_HOME/ANDROID_SDK_ROOT，Gradle 仍可定位 SDK"
    call :hint "建议运行  deploy.bat  生成本地 sdk.dir（该文件已被 .gitignore 忽略）"
  ) else (
    call :fail "local.properties 不存在，且没有任何 SDK 路径来源"
    set "MISSING=!MISSING! local.properties"
  )
)

rem =========================================================================
echo.
echo [13/16] debug 签名（app\debug.keystore 指纹一致性）
rem =========================================================================
if not exist "%PROJECT_DIR%app\debug.keystore" (
  call :fail "缺少 app\debug.keystore：debug 包无法签名"
  call :hint "该文件由仓库提供，请检查是否被误删（git checkout -- app/debug.keystore）"
  set "MISSING=!MISSING! debug.keystore"
) else (
  call :find_keytool
  if not defined KEYTOOL (
    call :skip "跳过指纹校验：PATH 上找不到 keytool"
  ) else (
  set "KEY_FILE=%TEMP%\legado-check-key-%RANDOM%%RANDOM%.txt"
  call "!KEYTOOL!" -list -v -keystore "%PROJECT_DIR%app\debug.keystore" -storepass android -alias androiddebugkey >"!KEY_FILE!" 2>&1
  set "KEY_SHA="
  for /f "tokens=2" %%A in ('findstr /C:"SHA256:" "!KEY_FILE!" 2^>nul') do if not defined KEY_SHA set "KEY_SHA=%%A"
  if exist "!KEY_FILE!" del /q "!KEY_FILE!" >nul 2>nul
  if not defined KEY_SHA (
    call :warn "无法读取 debug.keystore 指纹（keytool 输出为空）"
  ) else if /i "!KEY_SHA!"=="%EXPECT_DEBUG_KEY_SHA%" (
    call :ok "debug.keystore 指纹与官方一致（!KEY_SHA!）"
  ) else (
    call :fail "debug.keystore 指纹与官方不一致，新包无法覆盖安装旧版"
    call :hint "当前：!KEY_SHA!"
    call :hint "期望：%EXPECT_DEBUG_KEY_SHA%"
    set "MISSING=!MISSING! debug-key-sha"
  )
  )
)

rem =========================================================================
echo.
echo [14/16] google-services.json（Firebase 插件必需）
rem =========================================================================
if exist "%PROJECT_DIR%app\google-services.json" (
  call :ok "app\google-services.json 存在"
) else (
  call :fail "缺少 app\google-services.json，com.google.gms.google-services 插件会让配置阶段直接失败"
  call :hint "该文件由仓库提供，请检查是否被误删"
  set "MISSING=!MISSING! google-services.json"
)

rem =========================================================================
echo.
echo [15/16] git 历史（versionCode = 10000 + 提交数）
rem =========================================================================
where git.exe >nul 2>nul
if errorlevel 1 (
  call :warn "未找到 git，无法计算 versionCode"
) else (
  set "SHALLOW="
  for /f %%A in ('git -C "%PROJECT_ROOT%" rev-parse --is-shallow-repository 2^>nul') do set "SHALLOW=%%A"
  set "COMMIT_COUNT="
  for /f %%A in ('git -C "%PROJECT_ROOT%" rev-list HEAD --count 2^>nul') do set "COMMIT_COUNT=%%A"
  if "!SHALLOW!"=="true" (
    call :fail "仓库是浅克隆，提交数偏小会导致 versionCode 倒退、无法覆盖安装"
    call :hint "修复：git fetch --unshallow origin  （deploy.bat 也会提示）"
    set "MISSING=!MISSING! git-shallow"
  ) else if not defined COMMIT_COUNT (
    call :warn "无法读取提交数（可能不在 git 仓库内）"
  ) else (
    set /a VERSION_CODE=10000+!COMMIT_COUNT!
    call :ok "提交数 !COMMIT_COUNT!，本次构建 versionCode = !VERSION_CODE!"
    set "DIRTY="
    for /f "delims=" %%A in ('git -C "%PROJECT_ROOT%" status --porcelain 2^>nul') do if not defined DIRTY set "DIRTY=%%A"
    if defined DIRTY (
      call :warn "工作区有未提交改动（仅影响内容，不改变 versionCode）"
    )
  )
)

rem =========================================================================
echo.
echo [16/16] 网络连通性（首次构建需下载依赖）
rem =========================================================================
if "%CHECK_NET%"=="0" (
  call :skip "已用 --no-net 跳过"
) else (
  where curl >nul 2>nul
  if errorlevel 1 (
    call :skip "未找到 curl，跳过"
  ) else (
    call :probe "https://dl.google.com"        "dl.google.com（Google Maven / SDK）"
    call :probe "https://services.gradle.org"  "services.gradle.org（Gradle 发行版）"
    call :probe "https://repo.maven.apache.org" "repo.maven.apache.org（Maven Central）"
  )
)

rem =========================================================================
echo.
echo %C_CYA%============================================================%C_RST%
echo   检测摘要      %C_GRN%OK: %N_OK%%C_RST%    %C_YEL%WARN: %N_WARN%%C_RST%    %C_RED%FAIL: %N_FAIL%%C_RST%
echo %C_CYA%============================================================%C_RST%
if defined MISSING (
  echo   缺失项：%C_RED%%MISSING%%C_RST%
  echo.
  echo   下一步：运行  %C_GRN%deploy.bat%C_RST%  安装上述缺失项
) else (
  echo   %C_GRN%环境就绪，可以运行  build.bat  开始编译。%C_RST%
)
echo.
endlocal & exit /b %N_FAIL%

rem ============================ Subroutines ============================

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
rem 优先级：local.properties 的 sdk.dir → ANDROID_HOME → ANDROID_SDK_ROOT → 默认路径
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
rem API 36 的目录名可能带小版本号（android-36 / android-36.0 / android-36.1）
set "PLAT_DIR="
if exist "%SDK_DIR%\platforms\android-%REQUIRED_SDK%\android.jar" set "PLAT_DIR=android-%REQUIRED_SDK%"
if not defined PLAT_DIR (
  for /f "delims=" %%P in ('dir /b "%SDK_DIR%\platforms" 2^>nul ^| findstr /R /C:"^android-%REQUIRED_SDK%$" /C:"^android-%REQUIRED_SDK%\."') do if not defined PLAT_DIR set "PLAT_DIR=%%P"
)
exit /b 0

:find_sdkmanager
set "SDKMGR="
if defined SDK_DIR (
  for %%V in (latest 20 19 18 17 16 15 14 13 12 11 10 9 8) do (
    if exist "!SDK_DIR!\cmdline-tools\%%V\bin\sdkmanager.bat" if not defined SDKMGR set "SDKMGR=!SDK_DIR!\cmdline-tools\%%V\bin\sdkmanager.bat"
  )
)
if not defined SDKMGR (
  for /f "delims=" %%P in ('where sdkmanager 2^>nul') do if not defined SDKMGR set "SDKMGR=%%P"
)
exit /b 0

:find_keytool
rem JAVA_HOME 优先，其次 PATH（mise 的 shim 也会提供 keytool）
set "KEYTOOL="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\keytool.exe" set "KEYTOOL=%JAVA_HOME%\bin\keytool.exe"
if not defined KEYTOOL (
  for /f "delims=" %%P in ('where keytool 2^>nul') do if not defined KEYTOOL set "KEYTOOL=%%P"
)
exit /b 0

:probe
rem %1 = URL, %2 = 名称
where curl >nul 2>nul
if errorlevel 1 exit /b 0
curl -s -o nul --max-time 6 -I "%~1" >nul 2>&1
if errorlevel 1 (
  call :warn "不可达：%~2"
) else (
  call :ok "可达：%~2"
)
exit /b 0

:ok
set "_M=%~1"
set /a N_OK+=1
echo %C_GRN%[ OK ]%C_RST% !_M!
exit /b 0

:warn
set "_M=%~1"
set /a N_WARN+=1
echo %C_YEL%[WARN]%C_RST% !_M!
exit /b 0

:fail
set "_M=%~1"
set /a N_FAIL+=1
echo %C_RED%[FAIL]%C_RST% !_M!
exit /b 0

:skip
set "_M=%~1"
echo %C_DIM%[SKIP]%C_RST% !_M!
exit /b 0

:hint
set "_M=%~1"
echo       %C_DIM%→ !_M!%C_RST%
exit /b 0
