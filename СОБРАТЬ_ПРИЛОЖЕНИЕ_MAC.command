#!/bin/bash
# Собирает настоящее macOS-приложение AutoTestGenerator.app из готового jar.
# Запуск: двойной клик по этому файлу в Finder (или ./СОБРАТЬ_ПРИЛОЖЕНИЕ_MAC.command в Терминале).
set -e
cd "$(dirname "$0")"

JAR="target/AutoTestGenerator.jar"

echo "=== 1/3. Проверяю, собран ли jar ==="
if [ ! -f "$JAR" ]; then
  echo "jar не найден — собираю через Maven..."
  mvn -DskipTests package
fi

echo "=== 2/3. Готовлю чистую папку с одним jar ==="
rm -rf build-app dist
mkdir -p build-app
cp "$JAR" build-app/

echo "=== 3/3. Собираю AutoTestGenerator.app через jpackage ==="
rm -rf "dist"
jpackage \
  --type app-image \
  --name AutoTestGenerator \
  --input build-app \
  --main-jar AutoTestGenerator.jar \
  --main-class ru.autotestgen.ui.Launcher \
  --dest dist

rm -rf build-app
echo ""
echo "Готово! Приложение здесь:  dist/AutoTestGenerator.app"
echo "Перетащите его в папку «Программы» и запускайте двойным кликом."