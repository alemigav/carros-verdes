# Plano — app Android Carros Verdes

**Escopo deste documento:** definir o produto e a arquitetura do app Android antes de escrever
código. Duas decisões já fechadas: **Kotlin + Compose nativo** e **dados embutidos com
atualização remota**.

**Reformulação do objetivo (definida na conversa):** o app não é o site empacotado. É um app
mobile **senior-first** para escolher carro eletrificado — e que, ao mesmo tempo, deixa qualquer
pessoa consultar a ficha técnica de qualquer carro, sem passar pelo questionário.

---

## 1. O problema real

Quem está comprando o primeiro carro eletrificado aos 60+ anos não trava por falta de dados.
Trava por quatro motivos:

| Medo | O que o site hoje responde | O que falta no celular |
| --- | --- | --- |
| "Vou ficar sem bateria" | 320 km Inmetro | quanto isso é **na vida dele** |
| "Não sei carregar isso" | 30 min de 10% a 80% | **qual é o trabalho** de carregar |
| "Vai me custar caro" | R$/km, R$/100 km | **quanto sai por mês**, comparado ao carro de hoje |
| "Não entendo esses números" | ficha de 85 campos | **uma frase por número**, e a fonte à mão |

E trava também por usabilidade: letra pequena, filtro com 10 controles, tabela de 92 linhas,
gesto escondido. A V3 já acertou o diagnóstico (duas perguntas, três números, jargão traduzido).
O app precisa ir além do que uma página web consegue fazer.

## 2. O que torna este app único

Não é "site com fonte grande". São cinco coisas que só fazem sentido em app nativo:

1. **Uma pergunta por tela, com voz.** Nada de filtro. Pergunta, resposta em cartão grande,
   avança. O resultado é lido em voz alta (TTS do Android, offline). Quem não enxerga bem ouve.
2. **Resposta na unidade da vida da pessoa.** Não "autonomia 320 km", e sim "dá para ir de São
   Paulo a Campinas e voltar sem carregar" e "uma semana de uso normal". Não "R$ 0,14/km", e sim
   "R$ 380 por mês".
3. **Comparação com o carro que a pessoa já tem.** Todo resultado termina com "no seu caso, você
   gastaria R$ 950 de gasolina — aqui são R$ 380. Sobram R$ 570 por mês". É o argumento que
   decide, e é o que a V3 já calcula (`petrolMonth`) mas apresenta de lado.
4. **Semáforo de recarga honesto.** Não é kW: é **trabalho**. "Carrega na tomada de casa, de
   noite" / "precisa instalar um carregador na garagem" / "precisa planejar as paradas na
   estrada". Três estados, uma frase cada.
5. **Decisão compartilhada.** Quem compra carro nessa faixa não decide sozinho: consulta filho,
   filha, cônjuge. Um botão gera um **cartão-resumo** para mandar no WhatsApp, com o carro, os
   três números e o motivo.

E uma sexta, que é o outro lado do pedido — **a porta técnica não tem pedagogia forçada**:

6. **Ficha técnica de qualquer carro, em dois toques.** Quem já sabe o que quer entra por
   "Consultar um carro", busca por marca/modelo em letra grande e vê os 85 campos, agrupados,
   com a fonte e a confiança de cada um. Sem responder pergunta nenhuma.

## 3. As duas portas

```
                    ┌──────────────────────────────┐
                    │   Abertura: 3 botões grandes │
                    └──────────────┬───────────────┘
             ┌─────────────────────┼─────────────────────┐
             ▼                     ▼                     ▼
   ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
   │ ME AJUDE A       │  │ CONSULTAR        │  │ COMPARAR         │
   │ ESCOLHER         │  │ UM CARRO         │  │ (2 a 4 carros)   │
   │ conversa guiada  │  │ dados técnicos   │  │ lado a lado      │
   └────────┬─────────┘  └────────┬─────────┘  └──────────────────┘
            │                     │
            │ 3 perguntas          │ busca por marca/modelo
            ▼                     ▼
   ┌──────────────────┐  ┌──────────────────┐
   │ 3 carros, em     │  │ Ficha técnica    │
   │ ordem, com 3     │◄─┤ completa, por    │
   │ números grandes  │  │ grupo, com fonte │
   └──────────────────┘  └──────────────────┘
            │
            ▼
   ┌────────────────────────────────────┐
   │ Cartão para a família (WhatsApp)   │
   │ Modo loja (letra máxima, preço)    │
   └────────────────────────────────────┘
```

A mesma tela de ficha serve as duas portas. Isso é uma decisão de arquitetura, não de UI: existe
**um** renderizador de ficha técnica, alimentado por um catálogo declarativo de campos (§5).

## 4. Regras de design senior-first (não negociáveis)

| Item | Regra | Por quê |
| --- | --- | --- |
| Corpo de texto | 20 sp mínimo; títulos 28–34 sp | 16 px do site é pouco no celular |
| Alvo de toque | 64 dp mínimo, 16 dp entre botões | mão trêmula, unha longa |
| Contraste | 7:1 no texto, 4.5:1 nos ícones | catarata, sensibilidade a contraste |
| Escala do sistema | suportar até 200% sem cortar texto | ajuste de acessibilidade do Android |
| Rolagem | só vertical; nunca horizontal | idoso perde conteúdo fora da tela |
| Tempo | nenhum timer, nenhum auto-avanço | não existe "rápido demais" |
| Gestos | todo gesto tem botão equivalente | gesto escondido é gesto que não existe |
| Voz | TTS em todo resultado; botão "ouvir" sempre visível | baixa visão |
| Ausência de dado | vira **frase**, nunca traço nem zero | "—" é lido como "não tem" |
| Erro | frase que diz o que fazer, nunca código | — |

O último item é o mais importante e o menos óbvio. Tempo de recarga DC existe em **34%** das
versões e AEB em **56%**. Num app comum isso vira uma coluna cheia de traços. Aqui, cada campo
ausente produz uma frase diferente: *"O fabricante não publica o tempo de recarga desta versão —
isso não quer dizer que não carrega rápido."*

## 5. Arquitetura

Decisão central: **o app não contém nenhum fato sobre carro.** Todo fato vem de `data/cars.json`.
Essa é a garantia de "fácil de atualizar".

```
data/cars.json  (única fonte de verdade — o mesmo arquivo do site)
        │
        ├── empacotado em app/src/main/assets/cars.json
        └── atualizado em runtime via GitHub Pages (§6)
                    │
                    ▼
        ┌───────────────────────┐
        │  CarsRepository       │  lê asset, depois cache local, compara gerado_em
        └───────────┬───────────┘
                    ▼
        ┌───────────────────────┐
        │  domínio: Car         │  desserialização tolerante (campo ausente = null)
        └───────────┬───────────┘
                    ├───────────────────────┐
                    ▼                       ▼
        ┌───────────────────────┐  ┌───────────────────────┐
        │  Traducao.kt          │  │  CampoCatalogo.kt     │
        │  número → frase       │  │  campo → rótulo,      │
        │  (funções puras)      │  │  unidade, grupo,      │
        └───────────┬───────────┘  │  como formatar        │
                    │              └───────────┬───────────┘
                    ▼                          ▼
        ┌───────────────────────┐  ┌───────────────────────┐
        │  FluxoConversa        │  │  FichaTecnicaScreen   │
        │  (3 perguntas)        │  │  (um renderizador)    │
        └───────────────────────┘  └───────────────────────┘
```

### 5.1 `CampoCatalogo.kt` — a peça que faz "novo dado" ser barato

A ficha técnica não é uma tela escrita à mão com 85 linhas. É uma lista declarativa:

```kotlin
Campo("recarga_dc_10_80_min", grupo = Recarga,
      rotulo = "Recarga rápida, 10% a 80%", unidade = "min",
      leigo = "Na estrada, quanto tempo parado no carregador",
      ausente = "O fabricante não publica este tempo nesta versão.")

Campo("porta_malas_l", grupo = Dimensoes,
      rotulo = "Porta-malas", unidade = "L",
      leigo = "Cabe a mala de uma viagem de quatro pessoas")
```

Consequências práticas:

- **Campo novo no JSON** → uma linha no catálogo, e ele aparece na ficha, na busca e no cartão.
- **Carro novo** → nada no app. Só o JSON.
- **Texto leigo** de cada campo fica num só lugar, revisável sem tocar em layout.
- A busca interna e o cartão compartilhado consomem o mesmo catálogo — sem duplicar rótulos.

### 5.2 `Traducao.kt` — número vira frase, testado contra os dados reais

Porta da lógica da V3 (`rangePhrase`, `chargeText`, `trunkText`, `warrantyText`, custo mensal),
mas como funções puras com teste unitário rodando sobre `cars.json` real:

```kotlin
fun autonomiaEmViagens(km: Int?): String      // "São Paulo → Campinas, ida e volta"
fun custoMensal(car: Car, perfil: Perfil): Reais
fun economiaVsGasolina(car: Car, perfil: Perfil): Reais
fun trabalhoDeRecarga(car: Car): NivelRecarga  // FACIL / WALLBOX / PLANEJAR
fun vereditoEmUmaFrase(car: Car, perfil: Perfil): String
```

Premissas de custo vêm de `meta` + constantes versionadas (as mesmas da V3: 15.000 km/ano,
80% em casa a R$ 0,95/kWh, gasolina R$ 6,10, 10 km/L). Ficam num único `Premissas.kt`,
exibidas na tela "Como calculamos" — porque número sem premissa não se sustenta.

## 6. Atualização de dados — três níveis

| Nível | Como | Quem já tem o app recebe | Esforço |
| --- | --- | --- | --- |
| **Dado novo** | rodar `merge.py` → commit do `cars.json` | ao abrir, com botão "Atualizar" | 1 commit |
| **Campo novo** | pipeline + uma linha no `CampoCatalogo` | nova versão do app | pequeno |
| **Só APK** | — | reinstalar | — |

Mecânica:

1. Na abertura: lê **cache local** (se existe) → senão **asset embutido**. App abre em <300 ms, offline.
2. Em segundo plano: `GET https://alemigav.github.io/carros-verdes/data/cars.json`.
3. Compara `meta.gerado_em` e `meta.schema`. Aceita só se `schema` ≤ o que o app conhece.
4. Grava em arquivo temporário e troca de forma atômica (nunca deixa JSON pela metade).
5. Falha de rede é silenciosa e não bloqueia nada. O botão "Atualizar dados" mostra o estado:
   *"Dados de 20/11/2026 — já são os mais novos"*.
6. Tela "Sobre os dados": versão em uso, origem, data, e lista de fontes com link.

`meta.schema` precisa ser adicionado ao JSON (hoje não existe) — é uma linha no `merge.py`.

## 7. Telas

1. **Abertura** — marca, e os três botões grandes (§3). Sem carrossel, sem propaganda.
2. **Conversa** — 3 telas, uma pergunta cada: quanto pode gastar; como vai usar (cidade /
   misto / estrada); o que mais importa (economia / espaço / viajar tranquilo). Botão "voltar"
   sempre visível.
3. **Resultado** — 3 carros em ordem. Cada cartão: foto, nome, e três números grandes (R$/mês,
   autonomia em viagem, porta-malas). Rodapé: *"Por que este: gasta pouco e anda bastante"*.
   Botões: **Ouvir**, **Entender este carro**, **Ficha técnica**.
4. **Entender este carro** — a explicação em linguagem simples + comparação com gasolina em
   reais/mês. É a tela que convence.
5. **Ficha técnica** — 9 grupos recolhíveis, todos fechados menos Preço. Cada linha mostra a
   fonte e a confiança ao toque.
6. **Consultar um carro** — busca em letra grande, lista alfabética por marca, atalho direto
   para a ficha. Sem questionário.
7. **Comparar** — 2 a 4 carros, só as linhas que diferem, melhor valor destacado.
8. **Modo loja** — letra máxima, preço de tabela do carro escolhido + data de referência,
   pensado para olhar na concessionária.
9. **Cartão para a família** — resumo em imagem/texto para WhatsApp.
10. **Sobre os dados** — versão, fontes, premissas de cálculo, licença.

## 8. Stack e empacotamento

- Kotlin + Jetpack Compose + Material 3. Sem biblioteca de terceiros além de
  `kotlinx.serialization` e `androidx.datastore`.
- `minSdk 26` (Android 8) — muitos aparelhos em uso na faixa 60+ são antigos.
  `targetSdk 36`.
- Fotos: as 73 do `v3/photos` (3,7 MB) reencodadas para WebP 720 px → ~1,2 MB.
- Meta de APK: **< 15 MB**, funcionando 100% offline.
- TTS por `android.speech.tts.TextToSpeech` (voz pt-BR do sistema, offline).
- Compartilhamento: `Intent.ACTION_SEND` com texto + imagem — sem SDK de terceiros.

## 9. Escopo

**MVP (o que eu construo primeiro)**

- Projeto Gradle compilando e gerando APK.
- `CarsRepository` + desserialização tolerante + teste unitário sobre o `cars.json` real.
- `CampoCatalogo` com os 9 grupos.
- Portas 1 e 2: conversa de 3 perguntas e ficha técnica de qualquer carro.
- Custo mensal + comparação com gasolina.
- Acessibilidade: fonte, alvos, escala, TTS, TalkBack.
- Atualização remota com botão e estado.

**Depois**

- Cartão para WhatsApp, modo loja, comparador lado a lado, gráficos.
- Pesquisa por voz na pergunta (reconhecimento do sistema).
- Modo escuro dedicado (além do automático).

## 10. Decisões em aberto (preciso da sua resposta)

1. **Nome da conversa guiada.** "Me ajude a escolher" é claro mas burocrático. Alternativas:
   "Quero ajuda", "Vamos por partes", "Conversar". 
2. **Viagens de referência.** "São Paulo → Campinas" funciona para SP. Uso três trechos
   regionais por faixa de autonomia (curto/médio/longo) e deixo explícito que é exemplo?
3. **Publicação.** APK direto (lado a lado com o site) ou Play Store? Muda assinatura, política
   de privacidade e o texto de "Sobre".
4. **PHEV flex.** Os 11 híbridos plug-in flex entram no mesmo fluxo com aviso de combustível, ou
   ficam numa seção separada "também usam gasolina"?
