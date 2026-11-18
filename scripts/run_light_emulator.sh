#!/bin/sh
set -eu

sdk_dir="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
adb="$sdk_dir/platform-tools/adb"
avd="DriveNext_API_34"
dns_server="$(networksetup -getinfo Wi-Fi | awk '/^Router:/{print $2; exit}')"
if [ -z "$dns_server" ]; then
    dns_server="$(route -n get default | awk '/gateway:/{print $2; exit}')"
fi

if [ -z "$dns_server" ]; then
    echo "Не удалось найти DNS роутера. Проверьте подключение Mac к сети." >&2
    exit 1
fi

for serial in $("$adb" devices | awk '$1 ~ /^emulator-/ {print $1}'); do
    running_avd="$("$adb" -s "$serial" emu avd name 2>/dev/null | head -n 1 | tr -d '\r')"
    if [ "$running_avd" = "$avd" ]; then
        echo "Перезапускаю $avd с рабочим DNS..."
        "$adb" -s "$serial" emu kill >/dev/null
        while "$adb" devices | awk '$1 ~ /^emulator-/ {found=1} END {exit !found}'; do
            sleep 1
        done
        sleep 3
    fi
done

"$sdk_dir/emulator/emulator" -avd "$avd" \
    -dns-server "$dns_server" -no-audio -no-boot-anim "$@" &
emulator_pid=$!

echo "Дожидаюсь загрузки $avd..."
"$adb" wait-for-device
until [ "$("$adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    if ! kill -0 "$emulator_pid" 2>/dev/null; then
        wait "$emulator_pid"
        exit 1
    fi
    sleep 2
done

"$adb" shell settings put global private_dns_specifier dns.google
"$adb" shell settings put global private_dns_mode hostname
echo "Эмулятор запущен. Защищённый DNS включён; теперь можно нажать Run app в Android Studio."
wait "$emulator_pid"
