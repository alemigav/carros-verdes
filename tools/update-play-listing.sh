#!/bin/bash
# Envia para o Google Play a ficha da loja: título, descrições, ícone, banner e capturas.
# Os textos saem de docs/play/ficha-loja.json; as imagens, de docs/play/.
#
# Uso: ./tools/update-play-listing.sh
set -euo pipefail

PKG="com.carrosverdes.app"
CONTA="$HOME/.config/carrosverdes/play-service-account.json"
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
PY="${MIMO_PYTHON:-python3}"

[ -f "$CONTA" ] || { echo "sem credencial em $CONTA" >&2; exit 1; }
[ -f "$RAIZ/docs/play/ficha-loja.json" ] || { echo "sem docs/play/ficha-loja.json" >&2; exit 1; }

exec "$PY" - "$PKG" "$CONTA" "$RAIZ" <<'PY'
import base64, json, mimetypes, pathlib, subprocess, sys, time
import urllib.error, urllib.parse, urllib.request

pkg, conta, raiz = sys.argv[1], pathlib.Path(sys.argv[2]), pathlib.Path(sys.argv[3])
d = json.loads(conta.read_text())
tmp = pathlib.Path('/tmp'); chave = tmp/'chave-listing.pem'
chave.write_text(d['private_key']); chave.chmod(0o600)

b64 = lambda b: base64.urlsafe_b64encode(b).rstrip(b'=').decode()
agora = int(time.time())
entrada = b64(json.dumps({'alg': 'RS256', 'typ': 'JWT'}).encode()) + '.' + b64(json.dumps({
    'iss': d['client_email'],
    'scope': 'https://www.googleapis.com/auth/androidpublisher',
    'aud': 'https://oauth2.googleapis.com/token',
    'iat': agora, 'exp': agora + 3600}).encode())
(tmp/'entrada-listing.txt').write_text(entrada)
subprocess.run(['openssl', 'dgst', '-sha256', '-sign', str(chave), '-out', str(tmp/'assinatura-listing.bin'),
                str(tmp/'entrada-listing.txt')], check=True)
jwt = entrada + '.' + b64((tmp/'assinatura-listing.bin').read_bytes())
chave.unlink()

dados = urllib.parse.urlencode({
    'grant_type': 'urn:ietf:params:oauth:grant-type:jwt-bearer', 'assertion': jwt}).encode()
token = json.loads(urllib.request.urlopen('https://oauth2.googleapis.com/token', dados).read())['access_token']

base = f'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{pkg}'

def json_api(metodo, url, corpo=None):
    dados = json.dumps(corpo).encode() if corpo is not None else None
    req = urllib.request.Request(url, data=dados, method=metodo,
        headers={'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'})
    try:
        return json.loads(urllib.request.urlopen(req).read() or b'{}')
    except urllib.error.HTTPError as e:
        raise SystemExit(f'{metodo} {url.split("/v3/")[-1]} → HTTP {e.code}: {e.read().decode()[:300]}')

def enviar_imagem(edit, idioma, tipo, caminho):
    # imagens sobem pelo host de upload, igual ao pacote .aab
    destino = ('https://androidpublisher.googleapis.com/upload/androidpublisher/v3/'
               f'applications/{pkg}/edits/{edit}/listings/{idioma}/{tipo}?uploadType=media')
    mime = mimetypes.guess_type(str(caminho))[0] or 'image/png'
    req = urllib.request.Request(destino, data=caminho.read_bytes(), method='POST',
        headers={'Authorization': f'Bearer {token}', 'Content-Type': mime})
    try:
        resposta = json.loads(urllib.request.urlopen(req).read() or b'{}')
        print(f'  {tipo:18} {caminho.name} enviado ({resposta.get("id","ok")})')
    except urllib.error.HTTPError as e:
        print(f'  {tipo:18} {caminho.name} FALHOU: HTTP {e.code} {e.read().decode()[:200]}')

ficha = json.loads((raiz/'docs/play/ficha-loja.json').read_text())
idioma = ficha['idioma']

edicao = json_api('POST', f'{base}/edits', {})
eid = edicao['id']
print('edição', eid)

atual = json_api('GET', f'{base}/edits/{eid}/listings/{idioma}')
json_api('PUT', f'{base}/edits/{eid}/listings/{idioma}', {
    'language': idioma,
    'title': ficha['titulo'][:30],
    'shortDescription': ficha['descricao_curta'][:80],
    'fullDescription': ficha['descricao_completa'][:4000],
})
print(f"  textos  título {len(ficha['titulo'])} / curta {len(ficha['descricao_curta'])} / "
      f"completa {len(ficha['descricao_completa'])} caracteres")

pasta = raiz/'docs/play'
enviar_imagem(eid, idioma, 'icon', pasta/'icone-512.png')
enviar_imagem(eid, idioma, 'featureGraphic', pasta/'banner-1024x500.png')

# capturas: a Play aceita até 8; sempre reenviamos todas para manter a ordem
json_api('DELETE', f'{base}/edits/{eid}/listings/{idioma}/phoneScreenshots')
for imagem in sorted((pasta/'screenshots').glob('*.png')):
    enviar_imagem(eid, idioma, 'phoneScreenshots', imagem)

json_api('POST', f'{base}/edits/{eid}:commit')
print('ficha publicada')
PY
