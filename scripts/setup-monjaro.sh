#!/usr/bin/env bash
# Настройка MCleaner + Shizuku на магнитоле Geely Monjaro с ноутбука по ADB.
#
#   ./setup-monjaro.sh                 # первая настройка: установка, права, запуск Shizuku
#   ./setup-monjaro.sh --start-only    # после перезагрузки магнитолы: только запустить Shizuku
#   ./setup-monjaro.sh 192.168.1.50    # то же по сети (adb connect IP:5555)
#
# Без папки проекта (нужны только adb и curl): скачайте этот файл и запустите его —
#   curl -fsSLO https://raw.githubusercontent.com/harms-danil/MonjaroCleaner/main/scripts/setup-monjaro.sh
#   bash setup-monjaro.sh --start-only
#
# APK ищутся рядом с папкой проекта (../../), рядом со скриптом, в текущей папке
# или в переменных MCLEANER_APK / SHIZUKU_APK. Если их нет — скачиваются последние релизы с GitHub.
set -euo pipefail

APP=app.mcleaner
SHIZUKU=moe.shizuku.privileged.api
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Первая папка, где лежат APK: рядом с проектом, рядом со скриптом или текущая.
find_apk() { # $1 = шаблон имени
  local d f
  for d in "$HERE/../.." "$HERE" "$(pwd)"; do
    f="$(ls -t "$d"/$1 2>/dev/null | head -1 || true)"
    [[ -n "$f" ]] && { echo "$f"; return 0; }
  done
  return 0
}

start_only=false
target=""
for arg in "$@"; do
  case "$arg" in
    --start-only) start_only=true ;;
    -h|--help) sed -n '2,14p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) target="$arg" ;;
  esac
done

say()  { printf '\033[1;36m▶ %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m✓ %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m! %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

# --- adb ---------------------------------------------------------------
ADB="$(command -v adb || true)"
[[ -z "$ADB" && -x "$HOME/Library/Android/sdk/platform-tools/adb" ]] && ADB="$HOME/Library/Android/sdk/platform-tools/adb"
[[ -n "$ADB" ]] || die "adb не найден. Установите platform-tools или Android SDK."

if [[ -n "$target" ]]; then
  [[ "$target" == *:* ]] || target="$target:5555"
  say "Подключаюсь к $target"
  "$ADB" connect "$target" >/dev/null
  export ANDROID_SERIAL="$target"
fi

say "Жду магнитолу (USB или сеть)…"
"$ADB" wait-for-device
dsh() { "$ADB" shell "$@" | tr -d '\r'; }
shizuku_running() { [[ "$(dsh ps -A)" == *shizuku_server* ]]; }

devices=$("$ADB" devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
[[ -n "${ANDROID_SERIAL:-}" || "$devices" -le 1 ]] || die "Подключено несколько устройств. Укажите одно: ANDROID_SERIAL=<serial> $0"

ok "Устройство: $(dsh getprop ro.product.manufacturer) $(dsh getprop ro.product.model), Android $(dsh getprop ro.build.version.release)"

# --- установка ---------------------------------------------------------
# Скачивает APK последнего релиза репозитория $1 (имя файла — регулярка $2) во временную папку,
# печатает путь. Сообщения — в stderr, чтобы не смешаться с путём.
download_latest() {
  local repo="$1" pattern="$2" url dir
  command -v curl >/dev/null || { warn "Нет curl — не могу скачать $repo" >&2; return 0; }
  url="$(curl -fsSL "https://api.github.com/repos/$repo/releases/latest" 2>/dev/null \
    | grep -oE '"browser_download_url": *"[^"]+"' | cut -d'"' -f4 | grep -E "$pattern" | head -1 || true)"
  [[ -n "$url" ]] || { warn "Не нашёл APK в последнем релизе $repo" >&2; return 0; }
  dir="${TMPDIR:-/tmp}/mcleaner-setup"; mkdir -p "$dir"
  say "Скачиваю $(basename "$url")" >&2
  curl -fsSL -o "$dir/$(basename "$url")" "$url" && echo "$dir/$(basename "$url")"
}
install_apk() { # $1 = путь, $2 = имя для вывода
  say "Устанавливаю $2 ($(basename "$1"))"
  "$ADB" install -r "$1" | tail -1
}

if ! $start_only; then
  MCLEANER_APK="${MCLEANER_APK:-$(find_apk 'MCleaner-*.apk')}"
  SHIZUKU_APK="${SHIZUKU_APK:-$(find_apk 'shizuku-*.apk')}"
  [[ -f "$MCLEANER_APK" ]] || MCLEANER_APK="$(download_latest harms-danil/MonjaroCleaner 'MCleaner-.*\.apk$')"
  [[ -f "$SHIZUKU_APK" ]] || SHIZUKU_APK="$(download_latest RikkaApps/Shizuku 'shizuku-.*-release\.apk$')"
  [[ -f "$MCLEANER_APK" ]] || die "Не удалось получить MCleaner APK (нет файла рядом и нет связи с GitHub)"
  [[ -f "$SHIZUKU_APK" ]] || die "Не удалось получить Shizuku APK (нет файла рядом и нет связи с GitHub)"

  install_apk "$SHIZUKU_APK" "Shizuku"
  install_apk "$MCLEANER_APK" "MCleaner"

  say "Выдаю MCleaner доступ к статистике и файлам"
  dsh appops set "$APP" GET_USAGE_STATS allow
  if [[ "$(dsh getprop ro.build.version.sdk)" -ge 30 ]]; then
    dsh appops set "$APP" MANAGE_EXTERNAL_STORAGE allow
  else
    # Android 9/10: без READ приложение не видит общую память, одного WRITE мало.
    dsh pm grant "$APP" android.permission.READ_EXTERNAL_STORAGE || true
    dsh pm grant "$APP" android.permission.WRITE_EXTERNAL_STORAGE || true
  fi
  # Не даём системе «экономить батарею» на автоускорении.
  dsh dumpsys deviceidle whitelist "+$APP" >/dev/null || true
  ok "Права выданы"
fi

# Права на память на Android 9/10 выдаём и в режиме --start-only: без READ
# MCleaner 1.2.0 не может скачать обновление (Permission denied).
if $start_only && [[ "$(dsh getprop ro.build.version.sdk)" -lt 30 ]] && [[ "$(dsh pm path "$APP")" == *package:* ]]; then
  dsh pm grant "$APP" android.permission.READ_EXTERNAL_STORAGE || true
  dsh pm grant "$APP" android.permission.WRITE_EXTERNAL_STORAGE || true
fi

# --- запуск Shizuku ----------------------------------------------------
[[ "$(dsh pm path "$SHIZUKU")" == *package:* ]] || die "Shizuku не установлен. Запустите скрипт без --start-only."

# Всегда перезапускаем: процесс shizuku_server может остаться после сбоя системы, но уже без связи с ней.
# Стартер Shizuku сам завершает старый процесс.
{
  if shizuku_running; then say "Перезапускаю Shizuku"; else say "Запускаю Shizuku"; fi
  apk_dir="$(dirname "$(dsh pm path "$SHIZUKU" | head -1 | cut -d: -f2)")"
  starter=""
  for abi in arm64 arm; do
    if [[ "$(dsh "[ -x '$apk_dir/lib/$abi/libshizuku.so' ] && echo yes")" == yes ]]; then
      starter="$apk_dir/lib/$abi/libshizuku.so"; break
    fi
  done
  if [[ -n "$starter" ]]; then
    dsh "$starter" || true
  else
    # Старый способ: скрипт, который Shizuku создаёт после первого открытия.
    dsh am start -n "$SHIZUKU/moe.shizuku.manager.MainActivity" >/dev/null 2>&1 || true
    sleep 3
    dsh sh /storage/emulated/0/Android/data/$SHIZUKU/start.sh || true
  fi
  sleep 2
  shizuku_running || die "Shizuku не запустился. Откройте Shizuku на магнитоле и посмотрите команду в «Запуск через подключение к компьютеру»."
  ok "Shizuku запущен"
}

# --- финал -------------------------------------------------------------
dsh am start -n "$APP/.MainActivity" >/dev/null 2>&1 || true
echo
ok "Готово. MCleaner открыт на магнитоле."
if ! $start_only; then
  warn "Остался один шаг руками: MCleaner → «Настройки» → «Запросить разрешение» → «Разрешить всегда»."
  warn "После полной перезагрузки магнитолы запустите: $0 --start-only"
fi
