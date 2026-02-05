#!/usr/bin/env bash
set -euo pipefail

: "${OUTPUT_ROOT:?OUTPUT_ROOT is required}"
: "${SRC_ROOT:?SRC_ROOT is required}"
: "${MAP_JSON:?MAP_JSON is required}"

OUT_DIR="$OUTPUT_ROOT/.cursor/rules"
SRC_RULES_DIR="$SRC_ROOT/rules"

mkdir -p "$OUT_DIR"

echo "📝 Building Cursor Project Rules (RULE.md folder) -> $OUT_DIR"
echo "MAP: $MAP_JSON"

# order: array (compatible with bash 3.x)
ORDER=()
while IFS= read -r line; do
  [[ -n "$line" ]] && ORDER+=("$line")
done < <(jq -r '.cursor.order[]' "$MAP_JSON")

if [[ ${#ORDER[@]} -eq 0 ]]; then
  echo "❌ cursor.order is empty in rules-map.json"
  exit 1
fi

for src_file in "${ORDER[@]}"; do
  id="$(jq -r --arg f "$src_file" '.rules[$f].cursor.id // empty' "$MAP_JSON")"
  if [[ -z "$id" || "$id" == "null" ]]; then
    echo "❌ Missing rules[\"$src_file\"].cursor.id in rules-map.json"
    exit 1
  fi

  src_path="$SRC_RULES_DIR/$src_file"
  if [[ ! -f "$src_path" ]]; then
    echo "❌ Source rule file not found: $src_path"
    exit 1
  fi

  out_folder="$OUT_DIR/$id"
  rm -rf "$out_folder"
  mkdir -p "$out_folder"
  out_path="$out_folder/RULE.md"

  fm_json="$(jq -c --arg f "$src_file" '.rules[$f].cursor.frontmatter // {}' "$MAP_JSON")"

  {
    echo "---"

    desc="$(echo "$fm_json" | jq -r '.description // empty')"
    if [[ -n "$desc" ]]; then
      echo "description: $desc"
    fi

    aa="$(echo "$fm_json" | jq -r '.alwaysApply // empty')"
    if [[ "$aa" == "true" || "$aa" == "false" ]]; then
      echo "alwaysApply: $aa"
    fi

    has_globs="$(echo "$fm_json" | jq -r 'has("globs")')"
    if [[ "$has_globs" == "true" ]]; then
      echo "globs:"
      echo "$fm_json" | jq -r '.globs[]? | "  - \"" + . + "\""'
    fi

    echo "---"
    echo
    cat "$src_path"
    echo
  } > "$out_path"

  echo "✅ $out_path"
done