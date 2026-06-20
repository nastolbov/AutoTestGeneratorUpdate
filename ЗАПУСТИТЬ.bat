@echo off
rem Запуск AutoTestGenerator в один клик, без чёрного окна.
set "ROOT=%~dp0"
set "JDK=%ROOT%tools\jdk\bin\javaw.exe"
set "JAR=%ROOT%target\AutoTestGenerator.jar"

if exist "%JDK%" (
    start "" "%JDK%" -jar "%JAR%"
) else (
    start "" javaw -jar "%JAR%"
)