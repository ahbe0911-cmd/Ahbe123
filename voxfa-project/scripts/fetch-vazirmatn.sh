#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FONT_DIR="$ROOT/app/src/main/res/font"
LICENSE_DIR="$ROOT/app/src/main/assets/licenses"
mkdir -p "$FONT_DIR" "$LICENSE_DIR"
curl -fsSL --retry 3 \
  https://raw.githubusercontent.com/rastikerdar/vazirmatn/master/fonts/ttf/Vazirmatn-Regular.ttf \
  -o "$FONT_DIR/vazirmatn_regular.ttf"
curl -fsSL --retry 3 \
  https://raw.githubusercontent.com/rastikerdar/vazirmatn/master/OFL.txt \
  -o "$LICENSE_DIR/Vazirmatn-OFL.txt"
test -s "$FONT_DIR/vazirmatn_regular.ttf"
test -s "$LICENSE_DIR/Vazirmatn-OFL.txt"
