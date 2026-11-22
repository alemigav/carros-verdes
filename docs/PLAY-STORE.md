# Publicação na Play Store — Carros Verdes

## 0. Situação atual (22/11/2026)

| Etapa | Situação |
| --- | --- |
| Pacote `1.0.0` (versionCode 1) na faixa de **teste interno** | **enviado e publicado** |
| Ficha da loja: título, descrição curta, descrição completa | **publicados** |
| Ícone, banner e 5 capturas | **publicados** |
| E-mail de contato e site | **pendentes** (falta o e-mail) |
| Declarações de "Conteúdo do app" | **pendentes** — só existem na interface web |
| Política de privacidade no ar | **pendente** — arquivo no repositório, falta publicar |

Tudo o que está marcado como publicado foi feito pela API, com a conta de serviço
`carros-verdes@carros-verdes.iam.gserviceaccount.com`.

## 1. O que está pronto

| Item | Caminho | Situação |
| --- | --- | --- |
| Pacote de envio (`.aab`) | `android/app/build/outputs/bundle/release/app-release.aab` | assinado, 6,0 MB |
| APK para instalar direto | `android/app/build/outputs/apk/release/app-release.apk` | assinado, 4,3 MB |
| Ícone 512×512 | `docs/play/icone-512.png` | pronto |
| Banner 1024×500 | `docs/play/banner-1024x500.png` | pronto |
| Capturas de tela | `docs/play/screenshots/01..05` | 5 imagens em 9:16 (1350×2400) |
| Política de privacidade | `privacidade/index.html` | no repositório; **precisa ser publicado** |
| Ficha da loja e questionários | este documento | pronto |

Integridade dos pacotes (para conferir depois de qualquer cópia):

```bash
shasum -a 256 android/app/build/outputs/bundle/release/app-release.aab
```

O `applicationId` **precisa** continuar `com.carrosverdes.app`. Se mudar, a Play recusa o pacote
com "Package not found" ou erro de assinatura.

## 2. Dados técnicos

| Campo | Valor |
| --- | --- |
| applicationId | `com.carrosverdes.app` (igual ao app já criado na Play Console) |
| versionCode / versionName | `1` / `1.0.0` (publicado na trilha interna) |
| minSdk / targetSdk / compileSdk | 26 (Android 8) / 36 / 36 |
| Permissões | `INTERNET`, `ACCESS_NETWORK_STATE` |
| Bibliotecas de terceiros | nenhuma de rastreamento, anúncio ou análise |
| Tamanho instalado | ~9 MB |

## 3. Assinatura

A chave de upload está em `android/keystore/` (fora do git) e a senha em
`android/keystore.properties`. **Faça uma cópia dos dois fora do computador**: sem eles não é
possível publicar atualizações.

| Campo | Valor |
| --- | --- |
| Alias | `carrosverdes` |
| Algoritmo | RSA 4096, SHA384withRSA |
| SHA-1 | `DC:F4:16:F1:E4:31:BA:E4:93:77:02:61:28:3E:A3:AC:F5:13:62:9B` |
| SHA-256 | `37:13:B1:A3:77:A6:98:67:08:99:62:B8:DC:5B:98:02:3C:F6:D1:0B:6C:70:32:1F:39:99:EE:35:E8:C9:7C:B8` |

Ao ativar o **Play App Signing**, esta chave passa a ser apenas a *upload key*; a chave que assina
o app distribuído fica com o Google.

## 4. Ficha da loja

**Nome do app** (máx. 30) — `Carros Verdes`

**Descrição curta** (máx. 80)
```
Compare carros elétricos e híbridos flex: custo por mês, autonomia e ficha completa.
```

**Descrição completa** (máx. 4.000)

```
Carros Verdes ajuda a escolher um carro elétrico ou híbrido plug-in flex sem precisar entender de
potência, bateria ou corrente alternada.

PRIMEIRO, ENTENDER

Sete lições curtas, em português de gente, explicam o que realmente decide a compra: o que é kWh,
por que a autonomia da vida real é menor que a da etiqueta, como carregar em casa, quanto custa
rodar por mês e o que perguntar na concessionária. Cada lição termina em um botão que já leva aos
carros de que ela fala.

DEPOIS, COMPARAR

A lista traz 92 versões de elétricos e híbridos flex à venda no Brasil, com filtros por preço
(mínimo e máximo), autonomia, bateria, potência de recarga rápida, recarga em casa, potência do
motor, lugares, porta-malas, carroceria, tração, marca e dezenove equipamentos — de piloto
adaptativo a bomba de calor.

A ordenação é sempre um critério escrito: menor preço, maior preço, menor custo por quilômetro,
menor consumo, maior autonomia, maior bateria, recarga mais rápida, mais potência, maior
porta-malas, mais lugares ou maior garantia de bateria.

Dá para marcar de dois a quatro carros e comparar lado a lado, com o melhor valor de cada linha
destacado — e uma opção para ver só as linhas que mudam.

TODAS AS VERSÕES À VISTA

Cada cartão mostra as versões daquele modelo, uma por linha, com o preço e os números de cada uma.
Nada de esconder a versão mais cara atrás de uma média.

OS NÚMEROS NA SUA VIDA

O gasto aparece em reais por mês, comparado ao que você gastaria de gasolina. A recarga é
explicada pelo trabalho que dá: tomada comum, carregador de parede ou planejar a estrada.

CADA NÚMERO TEM ORIGEM

As fichas técnicas citam as fontes de cada carro e a confiança dos dados. Quando o fabricante não
publica um campo, o app escreve uma frase explicando o que falta, em vez de mostrar um traço ou um
zero que pareça informação. Nos híbridos sem consumo de combustível publicado, o custo mensal é
apresentado como estimativa parcial, nunca como economia garantida.

FEITO PARA LER SEM ESFORÇO

Texto de corpo em 20 sp, alvos de toque grandes, contraste alto e leitura em voz alta pelo motor do
próprio celular, sem internet.

O QUE O APP NÃO FAZ

- Não pede cadastro e não tem login.
- Não coleta nenhum dado pessoal.
- Não mostra anúncio.
- Não pede acesso a localização, contatos, câmera ou microfone.
- Funciona sem internet, com a base que vem dentro do app.

A base pode ser atualizada pelo botão "Procurar dados novos", sem passar pela loja.

Uso reservado. © 2026 Alexandre Michel Gavronski.
```

**Categoria** — Auto e veículos
**Tags** — carro elétrico, carro híbrido, comparador, acessibilidade
**E-mail de contato** — *preencher*
**Site** — https://alemigav.github.io/carros-verdes/
**Política de privacidade** — https://alemigav.github.io/carros-verdes/privacidade/

## 5. Segurança de dados

Responda **não coleta nem compartilha dados** em todas as categorias.

- Coleta de dados: **não**
- Compartilhamento: **não**
- Criptografia em trânsito: não se aplica
- Exclusão de dados: não se aplica
- O único tráfego é o download HTTPS do arquivo público de dados

## 6. Classificação de conteúdo

Questionário IARC: sem violência, sexo, linguagem imprópria, drogas, apostas, compras, anúncios,
interação entre usuários ou compartilhamento de localização. Resultado esperado: **Livre**.

## 7. Recursos gráficos

| Recurso | Requisito | Arquivo |
| --- | --- | --- |
| Ícone | 512×512 PNG 32 bits | `docs/play/icone-512.png` |
| Banner | 1024×500 | `docs/play/banner-1024x500.png` |
| Captura 1 | 9:16, ≥320 px | `docs/play/screenshots/01-inicio.png` |
| Captura 2 | 9:16 | `docs/play/screenshots/02-carros.png` |
| Captura 3 | 9:16 | `docs/play/screenshots/03-filtros.png` |
| Captura 4 | 9:16 | `docs/play/screenshots/04-ficha-tecnica.png` |
| Captura 5 | 9:16 | `docs/play/screenshots/05-entender.png` |

As capturas foram ajustadas de 1080×2400 para 1350×2400 pelo limite de proporção da loja (9:16).

## 8. Passo a passo no Play Console

O primeiro envio **precisa** ser feito na interface web: criar o app, aceitar os acordos e ativar
o Play App Signing não têm equivalente na API.

1. Guardar a chave: copiar `android/keystore/` e `android/keystore.properties` para dois lugares
   seguros.
2. Publicar a política de privacidade: commitar `privacidade/index.html` e conferir a URL no
   navegador (GitHub Pages serve a pasta do repositório).
3. No Play Console, criar o app com o nome **Carros Verdes**, idioma padrão **Português (Brasil)**,
   tipo **Aplicativo**, **Gratuito**.
4. Preencher ficha da loja (textos desta seção 4), gráficos (seção 7), classificação (seção 6) e
   segurança de dados (seção 5).
5. Em **Teste interno**, subir o `app-release.aab`, aceitar o Play App Signing e instalar pelo link
   em um aparelho real.
6. Conferir no aparelho: fonte do sistema em 200%, TalkBack, modo avião, botão de atualizar dados.
7. Promover para produção.

## 9. Envio automatizado pela API

Dois scripts fazem o trabalho, sem interface web:

```bash
# nova versão do app (depois de subir o versionCode)
./tools/upload-play.sh --track interno      # internal | beta | alpha | producao
./tools/upload-play.sh --track interno --rascunho   # sobe sem liberar

# ficha da loja: textos e imagens
./tools/update-play-listing.sh
```

- Os textos da ficha ficam em `docs/play/ficha-loja.json` — editar ali e rodar o script de novo.
- As imagens saem de `docs/play/` (ícone, banner) e `docs/play/screenshots/`.
- A credencial é lida de `~/.config/carrosverdes/play-service-account.json` e nunca é impressa.

### O que a API não faz

- Criar o app (o pacote nasce no primeiro envio pela interface).
- Declarações de **Conteúdo do app**: classificação indicativa, segurança de dados, público-alvo,
  anúncios e acesso ao app. Todas são só na interface web.
- Gerenciar a lista de testadores do teste interno.

## 9b. Envio automatizado pela API (opcional)

Com uma **conta de serviço** do Google Cloud com acesso ao Play Developer API — e essa conta
convidada no Play Console em *Configurações → Acesso à API* — o envio do `.aab` pode ser feito por
linha de comando, sem a interface web.

O que a API faz: sobe o pacote e cria a versão em uma faixa (interno, fechado, aberto, produção).
O que ela **não** faz: criar o app, aceitar acordos, ativar o Play App Signing ou preencher a ficha.

**Como entregar a credencial com segurança:** salve o JSON da conta de serviço em

```
~/.config/carrosverdes/play-service-account.json
```

fora do repositório, e me avise apenas que o arquivo está lá. Nunca cole a chave privada em uma
mensagem, e nunca a coloque dentro do projeto — ela dá poder de publicar em seu nome.

## 10. Antes de cada envio futuro

- Subir `versionCode` (e `versionName`) em `android/app/build.gradle.kts`.
- Rodar `./gradlew :app:testDebugUnitTest` — os testes validam o `data/cars.json` real.
- Rodar `./gradlew :app:lintDebug` — precisa continuar em 0 erros.
