#!/usr/bin/env bash
set -euo pipefail

# Release-Skript für "Mein Thunderbird" (eigene Beta, eigener Signierschlüssel).
#
# Ablauf: prüfen -> Tag bestimmen -> bauen -> Signatur prüfen -> nach release-preview
# kopieren -> Zusammenfassung -> nach Rückfrage Tag pushen und GitHub-Release anlegen.
#
# Usage:
#   scripts/mein-thunderbird-release.sh [--dry-run] <notes-file>
#
# Erwartet: Branch "mein-thunderbird", sauberer Arbeitsordner, Signiereinstellungen
# unter .signing/ und ein GitHub-Login für das Konto JustNotesa.

REPO="JustNotesa/thunderbird-android"
REMOTE="fork"
BRANCH="mein-thunderbird"
GH_ACCOUNT="JustNotesa"
SIGNING_PROPERTIES=".signing/tb.beta.signing.properties"
NIEDRIG_TOOL="$HOME/.claude/tools/niedrig.sh"
ANDROID_HOME_DEFAULT="$HOME/Library/Android/sdk"
APK_RELATIVE_PATH="app-thunderbird/build/outputs/apk/foss/beta/app-thunderbird-foss-beta.apk"
GRADLE_TASK=":app-thunderbird:assembleFossBeta"
APKSIGNER=""

die() {
  echo "Fehler: $*" >&2
  exit 1
}

info() {
  echo "$*"
}

usage() {
  cat <<'EOF'
Usage: scripts/mein-thunderbird-release.sh [--dry-run] <notes-file>

Baut die eigene Thunderbird-Beta (Branch mein-thunderbird), prüft die Signatur,
legt die APK unter release-preview ab und veröffentlicht sie als GitHub-Release.

Optionen:
  --dry-run      Bauen und prüfen, aber nichts hochladen.
  -h, --help     Diese Hilfe anzeigen.

Argumente:
  <notes-file>   Textdatei mit den Release-Notizen; muss existieren und nicht leer sein.

Umgebung:
  ANDROID_HOME   Android-SDK; Standard ist ~/Library/Android/sdk
EOF
}

die_usage() {
  echo "Fehler: $*" >&2
  usage >&2
  exit 2
}

# Tags der GitHub-Releases; schlägt das Lesen fehl, liefert die Funktion 1 zurück.
collect_release_tags() {
  GH_TOKEN="$GH_TOKEN_JUSTNOTESA" gh release list -R "$REPO" --limit 200 \
    --json tagName -q '.[].tagName'
}

# Tags des Forks und lokal, ohne "refs/tags/" und ohne Peeled-Suffix "^{}".
# Schlägt das Lesen fehl, liefert die Funktion 1 zurück.
collect_git_tags() {
  local remote_tags=""
  local local_tags=""
  remote_tags="$(git ls-remote --tags "$REMOTE" 'refs/tags/mein-thunderbird-*' |
    awk '{print $2}' | sed -e 's#^refs/tags/##' -e 's#\^{}$##')" || return 1
  local_tags="$(git tag -l 'mein-thunderbird-*')" || return 1
  printf '%s\n%s\n' "$remote_tags" "$local_tags"
}

# Neuestes apksigner im Android-SDK; APKSIGNER bleibt leer, wenn keines da ist.
find_apksigner() {
  local dir=""
  local candidate=""
  while IFS= read -r dir; do
    if [[ -x "$dir/apksigner" ]]; then
      candidate="$dir/apksigner"
    fi
  done < <(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V)
  APKSIGNER="$candidate"
}

DRY_RUN=0
NOTES=""
ORIGINAL_DIR="${PWD}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      break
      ;;
    -*)
      die_usage "Unbekannte Option: $1"
      ;;
    *)
      if [[ -n "$NOTES" ]]; then
        die_usage "Es wird nur eine Notizen-Datei erwartet (bekam: $1)."
      fi
      NOTES="$1"
      shift
      ;;
  esac
done

if [[ $# -gt 0 ]]; then
  if [[ -n "$NOTES" ]]; then
    die_usage "Es wird nur eine Notizen-Datei erwartet (bekam: $1)."
  fi
  NOTES="$1"
  shift
fi

if [[ -z "$NOTES" ]]; then
  die_usage "Es fehlt die Notizen-Datei."
fi

if [[ "$NOTES" != /* ]]; then
  NOTES="${ORIGINAL_DIR}/${NOTES}"
fi

if [[ ! -f "$NOTES" ]]; then
  die_usage "Die Notizen-Datei existiert nicht: $NOTES"
fi

if [[ ! -s "$NOTES" ]]; then
  die_usage "Die Notizen-Datei ist leer: $NOTES"
fi

# Ab hier immer im Repo-Root arbeiten.
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [[ -z "$REPO_ROOT" ]]; then
  die "Kein Git-Repository gefunden."
fi
cd "$REPO_ROOT" || die "Wechsel in das Repository nicht möglich: $REPO_ROOT"

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)" || die "Der aktuelle Branch konnte nicht ermittelt werden."
if [[ "$CURRENT_BRANCH" != "$BRANCH" ]]; then
  die "Falscher Branch: erwartet wird '$BRANCH', aktuell ist '$CURRENT_BRANCH'."
fi

DIRTY="$(git status --porcelain)" || die "Der Zustand des Arbeitsordners konnte nicht ermittelt werden."
if [[ -n "$DIRTY" ]]; then
  die "Der Arbeitsordner ist nicht sauber. Bitte erst committen oder die Änderungen weglegen:
$DIRTY"
fi

if [[ ! -f "$SIGNING_PROPERTIES" ]]; then
  die "Die Signiereinstellungen fehlen: $REPO_ROOT/$SIGNING_PROPERTIES"
fi

if ! command -v git >/dev/null 2>&1; then
  die "git wird nicht gefunden."
fi

if ! command -v gh >/dev/null 2>&1; then
  die "gh (GitHub CLI) wird nicht gefunden."
fi

# Token einmalig holen, damit gh nicht still auf das global aktive Konto zurückfällt.
if ! GH_TOKEN_JUSTNOTESA="$(gh auth token -u "$GH_ACCOUNT")"; then
  die "Das GitHub-Token für das Konto $GH_ACCOUNT konnte nicht gelesen werden."
fi
if [[ -z "$GH_TOKEN_JUSTNOTESA" ]]; then
  die "Das GitHub-Token für das Konto $GH_ACCOUNT ist leer."
fi

export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_HOME_DEFAULT}"
if [[ ! -d "$ANDROID_HOME" ]]; then
  die "Das Android-SDK fehlt: $ANDROID_HOME (ANDROID_HOME setzen oder Standard ~/Library/Android/sdk nutzen)."
fi

if [[ ! -x "$NIEDRIG_TOOL" ]]; then
  die "Das Hilfsskript fehlt oder ist nicht ausführbar: $NIEDRIG_TOOL"
fi

# Tag bestimmen: heutiges Datum, bei vorhandenen Tags eine Nummer dahinter.
DATE="$(date +%Y-%m-%d)" || die "Das Datum konnte nicht ermittelt werden."
BASE="mein-thunderbird-${DATE}"
RELEASE_TAGS="$(collect_release_tags)" || die "Die GitHub-Releases konnten nicht gelesen werden."
GIT_TAGS="$(collect_git_tags)" || die "Die Tags im Repository konnten nicht gelesen werden."
MAX_SUFFIX=0
while IFS= read -r CANDIDATE; do
  if [[ -z "$CANDIDATE" ]]; then
    continue
  fi
  if [[ "$CANDIDATE" == "$BASE" ]]; then
    if [[ "$MAX_SUFFIX" -lt 1 ]]; then
      MAX_SUFFIX=1
    fi
  elif [[ "$CANDIDATE" == "$BASE-"* ]]; then
    SUFFIX="${CANDIDATE#"$BASE-"}"
    if [[ "$SUFFIX" =~ ^[0-9]+$ ]] && [[ "$SUFFIX" -gt "$MAX_SUFFIX" ]]; then
      MAX_SUFFIX="$SUFFIX"
    fi
  fi
done < <(printf '%s\n' "$RELEASE_TAGS" "$GIT_TAGS")

if [[ "$MAX_SUFFIX" -eq 0 ]]; then
  TAG="$BASE"
  N=1
else
  N=$((MAX_SUFFIX + 1))
  TAG="${BASE}-${N}"
fi

if [[ "$N" -eq 1 ]]; then
  TITLE="Mein Thunderbird $DATE"
else
  TITLE="Mein Thunderbird $DATE ($N)"
fi

find_apksigner
if [[ -z "$APKSIGNER" ]]; then
  die "In $ANDROID_HOME/build-tools wurde kein apksigner gefunden."
fi

APK="${REPO_ROOT}/${APK_RELATIVE_PATH}"
SHA="$(git rev-parse HEAD 2>/dev/null || true)"
if [[ -z "$SHA" ]]; then
  die "Der Commit-Hash konnte nicht ermittelt werden."
fi

info "Tag:          $TAG"
info "Titel:        $TITLE"
info "apksigner:    $APKSIGNER"
info "Baue die Beta ($GRADLE_TASK) ..."

# Alte APK entfernen, damit nach dem Build garantiert das neue Stück geprüft wird.
rm -f "$APK"
# 3 GB Heap: der Mac hat 18 GB, ein Build mit dem Projektstandard (10 GB) treibt den Swap ans Limit.
# stdin von /dev/null, damit Gradle nicht die Eingabe für die Rückfrage weiter unten verbraucht.
GRADLE_JVM="-Dfile.encoding=UTF-8 -XX:+UseG1GC -Xmx3g"
"$NIEDRIG_TOOL" ./gradlew "$GRADLE_TASK" -Ptb.useUploadKey=false "-Pmein.releaseTag=$TAG" --no-daemon \
  "-Dorg.gradle.jvmargs=$GRADLE_JVM" "-Pkotlin.daemon.jvmargs=$GRADLE_JVM" < /dev/null

if [[ ! -f "$APK" ]]; then
  die "Die APK wurde nicht erzeugt: $APK"
fi

info ""
info "Signatur der APK:"
if ! "$APKSIGNER" verify --print-certs "$APK"; then
  die "Die Signatur der APK konnte nicht geprüft werden."
fi

# APK zusätzlich außerhalb des Repos ablegen (Vorschau vor dem Hochladen).
PREVIEW_ROOT="$(cd "${REPO_ROOT}/../.." && pwd)" || die "Der Pfad ${REPO_ROOT}/../.. ist nicht lesbar."
PREVIEW_DIR="${PREVIEW_ROOT}/release-preview"
mkdir -p "$PREVIEW_DIR"
APK_COPY="${PREVIEW_DIR}/${TAG}.apk"
cp "$APK" "$APK_COPY"

info ""
info "Größe:        $(wc -c < "$APK_COPY" | tr -d '[:space:]') Bytes"
info "SHA-256:      $(shasum -a 256 "$APK_COPY" | awk '{print $1}')"

info ""
info "=== Zusammenfassung ==="
info "Tag:          $TAG"
info "Titel:        $TITLE"
info "Commit:       $SHA"
info "APK:          $APK_COPY"
info ""
info "=== Notizen ==="
cat "$NOTES"

if [[ "$DRY_RUN" -eq 1 ]]; then
  info ""
  info "Trockenlauf: nichts wurde hochgeladen."
  exit 0
fi

# Der Tag darf weder als Release noch als Tag im Remote existieren, sonst würde
# die APK an einem fremden Release hängen bleiben.
info ""
info "Prüfe, ob $TAG bereits vergeben ist ..."
if GH_TOKEN="$GH_TOKEN_JUSTNOTESA" gh release view "$TAG" -R "$REPO" >/dev/null 2>&1; then
  die "Für $TAG existiert bereits ein GitHub-Release."
fi
EXISTING_REMOTE_TAG=""
if ! EXISTING_REMOTE_TAG="$(git ls-remote --tags "$REMOTE" "refs/tags/$TAG")"; then
  die "Die Tags im Remote $REMOTE konnten nicht gelesen werden."
fi
if [[ -n "$EXISTING_REMOTE_TAG" ]]; then
  die "Der Tag $TAG existiert bereits im Remote $REMOTE."
fi

info ""
if ! read -r -p "Veröffentlichen? [y/N] " ANSWER; then
  die "Abgebrochen: Es wurde nichts veröffentlicht."
fi

case "$ANSWER" in
  y|Y|j|J) ;;
  *) die "Abgebrochen: Es wurde nichts veröffentlicht." ;;
esac

info ""
info "Schiebe den Branch $BRANCH nach $REMOTE ..."
git push "$REMOTE" "$BRANCH"

info "Lege das GitHub-Release $TAG an ..."
GH_TOKEN="$GH_TOKEN_JUSTNOTESA" gh release create "$TAG" "${APK_COPY}#${TAG}.apk" \
  -R "$REPO" --target "$SHA" --title "$TITLE" --notes-file "$NOTES" --latest

info ""
info "Release: https://github.com/${REPO}/releases/tag/${TAG}"
