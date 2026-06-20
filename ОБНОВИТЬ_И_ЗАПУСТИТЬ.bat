@echo off
chcp 65001 >nul
title AutoTestGenerator - Обновить и запустить

set "ROOT=%~dp0"
set "JDK_DIR=%ROOT%tools\jdk"
set "MVN_DIR=%ROOT%tools\maven"

rem Подключаем встроенные JDK и Maven, если они есть (после 1_УСТАНОВКА.bat)
if exist "%JDK_DIR%\bin\java.exe" set "JAVA_HOME=%JDK_DIR%"
if exist "%JDK_DIR%\bin\java.exe" set "PATH=%JDK_DIR%\bin;%MVN_DIR%\bin;%PATH%"

cd /d "%ROOT%"

echo.
echo === Шаг 1. Забираю свежую версию из репозитория ===
call git pull origin claude/jolly-cannon-1djd3q
if not "%errorlevel%"=="0" (
    echo [!] Не удалось обновиться из git. Проверьте интернет/доступ.
    pause
    goto :eof
)

echo.
echo === Шаг 2. Чищу старые сгенерированные тесты ===
if exist "%ROOT%generated-tests" rmdir /s /q "%ROOT%generated-tests"

echo.
echo === Шаг 3. Пересобираю программу (толстый jar) ===
call mvn -q -DskipTests package
if not "%errorlevel%"=="0" (
    echo [!] Сборка не удалась. Сообщения см. выше.
    pause
    goto :eof
)

echo.
echo === Шаг 4. Запускаю программу ===
start "" javaw -jar "%ROOT%target\AutoTestGenerator.jar"

echo Готово. Окно программы скоро откроется.