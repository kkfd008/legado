@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title Legado - Windows 11 构建环境检查

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "PROJECT_DIR=%%~fI"

set "PASS=0"
set "WARN=0"
set "FAIL=0"
set "REPORT=%PROJECT_DIR%\build_env_check_report.txt"

rem ==== 与云端保持一致的期望值（改动项目版本时请同步修改这四个脚本）====
set "EXPECT_JDK=17"
set "EXPECT_COMPILE_SDK=36"
set "EXPECT_GRADLE=8.13"
set "EXPECT_AGP=8.13.2"
set "EXPECT_KOTLIN=2.3.0"
set "EXPECT_DEBUG_KEY_SHA=FA:BD:39:70:66:08:66:65:96:4A:A7:16:3E:E3:3D:DC:E6:7E:CA:12:4E:9F:5B:23:7B:16:BF:32:28:B1:13:2D"

> "%REPORT%" echo Legado Windows Build Environment Check
>>"%REPORT%" echo Date: %DATE% %TIME%
>>"%REPORT%" echo Project: %PROJECT_DIR%
>>"%REPORT%" echo.

echo.
echo ==============================================
echo   Legado - Windows 11 构建环境检查
echo ==============================================
echo Project: "%PROJECT_DIR%"
echo Report:  "%REPORT%"
echo.

echo [1/7] Java / JDK (需与云端一致: JDK %EXPECT_JDK%)
set "JAVA_BIN=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
where "%JAVA_BIN%" >nul 2>nul
if errorlevel 1 (
  call :fail "未找到 java，请安装 JDK %EXPECT_JDK% 并配置 PATH / JAVA_HOME"
) else (
  set "JAVA_LINE="
  for /f "delims=" %%A in ('"%JAVA_BIN%" -version 2^>^&1') do if not defined JAVA_LINE set "JAVA_LINE=%%A"
  if defined JAVA_LINE call :log "INFO" "Gradle 将使用的 Java: !JAVA_LINE!"
  set "JAVA_SPEC="
  for /f "tokens=3" %%A in ('"%JAVA_BIN%" -XshowSettings:properties -version 2^>^&1 ^| findstr /C:"java.specification.version"') do set "JAVA_SPEC=%%A"
  if "!JAVA_SPEC!"=="%EXPECT_JDK%" (
    call :pass "JDK 版本正确: java.specification.version=!JAVA_SPEC!"
  ) else if defined JAVA_SPEC (
    call :fail "Gradle 将使用的 JDK 为 !JAVA_SPEC!，项目要求 JDK %EXPECT_JDK%（云端同样使用 %EXPECT_JDK%）"
  ) else (
    call :warn "无法读取 java.specification.version，请手动执行 java -version 确认"
  )
  if exist "%JAVA_HOME%\bin\javac.exe" (
    call :pass "javac.exe 可用（已安装完整 JDK）"
  ) else (
    where javac.exe >nul 2>nul
    if errorlevel 1 (
      call :warn "未找到 javac.exe，当前可能是 JRE 而非完整 JDK"
    ) else (
      call :pass "javac.exe 可用（已安装完整 JDK）"
    )
  )
  if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javac.exe" (
      call :pass "JAVA_HOME 指向有效 JDK: %JAVA_HOME%"
    ) else (
      call :warn "JAVA_HOME 已设置但 %JAVA_HOME%\bin\javac.exe 不存在"
    )
  )
)

echo.
echo [2/7] Android SDK (需与云端一致: Platform %EXPECT_COMPILE_SDK%)
set "SDK_DIR=%ANDROID_HOME%"
if not defined SDK_DIR set "SDK_DIR=%ANDROID_SDK_ROOT%"
if not defined SDK_DIR if exist "%PROJECT_DIR%\local.properties" (
  for /f "usebackq tokens=1,* delims==" %%A in ("%PROJECT_DIR%\local.properties") do (
    if /i "%%A"=="sdk.dir" set "SDK_DIR=%%B"
  )
)
if defined SDK_DIR (
  set "SDK_DIR=!SDK_DIR:\=/!"
  if "!SDK_DIR:~-1!"=="/" set "SDK_DIR=!SDK_DIR:~0,-1!"
  set "SDK_DIR=!SDK_DIR:/=\!"
  call :log "INFO" "Android SDK: !SDK_DIR!"
  if exist "!SDK_DIR!\platforms\android-%EXPECT_COMPILE_SDK%\android.jar" (
    call :pass "Android SDK Platform %EXPECT_COMPILE_SDK% 已安装"
  ) else (
    call :fail "缺少 !SDK_DIR!\platforms\android-%EXPECT_COMPILE_SDK%\android.jar，请安装 SDK Platform %EXPECT_COMPILE_SDK%"
  )
  set "BT_VERSION="
  if exist "!SDK_DIR!\build-tools\36.0.0\aapt2.exe" set "BT_VERSION=36.0.0"
  if not defined BT_VERSION if exist "!SDK_DIR!\build-tools\35.0.0\aapt2.exe" set "BT_VERSION=35.0.0"
  if defined BT_VERSION (
    call :pass "Build Tools 可用: !BT_VERSION!（与云端一致，缺 36.0.0 时回退 35.0.0）"
  ) else (
    call :fail "未找到 build-tools 36.0.0 / 35.0.0，请安装对应 Build Tools"
  )
  if exist "!SDK_DIR!\platform-tools\adb.exe" (
    call :pass "platform-tools (adb) 已安装"
  ) else (
    call :warn "未找到 adb.exe（不影响编译，但影响安装调试）"
  )
) else (
  call :fail "未找到 Android SDK，请设置 ANDROID_HOME / ANDROID_SDK_ROOT 或配置 local.properties"
)

echo.
echo [3/7] Gradle Wrapper (需为 Gradle %EXPECT_GRADLE%)
set "GRADLE_CMD="
if exist "%PROJECT_DIR%\gradlew.bat" (
  set "GRADLE_CMD=%PROJECT_DIR%\gradlew.bat"
  call :pass "使用项目内 Gradle Wrapper (gradlew.bat)，保证与云端 Gradle %EXPECT_GRADLE% 一致"
  if exist "%PROJECT_DIR%\gradle\wrapper\gradle-wrapper.properties" (
    for /f "usebackq tokens=1,* delims==" %%A in ("%PROJECT_DIR%\gradle\wrapper\gradle-wrapper.properties") do (
      if /i "%%A"=="distributionUrl" set "WRAPPER_URL=%%B"
    )
    echo !WRAPPER_URL! | findstr /C:"gradle-%EXPECT_GRADLE%-" >nul
    if errorlevel 1 (
      call :warn "gradle-wrapper.properties 未指向 Gradle %EXPECT_GRADLE%，请核对 !WRAPPER_URL!"
    ) else (
      call :pass "Wrapper 分发包版本为 Gradle %EXPECT_GRADLE%"
    )
  )
) else (
  call :fail "缺少 gradlew.bat，无法保证与云端 Gradle 版本一致"
)

echo.
echo [4/7] 项目版本配置（AGP / Kotlin / compileSdk / targetSdk）
set "AGP_VER="
for /f "tokens=3" %%A in ('findstr /B /C:"agp = " "%PROJECT_DIR%\gradle\libs.versions.toml"') do set "AGP_VER=%%~A"
if defined AGP_VER (
  if "!AGP_VER!"=="%EXPECT_AGP%" (
    call :pass "AGP 版本正确: !AGP_VER!"
  ) else (
    call :fail "AGP 为 !AGP_VER!，云端为 %EXPECT_AGP%，请同步 gradle/libs.versions.toml"
  )
) else (
  call :warn "无法从 gradle/libs.versions.toml 读取 AGP 版本"
)

set "KOTLIN_VER="
for /f "tokens=3" %%A in ('findstr /B /C:"kotlin = " "%PROJECT_DIR%\gradle\libs.versions.toml"') do set "KOTLIN_VER=%%~A"
if defined KOTLIN_VER (
  if "!KOTLIN_VER!"=="%EXPECT_KOTLIN%" (
    call :pass "Kotlin 版本正确: !KOTLIN_VER!"
  ) else (
    call :warn "Kotlin 为 !KOTLIN_VER!，云端为 %EXPECT_KOTLIN%"
  )
)

set "COMPILE_SDK="
for /f "tokens=3" %%A in ('findstr /C:"compile_sdk_version =" "%PROJECT_DIR%\build.gradle"') do set "COMPILE_SDK=%%A"
if defined COMPILE_SDK (
  if "!COMPILE_SDK!"=="%EXPECT_COMPILE_SDK%" (
    call :pass "compileSdk 正确: !COMPILE_SDK!"
  ) else (
    call :fail "compileSdk 为 !COMPILE_SDK!，云端为 %EXPECT_COMPILE_SDK%"
  )
) else (
  call :fail "无法读取 build.gradle 中的 compile_sdk_version"
)

set "TARGET_SDK="
for /f "tokens=2" %%A in ('findstr /C:"targetSdk " "%PROJECT_DIR%\app\build.gradle"') do set "TARGET_SDK=%%A"
if defined TARGET_SDK (
  if "!TARGET_SDK!"=="%EXPECT_COMPILE_SDK%" (
    call :pass "targetSdk 正确: !TARGET_SDK!"
  ) else (
    call :warn "targetSdk 为 !TARGET_SDK!，云端为 %EXPECT_COMPILE_SDK%"
  )
)

echo.
echo [5/7] 签名配置（与云端共用仓库内 debug.keystore）
if exist "%PROJECT_DIR%\app\debug.keystore" (
  call :pass "签名文件存在: app\debug.keystore"
  set "KEYTOOL="
  where keytool.exe >nul 2>nul
  if errorlevel 1 (
    if defined JAVA_HOME if exist "%JAVA_HOME%\bin\keytool.exe" set "KEYTOOL=%JAVA_HOME%\bin\keytool.exe"
  ) else (
    set "KEYTOOL=keytool"
  )
  if defined KEYTOOL (
    set "KEY_SHA="
    for /f "tokens=2" %%A in ('"!KEYTOOL!" -list -v -keystore "%PROJECT_DIR%\app\debug.keystore" -storepass android -alias androiddebugkey 2^>nul ^| findstr /C:"SHA256:"') do set "KEY_SHA=%%A"
    if defined KEY_SHA (
      call :log "INFO" "debug.keystore SHA256: !KEY_SHA!"
      if /i "!KEY_SHA!"=="%EXPECT_DEBUG_KEY_SHA%" (
        call :pass "签名指纹与云端一致（覆盖安装不会报签名冲突）"
      ) else (
        call :fail "签名指纹与云端不一致！安装会提示签名不一致/覆盖安装失败"
      )
    ) else (
      call :warn "无法读取 debug.keystore 指纹，请手动执行 keytool -list -v 校验"
    )
  ) else (
    call :warn "未找到 keytool.exe，无法校验签名指纹"
  )
) else (
  call :fail "缺少 app\debug.keystore，本地与云端签名将不一致"
)

echo.
echo [6/7] 代码同步状态（决定 versionCode 是否一致）
where git.exe >nul 2>nul
if errorlevel 1 (
  call :warn "未找到 git，无法校验 versionCode 基数（versionCode = 10000 + git 提交数）"
) else (
  set "SHALLOW="
  for /f %%A in ('git -C "%PROJECT_DIR%" rev-parse --is-shallow-repository 2^>nul') do set "SHALLOW=%%A"
  if "!SHALLOW!"=="true" (
    call :fail "当前为浅克隆仓库，提交数与云端完整克隆不一致，versionCode 会不同。请执行: git fetch --unshallow"
  ) else (
    call :pass "仓库为完整克隆（git 提交数可作为 versionCode 基数）"
  )
  set "LOCAL_COUNT="
  for /f %%A in ('git -C "%PROJECT_DIR%" rev-list HEAD --count 2^>nul') do set "LOCAL_COUNT=%%A"
  set "LOCAL_SHA="
  for /f %%A in ('git -C "%PROJECT_DIR%" rev-parse --short HEAD 2^>nul') do set "LOCAL_SHA=%%A"
  git -C "%PROJECT_DIR%" fetch origin master --quiet >nul 2>nul
  set "REMOTE_SHA="
  for /f %%A in ('git -C "%PROJECT_DIR%" rev-parse --short origin/master 2^>nul') do set "REMOTE_SHA=%%A"
  if defined LOCAL_COUNT (
    set /a VERSION_CODE=10000+!LOCAL_COUNT!
    call :log "INFO" "本地提交数: !LOCAL_COUNT!，预计 versionCode=!VERSION_CODE!"
    call :log "INFO" "本地 HEAD: !LOCAL_SHA! / origin/master: !REMOTE_SHA!"
    if "!LOCAL_SHA!"=="!REMOTE_SHA!" (
      call :pass "本地与 origin/master 在同一提交，versionCode 与云端一致"
    ) else (
      call :fail "本地 HEAD (!LOCAL_SHA!) 与 origin/master (!REMOTE_SHA!) 不一致，两端 versionCode 会不同，请先 pull/push 对齐"
    )
    set "DIRTY="
    for /f "delims=" %%A in ('git -C "%PROJECT_DIR%" status --porcelain 2^>nul') do if not defined DIRTY set "DIRTY=%%A"
    if defined DIRTY (
      call :warn "存在未提交改动；同一 versionCode 下代码可能不一致，建议先提交再构建"
    ) else (
      call :pass "工作区干净，产物可与云端逐版本对应"
    )
  ) else (
    call :warn "无法读取本地提交数，versionCode 可能不可预期"
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

echo.
echo ==============================================
echo   检查汇总
echo ==============================================
echo PASS: !PASS!
echo WARN: !WARN!
echo FAIL: !FAIL!
>>"%REPORT%" echo.
>>"%REPORT%" echo Summary: PASS=!PASS! WARN=!WARN! FAIL=!FAIL!
echo 完整报告: "%REPORT%"
if !FAIL! GTR 0 (
  echo.
  echo 结论: 存在阻断项，请先修复 FAIL 再构建。
) else if !WARN! GTR 0 (
  echo.
  echo 结论: 无阻断项，请关注 WARN 项。
) else (
  echo.
  echo 结论: 环境与云端一致，可以构建。
)
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
