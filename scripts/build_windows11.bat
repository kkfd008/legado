@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 936 >nul
title Legado - Windows 11 Debug APK 构建

set "SCRIPT_DIR=%~dp0"

echo.
echo ==========================================
echo       Legado Windows 11 Debug Build
echo ==========================================
echo 规则: JDK 17 + Gradle 由 mise 或项目 wrapper 提供; SDK 固定 platform 36 + build-tools 36.0.0
echo.

echo [0/6] 环境引导 (mise / JDK / Gradle / SDK)
rem 构建模式: 缺什么自动安装
set "ENV_NO_INSTALL=0"
set "ENV_FIX_FILE="
call "%SCRIPT_DIR%env_bootstrap_windows.bat"
if errorlevel 1 (
  echo ERROR: 环境引导失败，请查看上方输出。
  goto :fail
)

echo.
echo [1/6] JDK 校验
if not "!JAVA_SPEC!"=="%EXPECT_JDK%" (
  echo ERROR: JDK 主版本为 !JAVA_SPEC!，要求 %EXPECT_JDK%，JAVA_HOME=!JAVA_HOME!
  goto :fail
)
echo JDK !JAVA_SPEC! OK  !JAVA_HOME!

echo.
echo [2/6] Gradle 校验
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
  echo ERROR: Gradle !GRADLE_VERSION! 低于 AGP %EXPECT_AGP% 要求的 %EXPECT_GRADLE_MIN%。
  goto :fail
)
echo Gradle !GRADLE_VERSION! OK  来源 !GRADLE_SRC!
if "!GRADLE_CACHED!"=="0" (
  echo WARNING: 本地未缓存 Gradle !GRADLE_VERSION! distribution，构建将联网下载，请确保网络可达 services.gradle.org。
)

echo.
echo [3/6] SDK + 签名一致性校验
if not exist "%SDK_DIR%\platforms\android-%EXPECT_COMPILE_SDK%\android.jar" (
  echo ERROR: 缺少 platform android-%EXPECT_COMPILE_SDK%。
  goto :fail
)
if not exist "%SDK_DIR%\build-tools\%EXPECT_BUILD_TOOLS%\aapt2.exe" (
  echo ERROR: 缺少 build-tools %EXPECT_BUILD_TOOLS%。
  goto :fail
)
if not exist "%PROJECT_DIR%\app\debug.keystore" (
  echo ERROR: 缺少 app\debug.keystore，本地与云端签名将不一致。
  goto :fail
)
set "KEY_SHA="
set "KEY_FILE=%TEMP%\legado_key_%RANDOM%%RANDOM%.txt"
"!JAVA_HOME!\bin\keytool.exe" -list -v -keystore "%PROJECT_DIR%\app\debug.keystore" -storepass android -alias androiddebugkey >"!KEY_FILE!" 2>&1
for /f "tokens=2" %%A in ('findstr /C:"SHA256:" "!KEY_FILE!"') do if not defined KEY_SHA set "KEY_SHA=%%A"
if exist "!KEY_FILE!" del "!KEY_FILE!" >nul 2>nul
if /i not "!KEY_SHA!"=="%EXPECT_DEBUG_KEY_SHA%" (
  echo ERROR: debug.keystore 指纹与云端不一致。
  echo   当前: !KEY_SHA!
  echo   期望: %EXPECT_DEBUG_KEY_SHA%
  goto :fail
)
echo 签名 OK: !KEY_SHA!

echo.
echo [4/6] 与云端代码同步 versionCode = 10000 + git 提交数
where git.exe >nul 2>nul
if errorlevel 1 (
  echo WARNING: 未找到 git，跳过同步校验。
) else (
  set "SHALLOW="
  for /f %%A in ('git -C "%PROJECT_DIR%" rev-parse --is-shallow-repository 2^>nul') do set "SHALLOW=%%A"
  if "!SHALLOW!"=="true" (
    echo 检测到浅克隆，正在补齐历史: git fetch --unshallow ...
    git -C "%PROJECT_DIR%" fetch --unshallow origin
    if errorlevel 1 (
      echo ERROR: git fetch --unshallow 失败，无法保证 versionCode 与云端一致。
      goto :fail
    )
  )
  set "DIRTY="
  for /f "delims=" %%A in ('git -C "%PROJECT_DIR%" status --porcelain 2^>nul') do if not defined DIRTY set "DIRTY=%%A"
  if defined DIRTY (
    echo WARNING: 存在未提交改动，跳过自动拉取，请确认与云端在同一提交。
  ) else (
    echo 正在拉取 origin/master ...
    git -C "%PROJECT_DIR%" pull --rebase origin master
    if errorlevel 1 (
      echo ERROR: git pull 失败，请先处理冲突。
      goto :fail
    )
  )
  set "LOCAL_COUNT="
  for /f %%A in ('git -C "%PROJECT_DIR%" rev-list HEAD --count 2^>nul') do set "LOCAL_COUNT=%%A"
  set "LOCAL_SHA="
  for /f %%A in ('git -C "%PROJECT_DIR%" rev-parse --short HEAD 2^>nul') do set "LOCAL_SHA=%%A"
  set "REMOTE_SHA="
  for /f %%A in ('git -C "%PROJECT_DIR%" rev-parse --short origin/master 2^>nul') do set "REMOTE_SHA=%%A"
  if defined LOCAL_COUNT (
    set /a VERSION_CODE=10000+!LOCAL_COUNT!
    echo versionCode 预计: !VERSION_CODE!  提交数 !LOCAL_COUNT!，HEAD !LOCAL_SHA!
    if not "!LOCAL_SHA!"=="!REMOTE_SHA!" (
      echo WARNING: 本地 HEAD 与 origin/master !REMOTE_SHA! 不一致，两端 versionCode 会不同。
    )
  )
)

echo.
echo [5/6] 构建 Debug APK
pushd "%PROJECT_DIR%" || (
  echo ERROR: 无法进入项目目录。
  goto :fail
)

rem 临时写入 local.properties 指向固定 SDK，构建后还原
set "LOCAL_PROPERTIES=%PROJECT_DIR%\local.properties"
set "LOCAL_BACKUP=%TEMP%\legado-local-%RANDOM%-%RANDOM%.properties"
set "HAD_LOCAL=0"
if exist "%LOCAL_PROPERTIES%" (
  copy /y "%LOCAL_PROPERTIES%" "%LOCAL_BACKUP%" >nul
  if errorlevel 1 (
    echo ERROR: 无法备份 local.properties。
    popd
    goto :fail
  )
  set "HAD_LOCAL=1"
)
set "SDK_PROP=%SDK_DIR:\=/%"
> "%LOCAL_PROPERTIES%" echo sdk.dir=%SDK_PROP%

call "!GRADLE_CMD!" --no-daemon assembleAppDebug
set "BUILD_EXIT=%ERRORLEVEL%"

if "%HAD_LOCAL%"=="1" (
  copy /y "%LOCAL_BACKUP%" "%LOCAL_PROPERTIES%" >nul
  del "%LOCAL_BACKUP%" >nul 2>nul
) else (
  del "%LOCAL_PROPERTIES%" >nul 2>nul
)

if not "%BUILD_EXIT%"=="0" (
  echo.
  echo ERROR: Gradle 构建失败，退出码 %BUILD_EXIT%。
  popd
  goto :fail
)

set "APK="
for %%F in ("%PROJECT_DIR%\app\build\outputs\apk\app\debug\legado_app_*.apk") do set "APK=%%~fF"
if not defined APK (
  echo ERROR: 未找到 app\build\outputs\apk\app\debug\legado_app_*.apk
  popd
  goto :fail
)

echo.
echo [6/6] 产物校验 签名 / versionCode
"%SDK_DIR%\build-tools\%BT_VERSION%\aapt2.exe" dump badging "%APK%" > "%TEMP%\legado_badging.txt" 2>nul
for /f "delims=" %%A in ('findstr /C:"package: name=" "%TEMP%\legado_badging.txt"') do echo %%A
"%SDK_DIR%\build-tools\%BT_VERSION%\apksigner.bat" verify --print-certs "%APK%" > "%TEMP%\legado_certs.txt" 2>&1
findstr /C:"SHA-256" "%TEMP%\legado_certs.txt"
findstr /C:"%EXPECT_DEBUG_KEY_SHA%" "%TEMP%\legado_certs.txt" >nul
if errorlevel 1 (
  echo WARNING: 产物签名指纹与期望值不一致，请勿与已装应用互相覆盖安装。
) else (
  echo 产物签名与云端一致 OK
)
del "%TEMP%\legado_badging.txt" >nul 2>nul
del "%TEMP%\legado_certs.txt" >nul 2>nul

echo.
echo ==========================================
echo BUILD SUCCESS
echo APK: "%APK%"
echo 提示: 覆盖安装需 versionCode 不低于已装版本，且签名一致。
echo ==========================================
start "" explorer.exe /select,"%APK%"

popd
echo.
pause
endlocal
exit /b 0

:fail
echo.
echo 构建已中止，请查看上方提示。
pause
endlocal
exit /b 1
