#!/usr/bin/env bash
# Carica un AAB su Google Play con l'API ufficiale (Google Play Developer API v3),
# senza azioni o librerie di terzi: solo curl, openssl e jq.
#
# Uso: play-upload.sh <file.aab> <canale> [nome-versione]
#   canale: internal (test interno) | alpha (test chiuso) | production
# Variabili: PLAY_ACCESS_TOKEN (token temporaneo dalla federazione delle identità, consigliato)
#            oppure PLAY_SERVICE_ACCOUNT_JSON (chiave JSON dell'account di servizio);
#            PACKAGE_NAME (es. it.citiverse.app), RELEASE_NOTES_FILE (facoltativa)
set -euo pipefail

AAB="$1"
TRACK="$2"
NAME="${3:-}"
PKG="${PACKAGE_NAME:?manca PACKAGE_NAME}"
API="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PKG"
UPLOAD="https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/$PKG"

b64url() { openssl base64 -e -A | tr '+/' '-_' | tr -d '='; }

# 1. Token di accesso.
if [ -n "${PLAY_ACCESS_TOKEN:-}" ]; then
  # Federazione delle identità (senza chiavi): token già ottenuto da google-github-actions/auth, dura un'ora.
  TOKEN="$PLAY_ACCESS_TOKEN"
else
  # Chiave JSON: JWT firmato con la chiave dell'account di servizio (RFC 7523).
  KEY_FILE="$(mktemp)"
  trap 'rm -f "$KEY_FILE"' EXIT
  chmod 600 "$KEY_FILE"
  jq -r '.private_key' <<<"${PLAY_SERVICE_ACCOUNT_JSON:?manca PLAY_ACCESS_TOKEN o PLAY_SERVICE_ACCOUNT_JSON}" >"$KEY_FILE"
  EMAIL="$(jq -r '.client_email' <<<"$PLAY_SERVICE_ACCOUNT_JSON")"
  NOW="$(date +%s)"
  HEADER="$(printf '%s' '{"alg":"RS256","typ":"JWT"}' | b64url)"
  CLAIMS="$(jq -nc --arg iss "$EMAIL" --argjson iat "$NOW" --argjson exp "$((NOW + 3000))" \
    '{iss:$iss, scope:"https://www.googleapis.com/auth/androidpublisher",
      aud:"https://oauth2.googleapis.com/token", iat:$iat, exp:$exp}' | b64url)"
  SIG="$(printf '%s' "$HEADER.$CLAIMS" | openssl dgst -sha256 -sign "$KEY_FILE" | b64url)"
  TOKEN="$(curl -sS --fail-with-body https://oauth2.googleapis.com/token \
    -d grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer \
    -d assertion="$HEADER.$CLAIMS.$SIG" | jq -r '.access_token')"
fi
echo "::add-mask::$TOKEN"
AUTH=(-H "Authorization: Bearer $TOKEN")

# 2. Nuova "modifica" (edit): è una bozza che diventa effettiva solo al commit finale.
EDIT="$(curl -sS --fail-with-body -X POST "${AUTH[@]}" "$API/edits" | jq -r '.id')"
echo "Modifica aperta: $EDIT"

# 3. Caricamento dell'AAB.
CODE="$(curl -sS --fail-with-body -X POST "${AUTH[@]}" \
  -H "Content-Type: application/octet-stream" --data-binary @"$AAB" \
  "$UPLOAD/edits/$EDIT/bundles?uploadType=media" | jq -r '.versionCode')"
echo "Caricato AAB, versionCode $CODE"

# 4. Assegnazione al canale, con le note di rilascio in italiano (max 500 caratteri).
NOTES=""
if [ -n "${RELEASE_NOTES_FILE:-}" ] && [ -f "$RELEASE_NOTES_FILE" ]; then
  NOTES="$(head -c 500 "$RELEASE_NOTES_FILE")"
fi
BODY="$(jq -nc --arg track "$TRACK" --arg name "$NAME" --arg code "$CODE" --arg notes "$NOTES" '
  {track:$track, releases:[
    {versionCodes:[$code], status:"completed"}
    + (if $name != "" then {name:$name} else {} end)
    + (if $notes != "" then {releaseNotes:[{language:"it-IT", text:$notes}]} else {} end)
  ]}')"
curl -sS --fail-with-body -X PUT "${AUTH[@]}" -H "Content-Type: application/json" \
  --data "$BODY" "$API/edits/$EDIT/tracks/$TRACK" >/dev/null
echo "Assegnato al canale: $TRACK"

# 5. Commit: da qui la versione è pubblicata sul canale (per la produzione, dopo la revisione di Google).
curl -sS --fail-with-body -X POST "${AUTH[@]}" "$API/edits/$EDIT:commit" >/dev/null
echo "Pubblicato: versionCode $CODE sul canale $TRACK"
