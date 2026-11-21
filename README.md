# Carros Verdes

Comparador de carros elétricos e híbridos plug-in flex à venda no Brasil: preço de tabela,
autonomia Inmetro, recarga, equipamentos e custo real por quilômetro — com a fonte de
cada número ao lado.

**Site publicado:** https://alemigav.github.io/carros-verdes/

> **Uso reservado.** © 2026 Alexandre Michel Gavronski. Todos os direitos reservados.
> Este aplicativo **não é software livre** e não é distribuído sob licença aberta.
> O repositório é público para hospedar o site, e isso não concede licença de uso,
> cópia, modificação ou redistribuição. Leia [LICENSE](LICENSE) antes de qualquer
> reuso. Dados de terceiros mantêm suas próprias licenças (ver *Fontes* abaixo).

---

## O que a interface faz

- **90 versões** no total — 79 elétricos e 11 híbridos plug-in flex, com preço de 20/11/2026.
- **Nota pessoal de 0 a 100**: oito critérios com peso ajustável (custo-benefício,
  autonomia, eficiência, recarga rápida, desempenho, espaço, segurança, conforto).
  Seis presets (Equilibrado, Cidade e economia, Viagens longas, Família, Bem equipado,
  Desempenho).
- **Filtros**: busca por texto, faixa de preço, autonomia, bateria, potência de recarga
  DC, carroceria, marca, tração e dez itens obrigatórios (ACC, AEB, câmera 360°, V2L,
  bomba de calor etc.).
- **Quatro visões**: cartões com veredito do topo da lista, tabela ordenável, gráficos
  (preço × autonomia, km de autonomia mais baratos, mais eficientes) e custo de rodagem
  com parâmetros de uso editáveis.
- **Comparação lado a lado** de até quatro versões, com destaque do melhor valor de
  cada linha.
- **Ficha completa** por versão, em oito blocos (preço, bateria, recarga, motorização,
  dimensões, segurança, conforto, garantia), com fontes, observações e indicador de
  confiança dos dados.
- **Exportação CSV** do recorte atual, com BOM e separador `;` para abrir direto no
  Excel em português.
- Campos sem fonte verificável ficam **em branco**, não em zero; cada carro informa
  quantos campos foram verificados.

## Identidade visual

Marca "Carros Verdes": símbolo circular com folha e raio (o elétrico dentro do verde),
wordmark de peso forte com "Verdes" em verde institucional (`#0d6b57`).

| Arquivo | Uso |
| --- | --- |
| `assets/mark.svg` | símbolo (folha + raio), usado no cabeçalho e no rodapé |
| `favicon.svg` | favicon simplificado (raio sobre verde), legível em 16 px |
| `assets/logo.svg` | lockup símbolo + wordmark + assinatura |
| `assets/og-image.png` | cartão social 1200×630 |
| `assets/apple-touch-icon.png` | ícone 180×180 para iOS |

## Como rodar localmente

O app é estático, sem build. Precisa de um servidor HTTP porque os dados são buscados
via `fetch` (abrir por `file://` bloqueia a leitura):

```bash
python3 -m http.server 8000 --bind 127.0.0.1
# abra http://127.0.0.1:8000/
```

## Estrutura

```
index.html            marca, hero, filtros, quatro visões, rodapé legal
styles.css            tokens, layout, componentes e responsivo
app.js                estado, motor de notas, filtros, fichas, comparação, CSV
views.js              renderizadores (cartões, tabela, gráficos, custos)
data/cars.json        base compilada (90 versões)
data/features/*.json  pesquisa por carro (aplicada sobre aa_fallback.json)
data/build_base.py    planilha → cars_base.json
data/merge.py         base + pesquisa → cars.json
assets/               marca, ícone social, favicon
```

## Pipeline de dados

`data/build_base.py` lê a planilha `comparativo_eletricos_brasil_20-11-2026.xlsx` e gera
`data/cars_base.json`; `data/merge.py` aplica a pesquisa em `data/features/batch_NN.json`
sobre `aa_fallback.json` (o pesquisado vence) e grava `data/cars.json`. A base guarda o
preço do ano-modelo na versão citada — nunca empresta número de outra versão: campo sem
fonte fica nulo.

## Fontes

- **Recarregados** — ficha dos carros elétricos e híbridos, licença **CC BY 4.0**:
  https://recarregados.com/dados/carros-eletricos-hibridos-ficha
- **Primeiro Carro Elétrico** — catálogo (uso editorial, com link):
  https://www.primeirocarroeletrico.com.br/modelos
- **Eletrificado** — preços de novembro/2026 (uso editorial, com link):
  https://eletrificado.com/precos-carros-eletricos-novembro-2026/
- Páginas de especificações de cada fabricante, listadas na ficha de cada versão.

## Licença

Proprietária, com todos os direitos reservados. Ver [LICENSE](LICENSE). O conjunto
Recarregados (CC BY 4.0) e as páginas de fabricantes seguem as licenças de seus autores;
a compilação, a curadoria e a apresentação dos dados neste projeto são reservadas.

Autor: **Alexandre Michel Gavronski** — https://github.com/alemigav
