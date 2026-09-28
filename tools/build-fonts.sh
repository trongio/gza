#!/usr/bin/env bash
# Builds the bundled fonts of :core:designsystem from pinned upstream files: instances the
# variable fonts to condensed static weights, subsets them to the scripts Gza shows, checks
# the glyph coverage and writes them to res/font, with the licences to assets/licenses.
# Needs uv (uvx). Rerunning gives identical bytes.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
font_dir="$root/core/designsystem/src/main/res/font"
license_dir="$root/core/designsystem/src/main/assets/licenses"
fonttools_version=4.66.0
gf=https://raw.githubusercontent.com/google/fonts/23e54b51ddffbc7713c583748e3bd86f62b1fa4a/ofl
firago=https://raw.githubusercontent.com/bBoxType/FiraGO/5bbcb9d066ab563686ed1de1e6f62eec0148e82d
firago_ttf="$firago/Fonts/FiraGO_TTF_1001/Roman"
material_license=https://raw.githubusercontent.com/google/material-design-icons/master/LICENSE

# Latin, Latin-1, Latin Extended-A, Cyrillic, Georgian, Mtavruli, punctuation, lari sign,
# arrows, minus, bullet. Codepoints a font lacks are skipped.
text_unicodes='U+0020-007E,U+00A0-017F,U+0400-045F,U+10A0-10FF,U+1C90-1CBF,U+2000-206F,U+20BE,U+2190-2193,U+2212,U+2022'
# The mono face only ever gets times: digits, colon, plus, minus, plus-minus, spaces.
mono_unicodes='U+0020-007E,U+00A0-00FF,U+2009,U+2013,U+2026,U+202F,U+2212'

# fontTools stamps head.modified from this, so reruns are byte-identical.
export SOURCE_DATE_EPOCH=1790640000

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

fonttools() {
	uvx --quiet --from "fonttools==$fonttools_version" fonttools "$@"
}

fetch() {
	local url=$1 out=$2 sha=${3:-}
	curl -fsSL -o "$out" "$url"
	if [[ -n $sha ]]; then
		echo "$sha  $out" | sha256sum -c --quiet - || {
			echo "sha256 mismatch for $url" >&2
			exit 1
		}
	fi
}

fetch "$gf/notosansgeorgian/NotoSansGeorgian%5Bwdth,wght%5D.ttf" "$work/noto.ttf" \
	dc591156f36842d38996c4a7a17fee9bb58e45da3e2cac7a31b7d33de700adb9
fetch "$gf/martianmono/MartianMono%5Bwdth,wght%5D.ttf" "$work/martian.ttf" \
	c3467843ec1c2574b05fbcfd7147c7bfbcf63ddca8fc2bcb9d117f1bfb1b22e7
fetch "$firago_ttf/FiraGO-Regular.ttf" "$work/firago-regular.ttf" \
	495901c0c608ea265f4c31aa2a4c7a313e5cc2a3dd610da78a447fe8e07454a2
fetch "$firago_ttf/FiraGO-Medium.ttf" "$work/firago-medium.ttf" \
	5f753a48c7dff5b7af294e76624febb28c41071a5a65c0fd8a024ea9d1491e8a
fetch "$firago_ttf/FiraGO-SemiBold.ttf" "$work/firago-semibold.ttf" \
	b47f1eaf02deaf16051a897f84f275326476306eb198f1cbceb5b1f5882021b1
fetch "$gf/notosansgeorgian/OFL.txt" "$work/OFL-NotoSansGeorgian.txt"
fetch "$gf/martianmono/OFL.txt" "$work/OFL-MartianMono.txt"
fetch "$firago/OFL.txt" "$work/OFL-FiraGO.txt"
fetch "$material_license" "$work/Apache-2.0-MaterialSymbols.txt"

instance() {
	local vf=$1 wght=$2 out=$3
	fonttools varLib.instancer "$vf" "wght=$wght" wdth=75 --static --update-name-table -q -o "$out"
}

subset() {
	local in=$1 unicodes=$2 out=$3
	uvx --quiet --from "fonttools==$fonttools_version" pyftsubset "$in" \
		--unicodes="$unicodes" --layout-features='*' --name-IDs='*' --notdef-outline \
		--output-file="$out"
}

instance "$work/noto.ttf" 600 "$work/display-semibold.ttf"
instance "$work/noto.ttf" 800 "$work/display-extrabold.ttf"
instance "$work/martian.ttf" 500 "$work/mono-medium.ttf"
instance "$work/martian.ttf" 700 "$work/mono-bold.ttf"

out="$work/out"
mkdir -p "$out"
subset "$work/display-semibold.ttf" "$text_unicodes" "$out/gza_display_semibold.ttf"
subset "$work/display-extrabold.ttf" "$text_unicodes" "$out/gza_display_extrabold.ttf"
subset "$work/firago-regular.ttf" "$text_unicodes" "$out/gza_body_regular.ttf"
subset "$work/firago-medium.ttf" "$text_unicodes" "$out/gza_body_medium.ttf"
subset "$work/firago-semibold.ttf" "$text_unicodes" "$out/gza_body_semibold.ttf"
subset "$work/mono-medium.ttf" "$mono_unicodes" "$out/gza_mono_medium.ttf"
subset "$work/mono-bold.ttf" "$mono_unicodes" "$out/gza_mono_bold.ttf"

uvx --quiet --from "fonttools==$fonttools_version" python - "$out" <<'PY'
import sys
from pathlib import Path
from fontTools.ttLib import TTFont

out = Path(sys.argv[1])
georgian = range(0x10D0, 0x10FB)
digits = [ord(c) for c in "0123456789"]
cyrillic = range(0x0410, 0x0450)
failures = []

def check(name, needed, what):
    cmap = TTFont(out / name).getBestCmap()
    missing = [hex(c) for c in needed if c not in cmap]
    if missing:
        failures.append(f"{name} misses {what}: {missing[:8]}")
    return cmap

for name in ["gza_display_semibold.ttf", "gza_display_extrabold.ttf"]:
    check(name, georgian, "Georgian")
    check(name, digits, "digits")
for name in ["gza_body_regular.ttf", "gza_body_medium.ttf", "gza_body_semibold.ttf"]:
    check(name, georgian, "Georgian")
    check(name, digits, "digits")
    check(name, cyrillic, "Cyrillic")
for name in ["gza_mono_medium.ttf", "gza_mono_bold.ttf"]:
    cmap = check(name, digits + [ord(":"), ord("±")], "digits, colon or plus-minus")
    font = TTFont(out / name)
    widths = {font["hmtx"][cmap[c]][0] for c in digits if c in cmap}
    if len(widths) != 1:
        failures.append(f"{name} digits are not one width: {sorted(widths)}")

if failures:
    print("\n".join(failures), file=sys.stderr)
    sys.exit(1)
PY

mkdir -p "$font_dir" "$license_dir"
cp "$out"/gza_*.ttf "$font_dir/"
cp "$work"/OFL-*.txt "$work/Apache-2.0-MaterialSymbols.txt" "$license_dir/"
ls -l "$font_dir" "$license_dir"
