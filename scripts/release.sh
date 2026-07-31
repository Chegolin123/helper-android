#!/usr/bin/env bash
# release.sh — выпуск новой версии Helper.
#
# Использование:
#   ./scripts/release.sh 1.4.0
#
# Что делает:
#   1. Бампит versionName/versionCode в app/build.gradle.kts
#   2. Коммитит и тегает (v1.4.0)
#   3. Пушит main + тег
#   4. Собирает debug APK
#   5. Создаёт GitHub Release с APK как ассет "helper.apk"
#      → приложение при запуске увидит новый релиз и предложит обновиться.
#
# Требования: JDK 17, Android SDK, git с доступом к origin, токен GitHub
# (берётся из git credential helper или $GH_TOKEN).

set -euo pipefail

VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
  echo "❌ Укажите версию: ./scripts/release.sh 1.4.0"
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> Релиз Helper v$VERSION"

# 1. Бамп версии. versionCode = простая схема: major*10000 + minor*100 + patch.
MAJOR="$(echo "$VERSION" | cut -d. -f1)"
MINOR="$(echo "$VERSION" | cut -d. -f2)"
PATCH="$(echo "$VERSION" | cut -d. -f3)"
CODE=$(( MAJOR * 10000 + MINOR * 100 + PATCH ))

GRADLE="$ROOT/app/build.gradle.kts"
# Платформо-независимая замена через perl.
perl -0pi -e "s/versionCode = \\d+/versionCode = $CODE/" "$GRADLE"
perl -0pi -e "s/versionName = \"[^\"]+\"/versionName = \"$VERSION\"/" "$GRADLE"
echo "✓ versionName=$VERSION versionCode=$CODE"

# 2-3. Коммит + тег + push.
git add "$GRADLE"
git commit -m "🔖 Release v$VERSION" || echo "(ничего коммитить — уже актуально)"
git tag "v$VERSION"
git push origin main
git push origin "v$VERSION"
echo "✓ Запушено v$VERSION"

# 4. Сборка APK.
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home 2>/dev/null || echo)}"
export ANDROID_HOME="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
echo "==> Сборка APK..."
(./gradlew.bat :app:assembleDebug --no-daemon -q 2>/dev/null || ./gradlew :app:assembleDebug --no-daemon -q)
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$APK" ]] || { echo "❌ APK не собрался"; exit 1; }
echo "✓ APK готов: $(du -h "$APK" | cut -f1)"

# 5. GitHub Release через API.
TOKEN="${GH_TOKEN:-$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill 2>/dev/null | sed -n 's/^password=//p')}"
[[ -n "$TOKEN" ]] || { echo "❌ Нет GitHub-токена (GH_TOKEN или git credential)"; exit 1; }
OWNER="Chegolin123"
REPO="helper-android"

echo "==> Создание Release v$VERSION..."
# JSON через файл (относительный путь — надёжнее на Windows с Git Bash).
RELEASE_PAYLOAD=".release_payload.json"
python -c "import json,os; os.chdir(os.environ.get('PWD','.')); open('.release_payload.json','w',encoding='utf-8').write(json.dumps({'tag_name':'v$VERSION','name':'Helper v$VERSION','body':'Релиз v$VERSION. Обновление доступно из приложения.','prerelease':False}, ensure_ascii=False))"
RELEASE_RESP=$(curl -s -X POST -H "Authorization: token $TOKEN" -H "Content-Type: application/json" \
  --data-binary @"$RELEASE_PAYLOAD" \
  "https://api.github.com/repos/$OWNER/$REPO/releases")
rm -f "$RELEASE_PAYLOAD"
UPLOAD_URL="$(echo "$RELEASE_RESP" | python -c "import sys,json; print(json.load(sys.stdin).get('upload_url','').split('{')[0])" 2>/dev/null || echo "")"
[[ -n "$UPLOAD_URL" ]] || { echo "❌ Не удалось создать release: $RELEASE_RESP"; exit 1; }

echo "==> Загрузка APK как helper.apk..."
curl -s -X POST -H "Authorization: token $TOKEN" \
  -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary @"$APK" \
  "${UPLOAD_URL}?name=helper.apk" \
  | python -c "import sys,json; d=json.load(sys.stdin); print('✓ Ассет:', d.get('name','?'), d.get('browser_download_url','')) if d.get('name') else print('❌ upload failed:', d)"

echo ""
echo "🎉 Готово! v$VERSION опубликован."
echo "   Приложения при запуске увидят обновление и предложат установить."
