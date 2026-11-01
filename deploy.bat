@echo off
chcp 65001 >nul 2>nul
goto :legado_main
rem  [解析说明] 第 3 行的 goto 不能删：cmd 在文件内部切换 chcp 后，缓冲区解码会错位，
rem  导致后续注释/命令被从中间截断执行（报「不是内部或外部命令」）。先 goto 让 cmd 用新
rem  代码页重新扫描本文件，是让 UTF-8 中文批处理稳定运行的关键。
rem ============================================================================
rem  legado - deploy.bat   构建环境部署（只装缺失项，不重复安装）
rem
rem  用法：deploy.bat [--yes] [--no-color]
rem        --yes / -y   全部询问自动回答 yes（无人值守）
rem        --no-color   关闭彩色输出
rem  退出码：0 = 部署完成；N 大于 0 表示未能自动修复的项数
rem
rem  安装内容：
rem    mise                       JDK / Gradle 版本管理器（必须）
rem    JDK 17                     mise use java@17
rem    Gradle 8.13                mise use gradle@8.13
rem    Android cmdline-tools      官方压缩包，解压到 <SDK>\cmdline-tools\latest
rem    SDK Platform 36            sdkmanager
rem    Build-Tools 36.0.0         sdkmanager
rem    Platform-Tools adb         sdkmanager
rem    SDK licenses               自动接受
rem    local.properties           sdk.dir（git 忽略的本地文件）
rem ============================================================================

:legado_main
setlocal EnableExtensions EnableDelayedExpansion
title legado - 环境部署

set "ESC="

set "PROJECT_DIR=%~dp0"
for %%I in ("%PROJECT_DIR%.") do set "PROJECT_ROOT=%%~fI"

rem ---------- 期望版本 ----------
set "REQUIRED_JAVA=17"
set "REQUIRED_SDK=36"
set "REQUIRED_BUILD_TOOLS=36.0.0"
set "EXPECTED_GRADLE=8.13"
rem 官方 commandlinetools 归档（Google 会改文件名，404 时更新此链接）
set "CMDLINE_TOOLS_URL=https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip"

rem ---------- 参数 ----------
set "ASSUME_YES=0"
set "USE_COLOR=1"
for %%A in (%*) do (
  if /i "%%~A"=="--yes" set "ASSUME_YES=1"
  if /i "%%~A"=="-y"    set "ASSUME_YES=1"
  if /i "%%~A"=="--no-color" set "USE_COLOR=0"
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

set /a N_OK=0
set /a N_WARN=0
set /a N_FAIL=0
set /a N_DONE=0

echo.
echo %C_CYA%============================================================%C_RST%
echo %C_CYA%  legado 构建环境部署（安装缺失组件）%C_RST%
echo %C_CYA%============================================================%C_RST%
echo   项目目录   %PROJECT_DIR%
echo   目标       JDK %REQUIRED_JAVA% ^| Gradle %EXPECTED_GRADLE% ^| Platform %REQUIRED_SDK% ^| Build-Tools %REQUIRED_BUILD_TOOLS%
echo.

pushd "%PROJECT_DIR%" 2>nul
if errorlevel 1 (
  call :fail "无法进入项目目录：%PROJECT_DIR%"
  goto :summary
)

rem =========================================================================
rem 前置：mise 必须存在
rem =========================================================================
where mise >nul 2>nul
if errorlevel 1 (
  call :warn "未找到 mise，尝试用 winget 安装 ..."
  where winget >nul 2>nul
  if errorlevel 1 (
    call :fail "winget 不可用，无法自动安装 mise"
    call :hint "手动安装：https://mise.jdx.dev/installing-mise.html"
    goto :summary
  )
  if "%ASSUME_YES%"=="1" (
    winget install --id jdx.mise -e --accept-source-agreements --accept-package-agreements
  ) else (
    call :confirm "使用 winget 安装 mise？"
    if "!CONFIRMED!"=="1" winget install --id jdx.mise -e --accept-source-agreements --accept-package-agreements
  )
  where mise >nul 2>nul
  if errorlevel 1 (
    call :fail "mise 仍未可用，请重开终端后重跑本脚本"
    goto :summary
  )
  call :ok "mise 安装完成（如未生效请重开终端）"
  set /a N_DONE+=1
) else (
  call :ok "mise 可用，JDK 与 Gradle 版本由 mise.toml 统一管理"
)
rem 编辑过 mise.toml 后 mise 会要求重新信任配置；trust 是幂等的
call mise trust >nul 2>&1

rem =========================================================================
rem 前置：定位 Android SDK 根目录
rem =========================================================================
call :resolve_sdk
if not defined SDK_DIR (
  echo %C_YEL%未定位到 Android SDK 目录。%C_RST%
  if "%ASSUME_YES%"=="1" (
    if exist "%LOCALAPPDATA%\Android\Sdk" (
      set "SDK_DIR=%LOCALAPPDATA%\Android\Sdk"
    ) else (
      set "SDK_DIR=%USERPROFILE%\Android\Sdk"
    )
    call :info "无人值守模式，使用默认路径：!SDK_DIR!"
  ) else (
    set /p "INPUT_SDK=请输入 Android SDK 目录（直接回车使用 %LOCALAPPDATA%\Android\Sdk）: "
    if defined INPUT_SDK (
      set "SDK_DIR=!INPUT_SDK!"
    ) else (
      set "SDK_DIR=%LOCALAPPDATA%\Android\Sdk"
    )
  )
  set "SDK_DIR=!SDK_DIR:/=\!"
  if "!SDK_DIR:~-1!"=="\" set "SDK_DIR=!SDK_DIR:~0,-1!"
)
call :info "Android SDK 目录：!SDK_DIR!"
if not exist "!SDK_DIR!" (
  call :info "目录不存在，正在创建 ..."
  mkdir "!SDK_DIR!" 2>nul
  if errorlevel 1 (
    call :fail "无法创建 SDK 目录：!SDK_DIR!"
    goto :summary
  )
)

rem =========================================================================
echo.
echo %C_CYA%[1/8] JDK %REQUIRED_JAVA%（mise 管理）%C_RST%
rem =========================================================================
set "NEED_JDK=1"
where java.exe >nul 2>nul
if not errorlevel 1 (
  call :detect_java
  if "!JAVA_MAJOR!"=="%REQUIRED_JAVA%" (
    call :ok "JDK %REQUIRED_JAVA% 已就位（!JAVA_VER_NUM!），跳过安装"
    set "NEED_JDK=0"
  ) else (
    call :warn "当前 Java 为 !JAVA_MAJOR!，需要切换到 JDK %REQUIRED_JAVA%"
  )
) else (
  call :warn "PATH 中没有 java.exe，需要安装 JDK %REQUIRED_JAVA%"
)
if "!NEED_JDK!"=="1" (
  if not "%ASSUME_YES%"=="1" call :confirm "使用 mise 安装并切换到 JDK %REQUIRED_JAVA%？"
  if "!CONFIRMED!"=="0" (
    call :warn "已跳过 JDK 安装（用户选择）"
  ) else (
    call :info "正在执行：mise use java@%REQUIRED_JAVA%  ..."
    call mise use java@%REQUIRED_JAVA%
    if errorlevel 1 (
      call :fail "mise 安装 JDK %REQUIRED_JAVA% 失败"
    ) else (
      call :detect_java
      if "!JAVA_MAJOR!"=="%REQUIRED_JAVA%" (
        call :ok "JDK %REQUIRED_JAVA% 安装完成（!JAVA_VER_NUM!）"
        set /a N_DONE+=1
      ) else (
        call :warn "mise 已安装，但当前 shell 仍解析到 Java !JAVA_MAJOR!"
        call :hint "mise 的 shim 需要新终端生效，请重开后执行 java -version 确认"
      )
    )
  )
)

rem =========================================================================
echo.
echo %C_CYA%[2/8] Gradle %EXPECTED_GRADLE%（mise 管理）%C_RST%
rem =========================================================================
set "NEED_GRADLE=1"
set "GRADLE_CUR="
for /f "tokens=1,2" %%A in ('gradle --version 2^>nul ^| findstr /B /C:"Gradle"') do if not defined GRADLE_CUR set "GRADLE_CUR=%%B"
if defined GRADLE_CUR (
  if "!GRADLE_CUR!"=="%EXPECTED_GRADLE%" (
    call :ok "Gradle %EXPECTED_GRADLE% 已就位，跳过安装"
    set "NEED_GRADLE=0"
  ) else (
    call :warn "当前 Gradle 为 !GRADLE_CUR!，需要切换到 %EXPECTED_GRADLE%"
  )
) else (
  call :warn "PATH 中没有 gradle，需要安装 Gradle %EXPECTED_GRADLE%"
)
if "!NEED_GRADLE!"=="1" (
  if not "%ASSUME_YES%"=="1" call :confirm "使用 mise 安装并切换到 Gradle %EXPECTED_GRADLE%？"
  if "!CONFIRMED!"=="0" (
    call :warn "已跳过 Gradle 安装（用户选择）"
  ) else (
    call :info "正在执行：mise use gradle@%EXPECTED_GRADLE%  ..."
    call mise use gradle@%EXPECTED_GRADLE%
    if errorlevel 1 (
      call :fail "mise 安装 Gradle %EXPECTED_GRADLE% 失败"
    ) else (
      set "GRADLE_CUR="
      for /f "tokens=1,2" %%A in ('gradle --version 2^>nul ^| findstr /B /C:"Gradle"') do if not defined GRADLE_CUR set "GRADLE_CUR=%%B"
      if "!GRADLE_CUR!"=="%EXPECTED_GRADLE%" (
        call :ok "Gradle %EXPECTED_GRADLE% 安装完成（已固定到本项目 mise.toml）"
        set /a N_DONE+=1
      ) else (
        call :warn "mise 已安装，但当前 shell 解析到 !GRADLE_CUR!"
        call :hint "请重开终端后执行 gradle --version 确认"
      )
    )
  )
)

rem =========================================================================
echo.
echo %C_CYA%[3/8] sdkmanager / Android 命令行工具%C_RST%
rem =========================================================================
call :find_sdkmanager
if defined SDKMGR (
  call :ok "sdkmanager 已就位：!SDKMGR!"
) else (
  call :warn "未找到 sdkmanager，正在下载官方 Android 命令行工具 ..."
  call :install_cmdline_tools
  call :find_sdkmanager
  if defined SDKMGR (
    call :ok "sdkmanager 安装完成：!SDKMGR!"
    set /a N_DONE+=1
  ) else (
    call :fail "无法自动安装 sdkmanager"
    call :hint "手动方案：Android Studio / SDK Manager / SDK Tools 勾选 Command-line Tools (latest)"
    call :hint "或手动解压到：!SDK_DIR!\cmdline-tools\latest"
    call :hint "下载页：https://developer.android.com/studio#command-line-tools-only"
  )
)

rem =========================================================================
echo.
echo %C_CYA%[4/8] SDK Platform %REQUIRED_SDK%%C_RST%
rem =========================================================================
call :find_platform
if defined PLAT_DIR (
  call :ok "platforms\!PLAT_DIR! 已安装，跳过"
) else if not defined SDKMGR (
  call :fail "缺少 SDK Platform %REQUIRED_SDK%，但 sdkmanager 不可用，无法安装"
) else (
  if not "%ASSUME_YES%"=="1" call :confirm "安装 SDK Platform %REQUIRED_SDK%（约 100-200MB）？"
  if "!CONFIRMED!"=="0" (
    call :warn "已跳过 SDK Platform 安装（用户选择）"
  ) else (
    call :accept_licenses
    call :resolve_platform_pkg
    call :info "正在安装 !PLAT_PKG! ..."
    call "!SDKMGR!" --sdk_root="!SDK_DIR!" "!PLAT_PKG!"
    if errorlevel 1 (
      call :fail "!PLAT_PKG! 安装失败"
    ) else (
      call :find_platform
      if defined PLAT_DIR (
        call :ok "platforms\!PLAT_DIR! 安装完成"
        set /a N_DONE+=1
      ) else (
        call :warn "sdkmanager 返回成功但未找到 android.jar，请复查 !SDK_DIR!\platforms"
      )
    )
  )
)

rem =========================================================================
echo.
echo %C_CYA%[5/8] Build-Tools %REQUIRED_BUILD_TOOLS%%C_RST%
rem =========================================================================
if exist "!SDK_DIR!\build-tools\%REQUIRED_BUILD_TOOLS%\" (
  call :ok "build-tools\%REQUIRED_BUILD_TOOLS% 已安装，跳过"
) else if not defined SDKMGR (
  call :fail "缺少 Build-Tools %REQUIRED_BUILD_TOOLS%，但 sdkmanager 不可用，无法安装"
) else (
  if not "%ASSUME_YES%"=="1" call :confirm "安装 Build-Tools %REQUIRED_BUILD_TOOLS%？"
  if "!CONFIRMED!"=="0" (
    call :warn "已跳过 Build-Tools 安装（用户选择）"
  ) else (
    call :accept_licenses
    call :info "正在安装 build-tools;%REQUIRED_BUILD_TOOLS% ..."
    call "!SDKMGR!" --sdk_root="!SDK_DIR!" "build-tools;%REQUIRED_BUILD_TOOLS%"
    if errorlevel 1 (
      call :fail "Build-Tools %REQUIRED_BUILD_TOOLS% 安装失败"
    ) else (
      call :ok "Build-Tools %REQUIRED_BUILD_TOOLS% 安装完成"
      set /a N_DONE+=1
    )
  )
)

rem =========================================================================
echo.
echo %C_CYA%[6/8] Platform-Tools%C_RST%
rem =========================================================================
if exist "!SDK_DIR!\platform-tools\adb.exe" (
  call :ok "platform-tools 已安装，跳过"
) else if not defined SDKMGR (
  call :warn "缺少 platform-tools（不影响编译），sdkmanager 不可用，跳过"
) else (
  if not "%ASSUME_YES%"=="1" call :confirm "安装 platform-tools（adb，约 10MB）？"
  if "!CONFIRMED!"=="0" (
    call :warn "已跳过 platform-tools 安装（用户选择）"
  ) else (
    call :accept_licenses
    call :info "正在安装 platform-tools ..."
    call "!SDKMGR!" --sdk_root="!SDK_DIR!" "platform-tools"
    if errorlevel 1 (
      call :warn "platform-tools 安装失败（不影响编译）"
    ) else (
      call :ok "platform-tools 安装完成"
      set /a N_DONE+=1
    )
  )
)

rem =========================================================================
echo.
echo %C_CYA%[7/8] local.properties（写入 sdk.dir）%C_RST%
rem =========================================================================
call :write_local_properties

rem =========================================================================
echo.
echo %C_CYA%[8/8] 仓库完整性（签名 / Firebase / git 历史）%C_RST%
rem =========================================================================
if exist "%PROJECT_DIR%app\debug.keystore" (
  call :ok "app\debug.keystore 存在"
) else (
  call :fail "缺少 app\debug.keystore"
  call :hint "修复：git checkout -- app/debug.keystore"
)
if exist "%PROJECT_DIR%app\google-services.json" (
  call :ok "app\google-services.json 存在"
) else (
  call :fail "缺少 app\google-services.json，Firebase 插件会导致配置阶段失败"
  call :hint "修复：git checkout -- app/google-services.json"
)
where git.exe >nul 2>nul
if errorlevel 1 (
  call :warn "未找到 git，跳过 git 历史检查"
) else (
  set "SHALLOW="
  for /f %%A in ('git -C "%PROJECT_ROOT%" rev-parse --is-shallow-repository 2^>nul') do set "SHALLOW=%%A"
  if "!SHALLOW!"=="true" (
    call :warn "仓库是浅克隆，versionCode = 10000 + 提交数 会偏小，可能导致无法覆盖安装"
    if "%ASSUME_YES%"=="1" (
      call :info "正在补全 git 历史：git fetch --unshallow origin ..."
      git -C "%PROJECT_ROOT%" fetch --unshallow origin
      if errorlevel 1 (call :fail "git fetch --unshallow 失败，请手动处理") else (call :ok "git 历史已补全" & set /a N_DONE+=1)
    ) else (
      call :confirm "现在补全 git 历史（git fetch --unshallow origin）？"
      if "!CONFIRMED!"=="1" (
        git -C "%PROJECT_ROOT%" fetch --unshallow origin
        if errorlevel 1 (call :fail "git fetch --unshallow 失败，请手动处理") else (call :ok "git 历史已补全" & set /a N_DONE+=1)
      ) else (
        call :warn "已跳过 git 历史补全"
      )
    )
  ) else (
    call :ok "git 历史完整（非浅克隆）"
  )
)

:summary
echo.
echo %C_CYA%============================================================%C_RST%
echo   部署摘要      %C_GRN%新安装: %N_DONE%%C_RST%    %C_YEL%WARN: %N_WARN%%C_RST%    %C_RED%FAIL: %N_FAIL%%C_RST%
echo %C_CYA%============================================================%C_RST%
if "%N_FAIL%"=="0" (
  echo   %C_GRN%环境部署完成。下一步：运行  check.bat  复核，再运行  build.bat  编译。%C_RST%
) else (
  echo   %C_RED%有 %N_FAIL% 项未能自动修复，请按上面的提示手动处理后重跑本脚本。%C_RST%
)
echo.
popd 2>nul
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
set "SDK_DIR="
if exist "%PROJECT_DIR%local.properties" (
  for /f "usebackq tokens=1,* delims==" %%A in ("%PROJECT_DIR%local.properties") do (
    if /i "%%A"=="sdk.dir" set "SDK_DIR=%%B"
  )
)
if not defined SDK_DIR if defined ANDROID_HOME set "SDK_DIR=%ANDROID_HOME%"
if not defined SDK_DIR if defined ANDROID_SDK_ROOT set "SDK_DIR=%ANDROID_SDK_ROOT%"
if defined SDK_DIR (
  set "SDK_DIR=%SDK_DIR:/=\%"
  if "%SDK_DIR:~-1%"=="\" set "SDK_DIR=%SDK_DIR:~0,-1%"
)
exit /b 0

:find_platform
rem API 36 的目录名可能带小版本号（android-36 / android-36.0 / android-36.1）
set "PLAT_DIR="
if exist "!SDK_DIR!\platforms\android-%REQUIRED_SDK%\android.jar" set "PLAT_DIR=android-%REQUIRED_SDK%"
if not defined PLAT_DIR (
  for /f "delims=" %%P in ('dir /b "!SDK_DIR!\platforms" 2^>nul ^| findstr /R /C:"^android-%REQUIRED_SDK%$" /C:"^android-%REQUIRED_SDK%\."') do if not defined PLAT_DIR set "PLAT_DIR=%%P"
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

:resolve_platform_pkg
rem SDK 仓库里 36 的包名可能是 platforms;android-36 / 36.0 / 36.1，探测最高稳定版
set "PLAT_PKG=platforms;android-%REQUIRED_SDK%"
if not defined SDKMGR exit /b 0
set "PLAT_EXACT="
set "PLAT_BEST="
rem 先把 --list 落盘：在 for /f 里直接管道 sdkmanager 会报路径语法错误
set "PKG_LIST=%TEMP%\legado-sdklist-%RANDOM%.txt"
call "!SDKMGR!" --sdk_root="!SDK_DIR!" --list > "!PKG_LIST!" 2>nul
for /f "usebackq tokens=1" %%P in (`findstr /R /C:"platforms;android-%REQUIRED_SDK%" "!PKG_LIST!"`) do (
  set "CAND=%%P"
  if /i "!CAND!"=="platforms;android-%REQUIRED_SDK%" (
    set "PLAT_EXACT=!CAND!"
  ) else (
    set "IS_BETA=0"
    if /i not "!CAND:beta=!"=="!CAND!" set "IS_BETA=1"
    if /i not "!CAND:-rc=!"=="!CAND!" set "IS_BETA=1"
    if "!IS_BETA!"=="0" set "PLAT_BEST=!CAND!"
  )
)
if defined PLAT_EXACT (
  set "PLAT_PKG=!PLAT_EXACT!"
) else if defined PLAT_BEST (
  set "PLAT_PKG=!PLAT_BEST!"
)
del /q "!PKG_LIST!" >nul 2>&1
exit /b 0

:accept_licenses
if exist "!SDK_DIR!\licenses\android-sdk-license" exit /b 0
if not defined SDKMGR exit /b 0
call :info "正在接受 SDK 许可协议 ..."
for /l %%i in (1,1,8) do echo y| call "!SDKMGR!" --sdk_root="!SDK_DIR!" --licenses >nul 2>&1
exit /b 0

:install_cmdline_tools
rem 下载官方 commandlinetools 压缩包，解压到 <SDK>\cmdline-tools\latest
if not defined SDK_DIR (
  call :warn "SDK 目录未定义，无法安装 commandlinetools"
  exit /b 1
)
where curl >nul 2>nul
if errorlevel 1 (
  call :warn "未找到 curl，无法下载 commandlinetools"
  exit /b 1
)
set "CMDLINE_ZIP=%TEMP%\legado-cmdline-tools.zip"
rem 在 SDK 树内部解压：cmd 的 move 不能跨盘移动目录
set "CMDLINE_TMP=%SDK_DIR%\cmdline-tools\_unzip-%RANDOM%"
call :info "下载 %CMDLINE_TOOLS_URL% ..."
curl -L -s -S -o "!CMDLINE_ZIP!" "%CMDLINE_TOOLS_URL%" >nul 2>&1
if errorlevel 1 (
  call :warn "下载失败（链接可能已失效，请更新脚本顶部的 CMDLINE_TOOLS_URL）"
  exit /b 1
)
if not exist "%CMDLINE_ZIP%" (
  call :warn "下载结束但未找到压缩包"
  exit /b 1
)
if not exist "%CMDLINE_TMP%" mkdir "%CMDLINE_TMP%" 2>nul

rem 优先用 Windows 自带 tar.exe（bsdtar，解 zip 很快）
rem PATH 上更靠前的可能是 Git 的 GNU tar，读不了 zip，所以写绝对路径
set "TAR_EXE=%SystemRoot%\System32\tar.exe"
set "UNZIP_OK=0"
if exist "%TAR_EXE%" (
  call :info "正在解压（Windows tar）..."
  "%TAR_EXE%" -xf "%CMDLINE_ZIP%" -C "%CMDLINE_TMP%" >nul 2>&1
  if not errorlevel 1 set "UNZIP_OK=1"
)
if "%UNZIP_OK%"=="0" (
  call :info "正在解压（PowerShell，较慢，请耐心等待）..."
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Expand-Archive -LiteralPath '%CMDLINE_ZIP%' -DestinationPath '%CMDLINE_TMP%' -Force"
  if not errorlevel 1 set "UNZIP_OK=1"
)
if "%UNZIP_OK%"=="0" (
  call :warn "解压失败"
  exit /b 1
)
if not exist "%CMDLINE_TMP%\cmdline-tools\bin\sdkmanager.bat" (
  call :warn "解压结果中没有 cmdline-tools\bin\sdkmanager.bat"
  exit /b 1
)

if not exist "%SDK_DIR%\cmdline-tools" mkdir "%SDK_DIR%\cmdline-tools" 2>nul
if exist "%SDK_DIR%\cmdline-tools\latest" rmdir /s /q "%SDK_DIR%\cmdline-tools\latest" 2>nul
move /y "%CMDLINE_TMP%\cmdline-tools" "%SDK_DIR%\cmdline-tools\latest" >nul 2>&1
if errorlevel 1 (
  call :warn "移动到 %SDK_DIR%\cmdline-tools\latest 失败"
  exit /b 1
)
del /q "%CMDLINE_ZIP%" >nul 2>&1
rmdir /s /q "%CMDLINE_TMP%" >nul 2>&1
exit /b 0

:write_local_properties
set "LP=%PROJECT_DIR%local.properties"
set "SDK_PROP=!SDK_DIR:\=/!"
if exist "!LP!" (
  set "LP_SDK="
  for /f "usebackq tokens=1,* delims==" %%A in ("!LP!") do if /i "%%A"=="sdk.dir" set "LP_SDK=%%B"
  if "!LP_SDK!"=="!SDK_PROP!" (
    call :ok "local.properties 的 sdk.dir 已正确，未改动"
    exit /b 0
  )
  call :warn "local.properties 的 sdk.dir=!LP_SDK! 与本次部署路径不一致，正在更新"
  set "LP_BACKUP=%TEMP%\legado-local-%RANDOM%.properties"
  copy /y "!LP!" "!LP_BACKUP!" >nul
) else (
  call :info "local.properties 不存在，将创建（该文件已被 .gitignore 忽略）"
)
rem 保留其他键，只替换或追加 sdk.dir
set "TMP_LP=%TEMP%\legado-local-new-%RANDOM%.properties"
if exist "!TMP_LP!" del /q "!TMP_LP!" >nul
if exist "!LP!" (
  for /f "usebackq tokens=1,* delims==" %%A in ("!LP!") do (
    if /i not "%%A"=="sdk.dir" if not "%%~A"=="" >>"!TMP_LP!" echo %%A=%%B
  )
)
>>"!TMP_LP!" echo sdk.dir=!SDK_PROP!
copy /y "!TMP_LP!" "!LP!" >nul
if errorlevel 1 (
  call :fail "写入 local.properties 失败"
  del /q "!TMP_LP!" >nul 2>&1
  exit /b 1
)
del /q "!TMP_LP!" >nul 2>&1
call :ok "local.properties 已写入 sdk.dir=!SDK_PROP!"
exit /b 0

:confirm
set "CONFIRMED=0"
set "_M=%~1"
set /p "ANSWER=  !_M! [y/N]: "
if /i "!ANSWER!"=="y" set "CONFIRMED=1"
if /i "!ANSWER!"=="yes" set "CONFIRMED=1"
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

:info
set "_M=%~1"
echo %C_DIM%[ .. ]%C_RST% !_M!
exit /b 0

:hint
set "_M=%~1"
echo       %C_DIM%→ !_M!%C_RST%
exit /b 0
