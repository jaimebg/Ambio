#!/usr/bin/env bash
# Every key in values/strings.xml must appear in every values-*/strings.xml of
# the same module. Lint's MissingTranslation does this too, but only on a full
# lint run; this answers in a second.
set -euo pipefail
status=0
for module in feature/home feature/settings feature/stats; do
  base="$module/src/main/res/values/strings.xml"
  [ -f "$base" ] || continue
  keys=$(grep -oE '<(string|plurals) name="[^"]+"' "$base" | sed -E 's/.*name="([^"]+)"/\1/')
  for dir in "$module"/src/main/res/values-*/; do
    file="$dir/strings.xml"
    for key in $keys; do
      grep -q "name=\"$key\"" "$file" || { echo "missing $key in $file"; status=1; }
    done
  done
done
[ $status -eq 0 ] && echo "all keys translated"
exit $status
