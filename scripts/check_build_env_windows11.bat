@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 936 >nul
title Legado - Windows 11 构建环境检查

set "SCRIPT_DIR=%~dp0"
set "REPORT=%SCRIPT_DIR%..\build_env_check_report.txt"
for %%I in ("%SCRIPT_DIR%..") do set "REPORT=%%~fI\build_env_check_report.txt"

set "PASS=0"
set "WARN=0"
set "FAIL=0"

rem 检查模式: 只检测, 不安装; 缺失项写入 ENV_FIX_FILE 供报告使用
set "ENV_NO_INSTALL=1"
set "ENV_FIX_FILE=%TEMP%\legado_env_fix_%RANDOM%%RANDOM%.txt"
if exist "!ENV_FIX_FILE!" del "!ENV_FIX_FILE!" >nul 2>nul

> "%REPORT%" echo Legado Windows Build Environment Check
>>"%REPORT%" echo Date: %DATE% %TIME%
>>"%REPORT%" echo Project: %SCRIPT_DIR%..
>>"%REPORT%" echo.

echo.
echo ==============================================
echo   Legado - Windows 11 构建环境检查
echo ==============================================
echo 规则: JDK 17 + Gradle 由 mise 或项目 wrapper 提供; SDK 固定 platform 36 + build-tools 36.0.0
echo 模式: 只检测, 不安装; 缺失项会给出安装建议
echo Report: "%REPORT%"
echo.

echo [0/7] 环境检测 (mise / JDK / Gradle / SDK)
call "%SCRIPT_DIR%env_bootstrap_windows.bat"
if errorlevel 1 (
  call :fail "环境检测发现缺失项，见文末安装建议"
) else (
  call :pass "环境检测完成，所需组件齐备"
)

echo.
echo [1/7] JDK 期望版本 %EXPECT_JDK%
if "!JAVA_SPEC!"=="%EXPECT_JDK%" (
  call :pass "JDK %EXPECT_JDK% OK: !JAVA_HOME!"
) else (
  call :fail "JDK 主版本为 !JAVA_SPEC!，要求 %EXPECT_JDK%，JAVA_HOME=!JAVA_HOME!"
)
if exist "%JAVA_HOME%\bin\javac.exe" (
  call :pass "javac 可用，是完整 JDK"
) else (
  call :warn "未找到 javac，当前可能是 JRE"
)

echo.
echo [2/7] Gradle 期望版本不低于 %EXPECT_GRADLE_MIN%，当前 !GRADLE_VERSION!，来源 !GRADLE_SRC!
set "GV_MAJOR="
set "GV_MINOR="
for /f "tokens=1,2 delims=." %%A in ("!GRADLE_VERSION!") do (
  set "GV_MAJOR=%%A"
  set "GV_MINOR=%%B"
)
if not defined GV_MAJOR set "GV_MAJOR=0"
if not defined GV_MINOR set "GV_MINOR=0"
set "MIN_MAJOR="
set "MIN_MINOR="
for /f "tokens=1,2 delims=." %%A in ("%EXPECT_GRADLE_MIN%") do (
  set "MIN_MAJOR=%%A"
  set "MIN_MINOR=%%B"
)
if not defined MIN_MAJOR set "MIN_MAJOR=0"
if not defined MIN_MINOR set "MIN_MINOR=0"
set "GRADLE_OK=0"
if !GV_MAJOR! GTR !MIN_MAJOR! set "GRADLE_OK=1"
if !GV_MAJOR! EQU !MIN_MAJOR! if !GV_MINOR! GEQ !MIN_MINOR! set "GRADLE_OK=1"
if not "!GRADLE_OK!"=="1" (
  call :fail "Gradle !GRADLE_VERSION! 低于 AGP %EXPECT_AGP% 要求的 %EXPECT_GRADLE_MIN%"
) else (
  call :pass "Gradle !GRADLE_VERSION! OK，满足 AGP %EXPECT_AGP% 要求"
)
if "!GRADLE_CACHED!"=="1" (
  call :pass "Gradle distribution 已在本地缓存，构建无需联网"
) else (
  call :warn "本地未缓存 Gradle !GRADLE_VERSION! distribution，首次构建需联网下载"
)

echo.
echo [3/7] Android SDK 固定 platform %EXPECT_COMPILE_SDK% + build-tools %EXPECT_BUILD_TOOLS%
if exist "%SDK_DIR%\platforms\android-%EXPECT_COMPILE_SDK%\android.jar" (
  call :pass "platform android-%EXPECT_COMPILE_SDK% 已安装"
) else (
  call :fail "缺少 platform android-%EXPECT_COMPILE_SDK%"
)
if exist "%SDK_DIR%\build-tools\%EXPECT_BUILD_TOOLS%\aapt2.exe" (
  call :pass "build-tools %EXPECT_BUILD_TOOLS% 已安装"
) else (
  call :fail "缺少 build-tools %EXPECT_BUILD_TOOLS%"
)
if exist "%SDK_DIR%\platform-tools\adb.exe" (
  call :pass "platform-tools adb 已安装"
) else (
  call :warn "未找到 adb，不影响编译"
)

echo.
echo [4/7] 项目版本配置 AGP / Kotlin / compileSdk / targetSdk
set "AGP_VER="
for /f "tokens=3" %%A in ('findstr /B /C:"agp = " "%PROJECT_DIR%\gradle\libs.versions.toml"') do set "AGP_VER=%%~A"
if "!AGP_VER!"=="%EXPECT_AGP%" (
  call :pass "AGP 版本正确: !AGP_VER!"
) else (
  call :fail "AGP 为 !AGP_VER!，期望 %EXPECT_AGP%"
)
set "KOTLIN_VER="
for /f "tokens=3" %%A in ('findstr /B /C:"kotlin = " "%PROJECT_DIR%\gradle\libs.versions.toml"') do set "KOTLIN_VER=%%~A"
if "!KOTLIN_VER!"=="%EXPECT_KOTLIN%" (
  call :pass "Kotlin 版本正确: !KOTLIN_VER!"
) else (
  call :warn "Kotlin 为 !KOTLIN_VER!，期望 %EXPECT_KOTLIN%"
)
set "COMPILE_SDK="
for /f "tokens=3" %%A in ('findstr /C:"compile_sdk_version =" "%PROJECT_DIR%\build.gradle"') do set "COMPILE_SDK=%%A"
if "!COMPILE_SDK!"=="%EXPECT_COMPILE_SDK%" (
  call :pass "compileSdk 正确: !COMPILE_SDK!"
) else (
  call :fail "compileSdk 为 !COMPILE_SDK!，期望 %EXPECT_COMPILE_SDK%"
)
set "TARGET_SDK="
for /f "tokens=2" %%A in ('findstr /C:"targetSdk " "%PROJECT_DIR%\app\build.gradle"') do set "TARGET_SDK=%%A"
if "!TARGET_SDK!"=="%EXPECT_COMPILE_SDK%" (
  call :pass "targetSdk 正确: !TARGET_SDK!"
) else (
  call :warn "targetSdk 为 !TARGET_SDK!，期望 %EXPECT_COMPILE_SDK%"
)

echo.
echo [5/7] 签名，两端共用仓库内 app\debug.keystore
if exist "%PROJECT_DIR%\app\debug.keystore" (
  call :pass "签名文件存在: app\debug.keystore"
  set "KEY_SHA="
  set "KEY_FILE=%TEMP%\legado_key_%RANDOM%%RANDOM%.txt"
  "!JAVA_HOME!\bin\keytool.exe" -list -v -keystore "%PROJECT_DIR%\app\debug.keystore" -storepass android -alias androiddebugkey >"!KEY_FILE!" 2>&1
  for /f "tokens=2" %%A in ('findstr /C:"SHA256:" "!KEY_FILE!"') do if not defined KEY_SHA set "KEY_SHA=%%A"
  if exist "!KEY_FILE!" del "!KEY_FILE!" >nul 2>nul
  if /i "!KEY_SHA!"=="%EXPECT_DEBUG_KEY_SHA%" (
    call :pass "签名指纹与云端一致，覆盖安装不会报签名冲突"
  ) else (
    call :fail "签名指纹与云端不一致: !KEY_SHA!"
  )
) else (
  call :fail "缺少 app\debug.keystore"
)

echo.
echo [6/7] 代码同步 versionCode = 10000 + git 提交数
where git.exe >nul 2>nul
if errorlevel 1 (
  call :warn "未找到 git，跳过 versionCode 校验"
) else (
  set "SHALLOW="
  for /f %%A in ('git -C "%PROJECT_DIR%" rev-parse --is-shallow-repository 2^>nul') do set "SHALLOW=%%A"
  if "!SHALLOW!"=="true" (
    call :fail "浅克隆仓库，提交数与云端不同，versionCode 会不一致。请执行: git fetch --unshallow"
  ) else (
    call :pass "完整克隆，提交数可作 versionCode 基数"
  )
  git -C "%PROJECT_DIR%" fetch origin master --quiet >nul 2>nul
  set "LOCAL_SHA="
  for /f %%A in ('git -C "%PROJECT_DIR%" rev-parse --short HEAD 2^>nul') do set "LOCAL_SHA=%%A"
  set "REMOTE_SHA="
  for /f %%A in ('git -C "%PROJECT_DIR%" rev-parse --short origin/master 2^>nul') do set "REMOTE_SHA=%%A"
  set "LOCAL_COUNT="
  for /f %%A in ('git -C "%PROJECT_DIR%" rev-list HEAD --count 2^>nul') do set "LOCAL_COUNT=%%A"
  if defined LOCAL_COUNT (
    set /a VERSION_CODE=10000+!LOCAL_COUNT!
    call :log "INFO" "提交数 !LOCAL_COUNT!，预计 versionCode=!VERSION_CODE!，HEAD !LOCAL_SHA! / origin !REMOTE_SHA!"
    if "!LOCAL_SHA!"=="!REMOTE_SHA!" (
      call :pass "与 origin/master 同提交，versionCode 与云端一致"
    ) else (
      call :fail "与 origin/master 不同提交，两端 versionCode 会不同"
    )
  ) else (
    call :warn "无法读取提交数"
  )
  set "DIRTY="
  for /f "delims=" %%A in ('git -C "%PROJECT_DIR%" status --porcelain 2^>nul') do if not defined DIRTY set "DIRTY=%%A"
  if defined DIRTY (
    call :warn "存在未提交改动，同 versionCode 下代码可能不一致"
  ) else (
    call :pass "工作区干净"
  )
)

echo.
echo [7/7] Gradle 缓存与写入权限
set "CACHE_DIR=%GRADLE_USER_HOME%"
if not defined CACHE_DIR set "CACHE_DIR=%USERPROFILE%\.gradle"
if not exist "!CACHE_DIR!" mkdir "!CACHE_DIR!" >nul 2>nul
if exist "!CACHE_DIR!" (
  set "TEST_FILE=!CACHE_DIR!\.legado_write_test_!RANDOM!.tmp"
  >"!TEST_FILE!" echo test
  if exist "!TEST_FILE!" (
    del "!TEST_FILE!" >nul 2>nul
    call :pass "Gradle 缓存目录可写: !CACHE_DIR!"
  ) else (
    call :fail "Gradle 缓存目录不可写: !CACHE_DIR!"
  )
) else (
  call :fail "无法创建 Gradle 缓存目录: !CACHE_DIR!"
)

:summary
echo.
echo ==============================================
echo   检查汇总
echo ==============================================
echo PASS: !PASS!
echo WARN: !WARN!
echo FAIL: !FAIL!
>>"%REPORT%" echo.
>>"%REPORT%" echo Summary: PASS=!PASS! WARN=!WARN! FAIL=!FAIL!

if exist "!ENV_FIX_FILE!" (
  echo.
  echo ----------------------------------------------
  echo   安装建议 / 修复指令
  echo ----------------------------------------------
  >>"%REPORT%" echo.
  >>"%REPORT%" echo ---- FIX ----
  for /f "usebackq delims=" %%A in ("!ENV_FIX_FILE!") do (
    echo   %%A
    >>"%REPORT%" echo   %%A
  )
  >>"%REPORT%" echo ---- END FIX ----
)

echo 完整报告: "%REPORT%"
if !FAIL! GTR 0 (
  echo.
  echo 结论: 存在阻断项，请按上方安装建议处理后重新运行本检查。
) else if !WARN! GTR 0 (
  echo.
  echo 结论: 无阻断项，请关注 WARN。
) else (
  echo.
  echo 结论: 环境与云端一致，可以构建。
)
if exist "!ENV_FIX_FILE!" del "!ENV_FIX_FILE!" >nul 2>nul
echo.
pause
endlocal
exit /b

:pass
set /a PASS+=1
powershell -NoProfile -ExecutionPolicy Bypass -Command "Write-Host '[PASS] %~1' -ForegroundColor Green"
>>"%REPORT%" echo [PASS] %~1
exit /b

:warn
set /a WARN+=1
powershell -NoProfile -ExecutionPolicy Bypass -Command "Write-Host '[WARN] %~1' -ForegroundColor Yellow"
>>"%REPORT%" echo [WARN] %~1
exit /b

:fail
set /a FAIL+=1
powershell -NoProfile -ExecutionPolicy Bypass -Command "Write-Host '[FAIL] %~1' -ForegroundColor Red"
>>"%REPORT%" echo [FAIL] %~1
exit /b

:log
echo [%~1] %~2
>>"%REPORT%" echo [%~1] %~2
exit /b
