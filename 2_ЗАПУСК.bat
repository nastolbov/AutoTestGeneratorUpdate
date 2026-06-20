@echo off
chcp 65001 >nul
title AutoTestGenerator - ЗАПУСК (шаг 2 из 2)

set "ROOT=%~dp0"
set "JDK_DIR=%ROOT%tools\jdk"
set "MVN_DIR=%ROOT%tools\maven"

if not exist "%JDK_DIR%\bin\java.exe" goto :noenv
if not exist "%MVN_DIR%\bin\mvn.cmd" goto :noenv

set "JAVA_HOME=%JDK_DIR%"
set "PATH=%JDK_DIR%\bin;%MVN_DIR%\bin;%PATH%"

echo.
echo Запускаю AutoTestGenerator...
echo (первый запуск может занять 10-20 секунд)
echo Не закрывайте это чёрное окно, пока работаете с программой.
echo.

cd /d "%ROOT%"

rem Если собран "толстый" jar — запускаем его напрямую (быстро, без пересборки).
rem Иначе откатываемся на mvn javafx:run (соберёт и запустит из исходников).
if exist "%ROOT%target\AutoTestGenerator.jar" (
    call java -jar "%ROOT%target\AutoTestGenerator.jar"
) else (
    call mvn -q javafx:run
)
set "RC=%errorlevel%"
if not "%RC%"=="0" (
    echo.
    echo [!] Программа завершилась с ошибкой. Сообщения см. выше.
    echo     Если это первый запуск - проверьте интернет и повторите.
    pause
)
goto :eof

:noenv
echo.
echo ==========================================================
echo   Окружение не установлено.
echo   Сначала запустите файл  1_УСТАНОВКА.bat
echo ==========================================================
echo.
pause
