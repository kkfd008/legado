@echo off
setlocal EnableExtensions
chcp 65001 >nul
title DroidShare - Windows 11 APK Build

echo.
echo ==========================================
echo       DroidShare Windows 11 Build
echo ==========================================
echo.

set "PROJECT_DIR=%~dp0"
set "SDK_DIR=%ANDROID_HOME%"
if not defined SDK_DIR set "SDK_DIR=%ANDROID_SDK_ROOT%"
set "GRADLE_DIR=%GRADLE_HOME%"
set "GRADLE_CACHE=%GRADLE_USER_HOME%"

echo [1/4] Android SDK
if defined SDK_DIR (
  echo Current SDK: "%SDK_DIR%"
) else (
  echo ANDROID_HOME / ANDROID_SDK_ROOT is not set.
)
set /p "INPUT_SDK=Enter Android SDK directory (Enter to keep current): "
if defined INPUT_SDK set "SDK_DIR=%INPUT_SDK%"
if not defined SDK_DIR (
  echo ERROR: Android SDK directory is required.
  goto :fail
)
if not exist "%SDK_DIR%\platforms\android-35\android.jar" (
  echo ERROR: Cannot find "%SDK_DIR%\platforms\android-35\android.jar"
  echo Install Android SDK Platform 35 in SDK Manager, or correct the SDK path.
  goto :fail
)
if not exist "%SDK_DIR%\platform-tools" (
  echo WARNING: platform-tools directory not found. APK build may still work.
)
set "ANDROID_HOME=%SDK_DIR%"
set "ANDROID_SDK_ROOT=%SDK_DIR%"

echo.
echo [2/4] Gradle installation
if defined GRADLE_DIR (
  echo Current Gradle home: "%GRADLE_DIR%"
) else (
  echo GRADLE_HOME is not set.
)
set /p "INPUT_GRADLE=Enter Gradle installation directory containing bin\gradle.bat (Enter to use current/PATH): "
if defined INPUT_GRADLE set "GRADLE_DIR=%INPUT_GRADLE%"

if defined GRADLE_DIR (
  if exist "%GRADLE_DIR%\bin\gradle.bat" (
    set "GRADLE_CMD=%GRADLE_DIR%\bin\gradle.bat"
  ) else (
    echo ERROR: Cannot find "%GRADLE_DIR%\bin\gradle.bat"
    goto :fail
  )
) else (
  where gradle.bat >nul 2>nul
  if errorlevel 1 (
    where gradle >nul 2>nul
    if errorlevel 1 (
      echo ERROR: Gradle was not found. Set GRADLE_HOME, add Gradle to PATH, or enter its directory above.
      goto :fail
    ) else (
      set "GRADLE_CMD=gradle"
    )
  ) else (
    set "GRADLE_CMD=gradle.bat"
  )
)

echo.
echo [3/4] Gradle cache directory
if defined GRADLE_CACHE (
  echo Current Gradle user home: "%GRADLE_CACHE%"
) else (
  echo Default is %%USERPROFILE%%\.gradle
)
set /p "INPUT_CACHE=Enter Gradle cache/user-home directory (Enter to keep current/default): "
if defined INPUT_CACHE set "GRADLE_CACHE=%INPUT_CACHE%"
if defined GRADLE_CACHE (
  if not exist "%GRADLE_CACHE%" mkdir "%GRADLE_CACHE%" 2>nul
  if not exist "%GRADLE_CACHE%" (
    echo ERROR: Cannot create or access Gradle cache directory "%GRADLE_CACHE%"
    goto :fail
  )
  set "GRADLE_USER_HOME=%GRADLE_CACHE%"
)

echo.
echo [4/4] Build Debug APK
echo Project: "%PROJECT_DIR%"
echo SDK:     "%SDK_DIR%"
if defined GRADLE_DIR (echo Gradle:  "%GRADLE_DIR%") else (echo Gradle:  PATH)
if defined GRADLE_CACHE (echo Cache:   "%GRADLE_CACHE%") else (echo Cache:   "%USERPROFILE%\.gradle")
echo.

pushd "%PROJECT_DIR%" || (
  echo ERROR: Cannot enter project directory.
  goto :fail
)

rem Temporarily override local.properties so the selected SDK is used.
set "LOCAL_PROPERTIES=%PROJECT_DIR%local.properties"
set "LOCAL_BACKUP=%TEMP%\DroidShare-local-%RANDOM%-%RANDOM%.properties"
set "HAD_LOCAL_PROPERTIES=0"
if exist "%LOCAL_PROPERTIES%" (
  copy /y "%LOCAL_PROPERTIES%" "%LOCAL_BACKUP%" >nul
  if errorlevel 1 (
    echo ERROR: Cannot back up local.properties.
    popd
    goto :fail
  )
  set "HAD_LOCAL_PROPERTIES=1"
)
set "SDK_PROP=%SDK_DIR:\=/%"
> "%LOCAL_PROPERTIES%" echo sdk.dir=%SDK_PROP%

call "%GRADLE_CMD%" --version
if errorlevel 1 (
  echo ERROR: Gradle could not start. Check Gradle and Java/JDK configuration.
  goto :restore_and_fail
)

call "%GRADLE_CMD%" --no-daemon assembleDebug
set "BUILD_EXIT=%ERRORLEVEL%"

if "%HAD_LOCAL_PROPERTIES%"=="1" (
  copy /y "%LOCAL_BACKUP%" "%LOCAL_PROPERTIES%" >nul
  del "%LOCAL_BACKUP%" >nul 2>nul
) else (
  del "%LOCAL_PROPERTIES%" >nul 2>nul
)

if not "%BUILD_EXIT%"=="0" (
  echo.
  echo ERROR: Gradle build failed with exit code %BUILD_EXIT%.
  popd
  goto :fail
)

set "APK_PATH=%PROJECT_DIR%app\build\outputs\apk\debug\app-debug.apk"
if exist "%APK_PATH%" (
  echo.
  echo ==========================================
  echo BUILD SUCCESS
  echo APK: "%APK_PATH%"
  echo ==========================================
  start "" explorer.exe /select,"%APK_PATH%"
) else (
  echo WARNING: Gradle returned success but APK was not found at:
  echo "%APK_PATH%"
)

popd
echo.
pause
endlocal
exit /b 0

:restore_and_fail
if "%HAD_LOCAL_PROPERTIES%"=="1" (
  copy /y "%LOCAL_BACKUP%" "%LOCAL_PROPERTIES%" >nul
  del "%LOCAL_BACKUP%" >nul 2>nul
) else (
  del "%LOCAL_PROPERTIES%" >nul 2>nul
)
popd

:fail
echo.
echo Build stopped. Review the messages above.
pause
endlocal
exit /b 1
