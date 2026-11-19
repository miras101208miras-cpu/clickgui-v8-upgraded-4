@echo off
setlocal
cd /d "%~dp0"
echo ==== ClickGUI mod builder ====
where java >nul 2>nul || (echo [!] Java not found. Install JDK 21: https://adoptium.net/ & pause & exit /b 1)
powershell -NoProfile -ExecutionPolicy Bypass -File "tools\setup.ps1" || (echo [!] Setup failed. Check internet/DNS or copy .gradle-dist from previous folder & pause & exit /b 1)
set "GRADLE_CMD=.gradle-dist\gradle-9.2.1\bin\gradle.bat"
if exist ".gradle-bin.txt" set /p GRADLE_CMD=<".gradle-bin.txt"
call "%GRADLE_CMD%" build --no-daemon
if errorlevel 1 (
    echo [!] Online build failed, trying offline cache...
    call "%GRADLE_CMD%" build --no-daemon --offline || (echo [!] Build failed. Copy the error above and send it. & pause & exit /b 1)
)
if not exist out mkdir out
for %%f in (build\libs\*.jar) do (
    echo %%~nf | findstr /i sources >nul || copy /y "%%f" out\ >nul
)
echo.
echo DONE! Your mod is in the "out" folder.
echo Put it into .minecraft\mods together with Fabric API.
pause
