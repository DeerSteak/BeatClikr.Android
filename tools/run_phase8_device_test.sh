#!/usr/bin/env bash
set -euo pipefail

label="${1:?usage: run_phase8_device_test.sh LABEL -- GRADLE_ARGS...}"
shift
if [[ "${1:-}" != "--" || $# -lt 2 ]]; then
    echo "usage: run_phase8_device_test.sh LABEL -- GRADLE_ARGS..." >&2
    exit 2
fi
shift

adb_path="${ADB_PATH:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb}"
export ADB_MDNS_AUTO_CONNECT="${ADB_MDNS_AUTO_CONNECT:-0}"
transports="$("$adb_path" devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
transport_count="$(printf '%s\n' "$transports" | awk 'NF { count++ } END { print count + 0 }')"
if [[ "$transport_count" -ne 1 ]]; then
    echo "Expected exactly one active ADB transport; found $transport_count." >&2
    "$adb_path" devices -l >&2
    exit 1
fi

serial="$transports"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
artifact_dir="benchmarks/raw/phase8/${timestamp}-${label}"
mkdir -p "$artifact_dir"
command_file="$artifact_dir/command.txt"
metadata_file="$artifact_dir/metadata.txt"
gradle_log="$artifact_dir/gradle.log"
screen_mode="${PHASE8_SCREEN_MODE:-on}"
original_stay_awake="$("$adb_path" -s "$serial" shell settings get global stay_on_while_plugged_in | tr -d '\r')"
original_brightness_mode="$("$adb_path" -s "$serial" shell settings get system screen_brightness_mode | tr -d '\r')"
original_brightness="$("$adb_path" -s "$serial" shell settings get system screen_brightness | tr -d '\r')"
original_screen_timeout="$("$adb_path" -s "$serial" shell settings get system screen_off_timeout | tr -d '\r')"
original_media_volume="$("$adb_path" -s "$serial" shell cmd media_session volume --stream 3 --get | awk '/volume is/{print $4}')"

restore_device_settings() {
    if [[ "$original_stay_awake" == "null" ]]; then
        "$adb_path" -s "$serial" shell settings delete global stay_on_while_plugged_in >/dev/null
    else
        "$adb_path" -s "$serial" shell settings put global stay_on_while_plugged_in "$original_stay_awake"
    fi
    "$adb_path" -s "$serial" shell settings put system screen_brightness_mode "$original_brightness_mode"
    "$adb_path" -s "$serial" shell settings put system screen_brightness "$original_brightness"
    "$adb_path" -s "$serial" shell settings put system screen_off_timeout "$original_screen_timeout"
    "$adb_path" -s "$serial" shell cmd media_session volume --stream 3 --set "$original_media_volume" >/dev/null
}

if [[ "$screen_mode" == "on" ]]; then
    "$adb_path" -s "$serial" shell svc power stayon true
    "$adb_path" -s "$serial" shell input keyevent KEYCODE_WAKEUP
    "$adb_path" -s "$serial" shell wm dismiss-keyguard
    trap restore_device_settings EXIT
elif [[ "$screen_mode" != "unchanged" ]]; then
    echo "PHASE8_SCREEN_MODE must be 'on' or 'unchanged'." >&2
    exit 2
fi

if [[ -n "${PHASE8_BRIGHTNESS:-}" ]]; then
    "$adb_path" -s "$serial" shell settings put system screen_brightness_mode 0
    "$adb_path" -s "$serial" shell settings put system screen_brightness "$PHASE8_BRIGHTNESS"
fi
if [[ -n "${PHASE8_SCREEN_TIMEOUT_MS:-}" ]]; then
    "$adb_path" -s "$serial" shell settings put system screen_off_timeout "$PHASE8_SCREEN_TIMEOUT_MS"
fi
if [[ -n "${PHASE8_MEDIA_VOLUME:-}" ]]; then
    "$adb_path" -s "$serial" shell cmd media_session volume --stream 3 --set "$PHASE8_MEDIA_VOLUME" >/dev/null
    applied_media_volume="$("$adb_path" -s "$serial" shell cmd media_session volume --stream 3 --get | awk '/volume is/{print $4}')"
    if [[ "$applied_media_volume" != "$PHASE8_MEDIA_VOLUME" ]]; then
        echo "Unable to set media volume to $PHASE8_MEDIA_VOLUME; device remained at $applied_media_volume." >&2
        exit 1
    fi
fi

printf 'ANDROID_SERIAL=%q ./gradlew' "$serial" > "$command_file"
printf ' %q' "$@" >> "$command_file"
printf '\n' >> "$command_file"

capture_metadata() {
    local stage="$1"
    {
        printf 'stage=%s\n' "$stage"
        printf 'captured_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        printf 'source_commit=%s\n' "$(git rev-parse HEAD)"
        printf 'working_tree_porcelain_v1_begin\n'
        git status --porcelain=v1
        printf 'working_tree_porcelain_v1_end\n'
        printf 'serial=%s\n' "$serial"
        printf 'device=%s\n' "$("$adb_path" -s "$serial" shell getprop ro.product.model | tr -d '\r')"
        printf 'device_code=%s\n' "$("$adb_path" -s "$serial" shell getprop ro.product.device | tr -d '\r')"
        printf 'os_release=%s\n' "$("$adb_path" -s "$serial" shell getprop ro.build.version.release | tr -d '\r')"
        printf 'build_fingerprint=%s\n' "$("$adb_path" -s "$serial" shell getprop ro.build.fingerprint | tr -d '\r')"
        printf 'brightness_mode=%s\n' "$("$adb_path" -s "$serial" shell settings get system screen_brightness_mode | tr -d '\r')"
        printf 'brightness=%s\n' "$("$adb_path" -s "$serial" shell settings get system screen_brightness | tr -d '\r')"
        printf 'screen_timeout_ms=%s\n' "$("$adb_path" -s "$serial" shell settings get system screen_off_timeout | tr -d '\r')"
        printf 'screen_mode=%s\n' "$screen_mode"
        printf 'original_brightness_mode=%s\n' "$original_brightness_mode"
        printf 'original_brightness=%s\n' "$original_brightness"
        printf 'original_screen_timeout_ms=%s\n' "$original_screen_timeout"
        printf 'media_volume=%s\n' "$("$adb_path" -s "$serial" shell cmd media_session volume --stream 3 --get | awk '/volume is/{print $4}')"
        printf 'original_media_volume=%s\n' "$original_media_volume"
        printf 'interactive=%s\n' "$("$adb_path" -s "$serial" shell dumpsys power | awk -F= '/mWakefulness=|mInteractive=/{print $2; exit}' | tr -d '\r')"
        printf 'wifi_enabled=%s\n' "$("$adb_path" -s "$serial" shell cmd wifi status | head -1 | tr -d '\r')"
        printf 'battery_begin\n'
        "$adb_path" -s "$serial" shell dumpsys battery | tr -d '\r'
        printf 'battery_end\nthermal_begin\n'
        "$adb_path" -s "$serial" shell dumpsys thermalservice | tr -d '\r'
        printf 'thermal_end\naudio_begin\n'
        "$adb_path" -s "$serial" shell dumpsys audio | tr -d '\r'
        printf 'audio_end\n'
    } >> "$metadata_file"
}

capture_metadata before
status=0
ANDROID_SERIAL="$serial" \
JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}" \
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}" \
./gradlew "$@" 2>&1 | tee "$gradle_log" || status=${PIPESTATUS[0]}
result_dir="app/build/outputs/androidTest-results/connected/benchmark"
if [[ -d "$result_dir" ]]; then
    cp -R "$result_dir" "$artifact_dir/android-test-results"
fi
capture_metadata after
printf 'exit_status=%d\n' "$status" >> "$metadata_file"
printf 'Artifacts: %s\n' "$artifact_dir"
restore_device_settings
trap - EXIT
exit "$status"
