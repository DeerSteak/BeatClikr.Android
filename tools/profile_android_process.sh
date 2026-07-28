#!/usr/bin/env bash
set -euo pipefail

package_name="${1:?package name required}"
sample_count="${2:-180}"
interval_seconds="${3:-10}"
output_file="${4:-android-process-profile.csv}"
adb_path="${ADB_PATH:-adb}"

echo "elapsed_seconds,cpu_percent,pss_kb,rss_kb,battery_c,thermal_status,soc_c" > "$output_file"
start_seconds="$(date +%s)"

for ((sample = 1; sample <= sample_count; sample++)); do
    target_seconds="$((start_seconds + (sample - 1) * interval_seconds))"
    pid="$("$adb_path" shell pidof "$package_name" | tr -d '\r')"
    if [[ -z "$pid" ]]; then
        echo "Process unavailable at sample $sample" >&2
        exit 1
    fi

    top_line="$("$adb_path" shell top -b -n 1 -p "$pid" | tail -1)"
    memory_line="$("$adb_path" shell dumpsys meminfo "$package_name" | grep 'TOTAL PSS:')"
    battery_line="$("$adb_path" shell dumpsys battery | grep 'temperature:')"
    thermal_output="$("$adb_path" shell dumpsys thermalservice)"

    cpu_percent="$(awk '{print $9}' <<< "$top_line")"
    pss_kb="$(awk '{print $3}' <<< "$memory_line")"
    rss_kb="$(awk '{print $6}' <<< "$memory_line")"
    battery_tenths="$(awk '{print $2}' <<< "$battery_line")"
    thermal_status="$(awk '/Thermal Status:/ {print $3; exit}' <<< "$thermal_output")"
    soc_c="$(sed -n 's/.*mValue=\([^,]*\).*mName=soc_therm.*/\1/p' <<< "$thermal_output" | head -1)"
    elapsed_seconds="$(($(date +%s) - start_seconds))"

    awk -v elapsed="$elapsed_seconds" \
        -v cpu="$cpu_percent" \
        -v pss="$pss_kb" \
        -v rss="$rss_kb" \
        -v battery="$battery_tenths" \
        -v thermal="$thermal_status" \
        -v soc="${soc_c:-}" \
        'BEGIN { printf "%d,%.3f,%d,%d,%.1f,%d,%s\n", elapsed, cpu, pss, rss, battery / 10, thermal, soc }' \
        >> "$output_file"

    sleep_seconds="$((target_seconds + interval_seconds - $(date +%s)))"
    if ((sleep_seconds > 0)); then
        sleep "$sleep_seconds"
    fi
done
