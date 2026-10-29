@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title Legado - Windows 11 Debug APK 构建

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "PROJECT_DIR=%%~fI"

rem ==== 与云端保持一致（改动项目版本时请同步修改四个脚本）====
set "EXPECT_JDK=17"
set "EXPECT_COMPILE_SDK=36"
set "EXPECT_DEBUG_KEY_SHA=FA:BD:39:70:66:08:66:65:96:4A:A7:16:3E:E3:3D:DC:E6:7E:CA:12:4E:9F:5B:23:7B:16:BF:32:28:B1:13:2D"

echo.
echo ==========================================
echo       Legado Windows 11 Debug Build
echo ==========================================
echo.

echo [1/6] JDK
set "JAVA_BIN=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
set "JAVA_SPEC="
for /f "tokens=3" %%A in ('"%JAVA_BIN%" -XshowSettings:properties -version 2^>^&1 ^| findstr /C:"java.specification.version"') do set "JAVA_SPEC=%%A"
if not "%JAVA_SPEC%"=="%EXPECT_JDK%" (
  echo ERROR: 需要 JDK %EXPECT_JDK%（Gradle 实际使用 "%JAVA_BIN%"，当前: "%JAVA_SPEC%"），否则与云端编译环境不一致。
  goto :fail
)
echo JDK: %JAVA_SPEC% OK

echo.
echo [2/6] Android SDK
set "SDK_DIR=%ANDROID_HOME%"
if not defined SDK_DIR set "SDK_DIR=%ANDROID_SDK_ROOT%"
if defined SDK_DIR (echo Current SDK: "%SDK_DIR%") else (echo ANDROID_HOME / ANDROID_SDK_ROOT 未设置)
set /p "INPUT_SDK=输入 Android SDK 目录（直接回车保持当前）: "
if defined INPUT_SDK set "SDK_DIR=%INPUT_SDK%"
if not defined SDK_DIR (
  echo ERROR: 必须指定 Android SDK 目录。
  goto :fail
)
if not exist "%SDK_DIR%\platforms\android-%EXPECT_COMPILE_SDK%\android.jar" (
  echo ERROR: 未找到 "%SDK_DIR%\platforms\android-%EXPECT_COMPILE_SDK%\android.jar"
  echo 请安装 SDK Platform %EXPECT_COMPILE_SDK%（与云端一致）。
  goto :fail
)
set "BT_VERSION="
if exist "%SDK_DIR%\build-tools\36.0.0\aapt2.exe" set "BT_VERSION=36.0.0"
if not defined BT_VERSION if exist "%SDK_DIR%\build-tools\35.0.0\aapt2.exe" set "BT_VERSION=35.0.0"
if not defined BT_VERSION (
  echo ERROR: 未找到 build-tools 36.0.0 / 35.0.0。
  goto :fail
)
set "ANDROID_HOME=%SDK_DIR%"
set "ANDROID_SDK_ROOT=%SDK_DIR%"
echo SDK OK (platform %EXPECT_COMPILE_SDK%, build-tools %BT_VERSION%)

echo.
echo [3/6] 签名一致性校验
if not exist "%PROJECT_DIR%\app\debug.keystore" (
  echo ERROR: 缺少 app\debug.keystore，本地与云端签名将不一致。
  goto :fail
)
set "KEYTOOL="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\keytool.exe" set "KEYTOOL=%JAVA_HOME%\bin\keytool.exe"
if not defined KEYTOOL (
  where keytool.exe >nul 2>nul
  if not errorlevel 1 set "KEYTOOL=keytool"
)
if not defined KEYTOOL (
  echo ERROR: 未找到 keytool.exe，无法校验签名。
  goto :fail
)
set "KEY_SHA="
for /f "tokens=2" %%A in ('"!KEYTOOL!" -list -v -keystore "%PROJECT_DIR%\app\debug.keystore" -storepass android -alias androiddebugkey 2^>nul ^| findstr /C:"SHA256:"') do set "KEY_SHA=%%A"
if /i not "%KEY_SHA%"=="%EXPECT_DEBUG_KEY_SHA%" (
  echo ERROR: debug.keystore 指纹与云端不一致。
  echo   当前: "%KEY_SHA%"
  echo   期望: "%EXPECT_DEBUG_KEY_SHA%"
  echo 继续构建会覆盖安装失败（签名不一致）。
  goto :fail
)
echo 签名 OK: %KEY_SHA%

echo.
echo [4/6] 与云端代码同步（versionCode = 10000 + git 提交数）
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
    echo WARNING: 存在未提交改动，跳过自动拉取，请自行确认与云端在同一提交。
  ) else (
    echo 正在拉取 origin/master ...
    git -C "%PROJECT_DIR%" pull --rebase origin master
    if errorlevel 1 (
      echo ERROR: git pull 失败，请先处理冲突后再构建。
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
    echo versionCode 预计: !VERSION_CODE!（提交数 !LOCAL_COUNT!，HEAD !LOCAL_SHA!）
    if not "!LOCAL_SHA!"=="!REMOTE_SHA!" (
      echo WARNING: 本地 HEAD 与 origin/master (!REMOTE_SHA!) 不一致，两端 versionCode 会不同。
    )
  )
)

echo.
echo [5/6] 构建 Debug APK
pushd "%PROJECT_DIR%" || (
  echo ERROR: 无法进入项目目录。
  goto :fail
)

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

call gradlew.bat --no-daemon assembleAppDebug
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
  echo ERROR: 未找到构建产物 app\build\outputs\apk\app\debug\legado_app_*.apk
  popd
  goto :fail
)

echo.
echo [6/6] 产物校验（签名 / versionCode）
"%SDK_DIR%\build-tools\%BT_VERSION%\aapt2.exe" dump badging "%APK%" > "%TEMP%\legado_badging.txt" 2>nul
for /f "delims=" %%A in ('findstr /C:"package: name=" "%TEMP%\legado_badging.txt"') do echo %%A
"%SDK_DIR%\build-tools\%BT_VERSION%\apksigner.bat" verify --print-certs "%APK%" > "%TEMP%\legado_certs.txt" 2>&1
findstr /C:"SHA-256" "%TEMP%\legado_certs.txt"
findstr /C:"%EXPECT_DEBUG_KEY_SHA%" "%TEMP%\legado_certs.txt" >nul
if errorlevel 1 (
  echo WARNING: 产物签名指纹与期望值不一致，请勿与本机已装应用互相覆盖安装。
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
