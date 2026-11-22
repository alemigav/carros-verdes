# App Android — Carros Verdes

App nativo em Kotlin + Jetpack Compose, **senior-first**, com duas portas: decisão guiada e consulta
técnica. O plano de produto e as decisões de design estão em
[`docs/PLANO-APP-ANDROID.md`](../docs/PLANO-APP-ANDROID.md). A publicação está em
[`docs/PLAY-STORE.md`](../docs/PLAY-STORE.md).

## Regra que sustenta o projeto

**O app não contém nenhum fato sobre carro.** Todo dado de veículo vem de `data/cars.json`, o mesmo
arquivo que o site usa. Se você escrever "BYD Dolphin Mini custa X" dentro do Kotlin, a manutenção
já começou a apodrecer.

## Compilar

O JDK do sistema (Homebrew, versão 25) quebra o plugin do Kotlin, que não interpreta o formato de
versão `25.0.5.0.3`. Por isso há um JDK 21 só para o projeto, em `android/.jdk/` (fora do git).

```bash
cd android
export JAVA_HOME="$PWD/.jdk/jdk-21.0.13.1+2/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"

./gradlew :app:assembleDebug        # APK de teste
./gradlew :app:assembleRelease      # APK assinado
./gradlew :app:bundleRelease        # .aab para a Play Store
./gradlew :app:testDebugUnitTest    # testes contra o cars.json real
./gradlew :app:lintDebug            # regras de acessibilidade e API
```

Se o `.jdk/` não existir em outra máquina, baixe o Temurin 21 e ajuste o caminho:

```bash
curl -fL -o /tmp/temurin21.tar.gz \
  "https://api.adoptium.net/v3/binary/latest/21/ga/mac/aarch64/jdk/hotspot/normal/eclipse"
mkdir -p .jdk && tar -xzf /tmp/temurin21.tar.gz -C .jdk
```

## Estrutura

```
app/src/main/assets/cars.json      cópia da base (gerada a partir de data/cars.json)
app/src/main/assets/photos.json    mapa "Marca Modelo" → arquivo, autor, licença
app/src/main/assets/photos/*.webp  73 fotos reduzidas do Wikimedia Commons
app/src/main/java/br/com/carrosverdes/
  data/Car.kt                      leitura tolerante do JSON, campo ausente vira null
  data/CarsRepository.kt           base embutida, cache baixado, atualização atômica
  data/Fotos.kt                    fotos e créditos
  domain/Premissas.kt              km/ano, preços de energia e gasolina, manutenção
  domain/Traducao.kt               número → frase em português de gente
  domain/Ranking.kt                escolha dos carros por perfil de uso
  domain/CampoCatalogo.kt          catálogo dos campos da ficha técnica
  ui/*                             telas, tema e leitura em voz alta
app/src/test/java/.../BaseRealTest.kt  18 testes rodando sobre o data/cars.json real
```

## Atualizar dados — três níveis de esforço

**1. Carro novo, preço novo, versão nova (o caso comum).**
Nada de código. Rode o pipeline normal do site e copie o arquivo:

```bash
cd ..                      # raiz do repositório
python3 data/merge.py      # ou build_base.py + merge.py + add_versions.py
cp data/cars.json android/app/src/main/assets/cars.json
cd android && ./gradlew :app:testDebugUnitTest
```

Os testes validam a base nova antes de qualquer build. Depois, para quem já tem o app instalado,
basta o commit do `data/cars.json`: o app baixa a base nova pelo botão **Procurar dados novos**,
sem passar pela loja.

**2. Campo novo na base (ex.: `frunk_l`, `modo_senta_e_levanta`).**
O dado aparece sozinho na ficha, dentro do bloco "Outros dados da base", com o nome do campo
legível. Para ele ganhar rótulo próprio, unidade e explicação, acrescente **uma linha** em
`domain/CampoCatalogo.kt`:

```kotlin
Campo("frunk_l", "Porta-malas dianteiro", Grupo.DIMENSOES, Tipo.INTEIRO, "L",
      explicacao = "Espaço extra na frente, onde ficaria o motor."),
```

**3. Regra de negócio nova (novo critério de escolha, nova premissa de custo).**
Aí sim é código: `Premissas.kt` para números, `Traducao.kt` para texto, `Ranking.kt` para pesos.
Cada função pura dessas é coberta por teste.

## Adicionar uma foto

1. Coloque o arquivo em `v3/photos/` e registre em `v3/photos/credits.json` (autor, licença, origem).
2. Regenere os assets:

```bash
python3 - <<'PY'
from PIL import Image
import json, pathlib
raiz = pathlib.Path('.')
origem = json.loads((raiz/'v3/photos/credits.json').read_text())
destino = raiz/'android/app/src/main/assets/photos'
mapa = {}
for chave, info in origem.items():
    caminho = raiz/'v3'/info['arquivo']
    if not caminho.exists():
        continue
    saida = destino/(caminho.stem + '.webp')
    with Image.open(caminho) as img:
        img = img.convert('RGB')
        if img.width > 720:
            img = img.resize((720, round(img.height*720/img.width)), Image.LANCZOS)
        img.save(saida, 'WEBP', quality=72, method=6)
    creditos = {'arquivo': f'photos/{saida.name}', 'autor': info.get('autor',''),
                'licenca': info.get('licenca',''), 'origem': info.get('origem','')}
    for modelo in info.get('modelos', []):
        mapa[modelo] = creditos
(raiz/'android/app/src/main/assets/photos.json').write_text(
    json.dumps(mapa, ensure_ascii=False, indent=1))
PY
```

Sem foto licenciada o app não inventa imagem: mostra o nome do carro em letra grande.

## Assinatura

`android/keystore.properties` (fora do git) aponta para `android/keystore/carros-verdes-release.jks`.
Sem esse arquivo o build de release roda sem assinatura — útil para conferir, inútil para publicar.
**Guarde uma cópia dos dois fora do computador**: perdê-los impede atualizar o app na loja.

## Regras de interface que não se negociam

Estão no plano, mas ficam repetidas aqui porque é fácil quebrá-las sem perceber:

- texto de corpo nunca abaixo de 18 sp, corpo principal 20 sp;
- alvo de toque nunca abaixo de 64 dp;
- nenhuma rolagem horizontal, nenhum gesto sem botão equivalente, nenhum tempo limite;
- campo ausente vira **frase**, nunca traço ou zero;
- todo resultado tem botão de ouvir;
- nada de cadastro, login, anúncio ou rastreador.

O teste `valores ausentes viram frase e nao traco` existe justamente para impedir a regressão mais
provável nessa área.
