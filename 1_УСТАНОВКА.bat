@echo off
chcp 65001 >nul
title AutoTestGenerator - УСТАНОВКА (шаг 1 из 2)
setlocal enabledelayedexpansion

REM ============================================================
REM  Скрипт скачивает портативные JDK 17 и Apache Maven в папку
REM  tools\ рядом с собой. Права администратора НЕ нужны, PATH
REM  системы НЕ меняется. Запускать один раз. Нужен интернет.
REM ============================================================

set "ROOT=%~dp0"
set "TOOLS=%ROOT%tools"
set "JDK_DIR=%TOOLS%\jdk"
set "MVN_DIR=%TOOLS%\maven"

set "JDK_URL=https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse"
set "MVN_VER=3.9.9"
set "MVN_URL=https://archive.apache.org/dist/maven/maven-3/%MVN_VER%/binaries/apache-maven-%MVN_VER%-bin.zip"

echo.
echo ==========================================================
echo   Установка окружения для AutoTestGenerator
echo   (скачаются Java 17 и Maven - примерно 200-250 МБ)
echo ==========================================================
echo.

if not exist "%TOOLS%" mkdir "%TOOLS%"

REM ---------- 1. JDK 17 ----------
if exist "%JDK_DIR%\bin\java.exe" (
    echo [1/3] Java 17 уже установлена в tools\jdk - пропускаю.
) else (
    echo [1/3] Скачиваю Java 17 (Temurin)...
    powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; try { Invoke-WebRequest -Uri '%JDK_URL%' -OutFile '%TOOLS%\jdk.zip' -UseBasicParsing } catch { Write-Host $_.Exception.Message; exit 1 }"
    if errorlevel 1 goto :neterror
    echo       Распаковываю Java...
    if exist "%TOOLS%\jdk_tmp" rmdir /s /q "%TOOLS%\jdk_tmp"
    powershell -NoProfile -Command "Expand-Archive -Path '%TOOLS%\jdk.zip' -DestinationPath '%TOOLS%\jdk_tmp' -Force"
    if errorlevel 1 goto :unziperror
    for /d %%D in ("%TOOLS%\jdk_tmp\*") do move "%%D" "%JDK_DIR%" >nul
    rmdir /s /q "%TOOLS%\jdk_tmp" 2>nul
    del "%TOOLS%\jdk.zip" 2>nul
    if not exist "%JDK_DIR%\bin\java.exe" goto :unziperror
    echo       Java установлена.
)

REM ---------- 2. Maven ----------
if exist "%MVN_DIR%\bin\mvn.cmd" (
    echo [2/3] Maven уже установлен в tools\maven - пропускаю.
) else (
    echo [2/3] Скачиваю Apache Maven %MVN_VER%...
    powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; try { Invoke-WebRequest -Uri '%MVN_URL%' -OutFile '%TOOLS%\maven.zip' -UseBasicParsing } catch { Write-Host $_.Exception.Message; exit 1 }"
    if errorlevel 1 goto :neterror
    echo       Распаковываю Maven...
    if exist "%TOOLS%\mvn_tmp" rmdir /s /q "%TOOLS%\mvn_tmp"
    powershell -NoProfile -Command "Expand-Archive -Path '%TOOLS%\maven.zip' -DestinationPath '%TOOLS%\mvn_tmp' -Force"
    if errorlevel 1 goto :unziperror
    for /d %%D in ("%TOOLS%\mvn_tmp\*") do move "%%D" "%MVN_DIR%" >nul
    rmdir /s /q "%TOOLS%\mvn_tmp" 2>nul
    del "%TOOLS%\maven.zip" 2>nul
    if not exist "%MVN_DIR%\bin\mvn.cmd" goto :unziperror
    echo       Maven установлен.
)

REM ---------- 3. Прогрев зависимостей ----------
echo [3/3] Скачиваю зависимости программы (JavaFX, SQLite)...
echo       Это может занять 1-3 минуты при первом запуске.
set "JAVA_HOME=%JDK_DIR%"
set "PATH=%JDK_DIR%\bin;%MVN_DIR%\bin;%PATH%"
pushd "%ROOT%"
call mvn -q -DskipTests compile
set "MVN_RC=%errorlevel%"
popd
if not "%MVN_RC%"=="0" (
    echo.
    echo [!] Не удалось скачать зависимости. Проверьте интернет и запустите скрипт снова.
    goto :end
)

echo.
echo ==========================================================
echo   ГОТОВО! Окружение установлено.
echo   Теперь запустите файл  2_ЗАПУСК.bat
echo ==========================================================
goto :end

:neterror
echo.
echo [!] ОШИБКА СКАЧИВАНИЯ. Нет доступа в интернет или сайт недоступен.
echo     Проверьте подключение и запустите 1_УСТАНОВКА.bat снова.
goto :end

:unziperror
echo.
echo [!] ОШИБКА РАСПАКОВКИ архива. Удалите папку tools и повторите.
goto :end

:end
echo.
pause
endlocal
