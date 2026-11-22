#!/bin/bash
# Sobe o pacote .aab para o Google Play usando a conta de serviço.
#
# Uso:
#   ./tools/upload-play.sh [--track interno|fechado|aberto|producao] [--rascunho] [caminho.aab]
#
# A credencial fica FORA do repositório, em ~/.config/carrosverdes/play-service-account.json
# e nunca é impressa por este script.
set -euo pipefail

PKG="com.carrosverdes.app"
CONTA="$HOME/.config/carrosverdes/play-service-account.json"
TRILHA="internal"
STATUS="completed"
AAB=""

while [ $# -gt 0 ]; do
  case "$1" in
    --track) TRILHA="$2"; shift 2 ;;
    --rascunho) STATUS="draft"; shift ;;
    *) AAB="$1"; shift ;;
  esac
done

case "$TRILHA" in
  interno) TRILHA="internal" ;;
  fechado) TRILHA="beta" ;;
  aberto) TRILHA="alpha" ;;
  producao) TRILHA="production" ;;
esac

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
[ -z "$AAB" ] && AAB="$RAIZ/android/app/build/outputs/bundle/release/app-release.aab"

if [ ! -f "$AAB" ]; then echo "não achei o pacote: $AAB" >&2; exit 1; fi
if [ ! -f "$CONTA" ]; then echo "não achei a credencial em $CONTA" >&2; exit 1; fi

PY="${MIMO_PYTHON:-python3}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# --- 1. token de acesso a partir da conta de serviço ---
"$PY" - "$CONTA" "$TMP" <<'PY'
import base64, json, pathlib, sys, time
conta, tmp = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
d = json.loads(conta.read_text())
chave = tmp/'chave.pem'
chave.write_text(d['private_key']); chave.chmod(0o600)
b64 = lambda b: base64.urlsafe_b64encode(b).rstrip(b'=').decode()
agora = int(time.time())
entrada = b64(json.dumps({'alg':'RS256','typ':'JWT'}).encode()) + '.' + b64(json.dumps({
    'iss': d['client_email'],
    'scope': 'https://www.googleapis.com/auth/androidpublisher',
    'aud': 'https://oauth2.googleapis.com/token',
    'iat': agora, 'exp': agora + 3600,
}).encode())
(tmp/'entrada.txt').write_text(entrada)
PY
openssl dgst -sha256 -sign "$TMP/chave.pem" -out "$TMP/assinatura.bin" "$TMP/entrada.txt"
JWT="$(cat "$TMP/entrada.txt").$("$PY" -c "import base64,sys;print(base64.urlsafe_b64encode(open(sys.argv[1],'rb').read()).rstrip(b'=').decode())" "$TMP/assinatura.bin")"

TOKEN=$(curl -s -X POST https://oauth2.googleapis.com/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" \
  --data-urlencode "assertion=$JWT" \
  | "$PY" -c "import sys,json;d=json.load(sys.stdin);print(d.get('access_token',''))")

if [ -z "$TOKEN" ]; then echo "não consegui o token — confira se a conta de serviço está ativa" >&2; exit 1; fi

VERSAO=$(grep -o 'versionCode = [0-9]*' "$RAIZ/android/app/build.gradle.kts" | head -1 | grep -o '[0-9]*')
echo "pacote : $AAB"
echo "versão : $VERSAO   trilha: $TRILHA   status: $STATUS"

# --- 2. edição, envio do pacote, faixa e confirmação ---
EDICAO=$(curl -s -X POST "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PKG/edits" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{}' \
  | "$PY" -c "
import sys,json
d=json.load(sys.stdin)
if 'error' in d:
    print('', end='')
    sys.stderr.write('erro ao abrir edição: ' + d['error'].get('message','') + '\n')
else:
    print(d['id'])
")
if [ -z "$EDICAO" ]; then echo "o Play recusou abrir uma edição — veja a mensagem acima" >&2; exit 1; fi

echo "edição : $EDICAO"
echo "enviando o pacote (pode levar alguns minutos)…"
curl -s -X POST \
  "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/$PKG/edits/$EDICAO/bundles?uploadType=media" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/octet-stream" \
  --data-binary "@$AAB" \
  | "$PY" -c "
import sys,json
d=json.load(sys.stdin)
if 'error' in d:
    sys.stderr.write('erro no envio: ' + d['error'].get('message','') + '\n')
else:
    print('pacote aceito, versionCode', d.get('versionCode'))
"

curl -s -X PUT "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PKG/edits/$EDICAO/tracks/$TRILHA" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"releases\":[{\"versionCodes\":[\"$VERSAO\"],\"status\":\"$STATUS\"}]}" \
  | "$PY" -c "
import sys,json
d=json.load(sys.stdin)
if 'error' in d:
    sys.stderr.write('erro na faixa: ' + d['error'].get('message','') + '\n')
else:
    print('faixa', d.get('track'), 'com', len(d.get('releases',[])), 'versão(ões)')
"

curl -s -X POST "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PKG/edits/$EDICAO:commit" \
  -H "Authorization: Bearer $TOKEN" \
  | "$PY" -c "
import sys,json
d=json.load(sys.stdin)
if 'error' in d:
    sys.stderr.write('erro ao confirmar: ' + d['error'].get('message','') + '\n')
else:
    print('publicado. edição', d.get('id'))
"
