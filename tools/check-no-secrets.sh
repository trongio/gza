#!/usr/bin/env bash
# Fails if a secret from the local ttc.properties appears in the repo, in the working
# tree or anywhere in history. Never prints a secret value, only the property name.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

props=ttc.properties
if [[ ! -f $props ]]; then
	echo "ttc.properties not found: run ./tools/fetch-ttc-config.sh first" >&2
	exit 2
fi

leak=0
# gatewayBaseUrl and firebaseProjectId are public and documented in docs/TTC_API.md.
for k in ttc.gatewayKey ttc.firebaseApiKey ttc.firebaseAppId; do
	v=$(sed -n "s/^${k//./\\.}=//p" "$props" | head -1 | tr -d '[:space:]')
	[[ -z $v ]] && continue
	if git grep -q -F -e "$v"; then
		echo "LEAK: $k in tree"
		leak=1
	fi
	hit=$(git log --all -S"$v" --format=%H | head -1 || true)
	if [[ -n $hit ]]; then
		echo "LEAK: $k in history"
		leak=1
	fi
done

# Any Firebase-style API key in tracked files, whatever its value.
if files=$(git grep -l -E 'AIza[0-9A-Za-z_-]{35}'); then
	echo "LEAK: Firebase-style API key in:"
	echo "$files"
	leak=1
fi

if [[ $leak -ne 0 ]]; then
	exit 1
fi
echo "no secrets found"
