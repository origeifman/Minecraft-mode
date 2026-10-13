#!/usr/bin/env bash
# One-shot installer for Linux/macOS. Run from the project folder:  bash install.sh
set -euo pipefail
case "$(uname)" in
  Darwin) MC="$HOME/Library/Application Support/minecraft" ;;
  *)      MC="$HOME/.minecraft" ;;
esac
MODS="$MC/mods"; TMP="$(mktemp -d)"; mkdir -p "$MODS"
step() { printf '\n==> %s\n' "$1"; }

step "Checks"
command -v java >/dev/null || { echo "Install Java 17+ first (https://adoptium.net)"; exit 1; }
command -v curl >/dev/null || { echo "curl is required"; exit 1; }
command -v python3 >/dev/null || { echo "python3 is required (used to read JSON)"; exit 1; }
command -v ollama >/dev/null || { echo "Installing Ollama..."; curl -fsSL https://ollama.com/install.sh | sh; }

step "Fabric Loader for 1.20.1"
INST=$(curl -fsSL https://meta.fabricmc.net/v2/versions/installer | python3 -c 'import sys,json;print(next(i["url"] for i in json.load(sys.stdin) if i["stable"]))')
curl -fsSL "$INST" -o "$TMP/fabric-installer.jar"
java -jar "$TMP/fabric-installer.jar" client -mcversion 1.20.1 -dir "$MC"

modrinth() {
  local url
  url=$(curl -fsSL "https://api.modrinth.com/v2/project/$1/version?game_versions=%5B%221.20.1%22%5D&loaders=%5B%22fabric%22%5D" \
    | python3 -c 'import sys,json;f=[x for x in json.load(sys.stdin)[0]["files"] if x["primary"]][0];print(f["url"]+"|"+f["filename"])') \
    && curl -fsSL "${url%%|*}" -o "$MODS/${url##*|}" && echo "  installed ${url##*|}" \
    || echo "  could not install $1 automatically"
}
step "Fabric API, MaLiLib, Litematica"
for m in fabric-api malilib litematica; do modrinth "$m"; done

step "Baritone"
URL=$(curl -fsSL "https://api.github.com/repos/cabaletta/baritone/releases?per_page=30" | python3 -c '
import sys,json
for r in json.load(sys.stdin):
    if r["tag_name"].startswith("v1.10."):
        for a in r["assets"]:
            if a["name"].startswith("baritone-standalone-fabric") and "sources" not in a["name"]:
                print(a["browser_download_url"]); sys.exit()' || true)
if [ -n "${URL:-}" ]; then curl -fsSL "$URL" -o "$MODS/$(basename "$URL")" && echo "  installed $(basename "$URL")"
else echo "  could not find Baritone automatically: get the Fabric 1.20.1 jar from https://github.com/cabaletta/baritone/releases into $MODS"; fi

step "Building the Companion mod"
chmod +x gradlew && ./gradlew build
JAR=$(ls build/libs/autonomous-companion-*.jar | grep -v sources | head -1)
cp "$JAR" "$MODS/" && echo "  installed $(basename "$JAR")"

step "AI model (about 5 GB)"
(ollama serve >/dev/null 2>&1 &) ; sleep 5
ollama pull llama3.1:8b
printf '\nDone. Start Minecraft with the fabric-loader-1.20.1 profile, join a world and type  !help\n'
