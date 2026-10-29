@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title DroidShare - Build Environment Check

set "PASS=0"
set "WARN=0"
set "FAIL=0"
set "PROJECT_DIR=%~dp0"
set "REPORT=%PROJECT_DIR%build_env_check_report.txt"

> "%REPORT%" echo DroidShare Windows Build Environment Check
>>"%REPORT%" echo Date: %DATE% %TIME%
>>"%REPORT%" echo Project: %PROJECT_DIR%
>>"%REPORT%" echo.

echo.
echo ==============================================
echo   DroidShare - Windows 11 Build Environment
echo ==============================================
echo Project: "%PROJECT_DIR%"
echo Report:  "%REPORT%"
echo.

call :info "Checking operating system..."
for /f "tokens=*" %%A in ('powershell -NoProfile -Command "[System.Environment]::OSVersion.Version.ToString()" 2^>nul') do set "OS_VERSION=%%A"
if defined OS_VERSION (
  call :log "INFO" "Windows version: !OS_VERSION!"
) else (
  call :warn "Could not determine Windows version (PowerShell unavailable)."
)

echo.
echo [1/6] Java / JDK
where java.exe >nul 2>nul
if errorlevel 1 (
  call :fail "java.exe not found on PATH. Install JDK 17 and configure PATH/JAVA_HOME."
) else (
  set "JAVA_LINE="
  for /f "delims=" %%A in ('java -version 2^>^&1') do if not defined JAVA_LINE set "JAVA_LINE=%%A"
  if defined JAVA_LINE call :log "INFO" "Java: !JAVA_LINE!"
  set "JAVA_MAJOR="
  for /f "tokens=3" %%A in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr /C:"java.specification.version"') do set "JAVA_MAJOR=%%A"
  if "!JAVA_MAJOR!"=="17" (
    call :pass "Java specification version is 17."
  ) else if defined JAVA_MAJOR (
    call :warn "Java specification version is !JAVA_MAJOR!; this project expects JDK 17."
  ) else (
    call :warn "Could not read Java specification version. Check java -version manually."
  )
  where javac.exe >nul 2>nul
  if errorlevel 1 (
    call :warn "javac.exe not found on PATH. The active Java may be a JRE rather than a full JDK."
  ) else (
    call :pass "javac.exe found on PATH (a JDK is available)."
  )
  if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javac.exe" (
      call :pass "JAVA_HOME points to a JDK: %JAVA_HOME%"
    ) else (
      call :warn "JAVA_HOME is set but javac.exe was not found under %JAVA_HOME%\bin."
    )
  ) else (
    where mise.exe >nul 2>nul
    if not errorlevel 1 (
      call :log "INFO" "JAVA_HOME is unset; Java appears to be PATH/mise managed. Gradle can use the active Java from PATH."
      for /f "delims=" %%A in ('mise where java 2^>nul') do call :log "INFO" "mise Java installation: %%A"
    ) else (
      call :log "INFO" "JAVA_HOME is unset; Gradle can still use java.exe from PATH."
    )
  )
)

echo.
echo [2/6] Android SDK
set "SDK_DIR=%ANDROID_HOME%"
if not defined SDK_DIR set "SDK_DIR=%ANDROID_SDK_ROOT%"
if not defined SDK_DIR if exist "%PROJECT_DIR%local.properties" (
  for /f "usebackq tokens=1,* delims==" %%A in ("%PROJECT_DIR%local.properties") do (
    if /i "%%A"=="sdk.dir" set "SDK_DIR=%%B"
  )
)
if defined SDK_DIR (
  set "SDK_DIR=!SDK_DIR:\=/!"
  if "!SDK_DIR:~-1!"=="/" set "SDK_DIR=!SDK_DIR:~0,-1!"
  call :log "INFO" "Android SDK: !SDK_DIR!"
  if exist "!SDK_DIR!\platforms\android-35\android.jar" (
    call :pass "Android SDK Platform 35 found."
  ) else (
    call :fail "Missing !SDK_DIR!\platforms\android-35\android.jar. Install Android SDK Platform 35."
  )
  if exist "!SDK_DIR!\build-tools" (
    call :pass "Android SDK Build Tools directory found."
    set "BT_FOUND="
    for /d %%D in ("!SDK_DIR!\build-tools\*") do set "BT_FOUND=1"
    if defined BT_FOUND (
      call :log "INFO" "Installed Build Tools versions:"
      for /d %%D in ("!SDK_DIR!\build-tools\*") do call :log "INFO" "  %%~nxD"
    ) else (
      call :warn "No Android Build Tools version directory found."
    )
  ) else (
    call :warn "Android SDK build-tools directory not found."
  )
  if exist "!SDK_DIR!\platform-tools\adb.exe" (
    call :pass "Android Platform Tools (adb) found."
  ) else (
    call :warn "adb.exe not found; it is optional for APK compilation."
  )
) else (
  call :fail "Android SDK path not found. Set ANDROID_HOME / ANDROID_SDK_ROOT or configure local.properties."
)

echo.
echo [3/6] Gradle
set "GRADLE_CMD="
if defined GRADLE_HOME (
  if exist "%GRADLE_HOME%\bin\gradle.bat" set "GRADLE_CMD=%GRADLE_HOME%\bin\gradle.bat"
  if not defined GRADLE_CMD if exist "%GRADLE_HOME%\gradle.bat" set "GRADLE_CMD=%GRADLE_HOME%\gradle.bat"
  if not defined GRADLE_CMD if exist "%GRADLE_HOME%\current\bin\gradle.bat" set "GRADLE_CMD=%GRADLE_HOME%\current\bin\gradle.bat"
  rem Support a parent directory containing a versioned Gradle installation.
  if not defined GRADLE_CMD for /d %%D in ("%GRADLE_HOME%\*") do if exist "%%~fD\bin\gradle.bat" set "GRADLE_CMD=%%~fD\bin\gradle.bat"
  if not defined GRADLE_CMD call :warn "GRADLE_HOME is set to '%GRADLE_HOME%', but no gradle.bat was found in its usual locations."
)
if not defined GRADLE_CMD (
  where gradle.bat >nul 2>nul
  if not errorlevel 1 set "GRADLE_CMD=gradle.bat"
)
if not defined GRADLE_CMD (
  where gradle >nul 2>nul
  if not errorlevel 1 set "GRADLE_CMD=gradle"
)
if not defined GRADLE_CMD (
  where mise.exe >nul 2>nul
  if not errorlevel 1 (
    mise which gradle >nul 2>nul
    if not errorlevel 1 set "GRADLE_CMD=gradle"
  )
)
if defined GRADLE_CMD (
  call :pass "Gradle command found: !GRADLE_CMD!"
  set "GRADLE_VERSION="
  for /f "tokens=2" %%A in ('call "!GRADLE_CMD!" --version 2^>^&1 ^| findstr /R /C:"^Gradle [0-9]"') do set "GRADLE_VERSION=%%A"
  if defined GRADLE_VERSION (
    call :log "INFO" "Gradle version: !GRADLE_VERSION!"
    if "!GRADLE_VERSION!"=="8.9" (
      call :pass "Gradle 8.9 matches the expected version for Android Gradle Plugin 8.7.3."
    ) else (
      call :warn "Expected Gradle 8.9 for this project's Android Gradle Plugin 8.7.3; detected !GRADLE_VERSION!. Confirm compatibility."
    )
  ) else (
    call :warn "Gradle was found but its version could not be parsed. Run gradle --version manually."
  )
  call :log "INFO" "Gradle version command output:"
  call "!GRADLE_CMD!" --version >>"%REPORT%" 2>&1
) else (
  call :fail "Gradle not found. Set GRADLE_HOME or add Gradle to PATH."
)

echo.
echo [4/6] Project files
if exist "%PROJECT_DIR%settings.gradle.kts" (call :pass "settings.gradle.kts found.") else (call :fail "settings.gradle.kts is missing.")
if exist "%PROJECT_DIR%build.gradle.kts" (call :pass "Root build.gradle.kts found.") else (call :fail "Root build.gradle.kts is missing.")
if exist "%PROJECT_DIR%app\build.gradle.kts" (call :pass "app\build.gradle.kts found.") else (call :fail "app\build.gradle.kts is missing.")
if exist "%PROJECT_DIR%app\src\main\AndroidManifest.xml" (call :pass "AndroidManifest.xml found.") else (call :fail "AndroidManifest.xml is missing.")
if exist "%PROJECT_DIR%gradlew.bat" (
  call :pass "Gradle Wrapper found."
) else (
  call :warn "gradlew.bat is absent; this project currently relies on a separately installed Gradle."
)

echo.
echo [5/6] Gradle cache and write permissions
set "CACHE_DIR=%GRADLE_USER_HOME%"
if not defined CACHE_DIR set "CACHE_DIR=%USERPROFILE%\.gradle"
call :log "INFO" "Gradle user home: !CACHE_DIR!"
if not exist "!CACHE_DIR!" mkdir "!CACHE_DIR!" >nul 2>nul
if exist "!CACHE_DIR!" (
  set "TEST_FILE=!CACHE_DIR!\.droidshare_write_test_!RANDOM!.tmp"
  >"!TEST_FILE!" echo test
  if exist "!TEST_FILE!" (
    del "!TEST_FILE!" >nul 2>nul
    call :pass "Gradle cache directory exists and is writable."
  ) else (
    call :fail "Gradle cache directory is not writable: !CACHE_DIR!"
  )
) else (
  call :fail "Cannot create Gradle cache directory: !CACHE_DIR!"
)
if exist "%PROJECT_DIR%." (
  set "TEST_FILE=%PROJECT_DIR%.droidshare_write_test_!RANDOM!.tmp"
  >"!TEST_FILE!" echo test
  if exist "!TEST_FILE!" (
    del "!TEST_FILE!" >nul 2>nul
    call :pass "Project directory is writable."
  ) else (
    call :fail "Project directory is not writable; build outputs cannot be created."
  )
)

echo.
echo [6/6] Build configuration hints
findstr /C:"com.android.application" "%PROJECT_DIR%build.gradle.kts" >nul 2>nul
if not errorlevel 1 (
  call :log "INFO" "Android Gradle Plugin declaration found in root build.gradle.kts."
) else (
  call :warn "Could not detect Android Gradle Plugin declaration in root build.gradle.kts."
)
findstr /C:"compileSdk = 35" "%PROJECT_DIR%app\build.gradle.kts" >nul 2>nul
if not errorlevel 1 (
  call :pass "Project compileSdk is 35."
) else (
  call :warn "Could not confirm compileSdk = 35; verify the SDK platform requirement."
)

echo.
echo ==============================================
echo   Check summary
echo ==============================================
echo SUCC: !PASS!
echo WARN: !WARN!
echo FAIL: !FAIL!
echo.
>>"%REPORT%" echo.
>>"%REPORT%" echo Summary: PASS=!PASS! WARN=!WARN! FAIL=!FAIL!
echo Full report saved to:
echo "%REPORT%"
if !FAIL! GTR 0 (
  echo.
  echo RESULT: Environment has blocking problems. Fix FAIL items before building.
) else if !WARN! GTR 0 (
  echo.
  echo RESULT: No blocking failures detected, but review WARN items.
) else (
  echo.
  echo RESULT: Basic environment checks passed.
)
echo.
pause
endlocal
exit /b

:info
echo %~1
>>"%REPORT%" echo %~1
exit /b

:pass
set /a PASS+=1
powershell -NoProfile -ExecutionPolicy Bypass -Command "Write-Host '[SUCC] %~1' -ForegroundColor Green"
>>"%REPORT%" echo [SUCC] %~1
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
