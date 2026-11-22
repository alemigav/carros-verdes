package br.com.carrosverdes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.carrosverdes.R
import br.com.carrosverdes.data.Fotos
import br.com.carrosverdes.domain.CustoMensal
import br.com.carrosverdes.domain.Filtros
import br.com.carrosverdes.domain.Motorizacao
import br.com.carrosverdes.domain.Modelo
import br.com.carrosverdes.domain.Ordenacao
import br.com.carrosverdes.domain.Traducao

/**
 * A busca viva: cabeçalho fixo com busca, atalhos e contagem; só os cartões rolam.
 * Assim o filtro nunca sai do alcance enquanto a pessoa percorre a lista.
 */
@Composable
fun ListaScreen(
    modelos: List<Modelo>,
    totalNaBase: Int,
    estado: EstadoExploracao,
    fotos: Fotos,
    aoBuscar: (String) -> Unit,
    aoEscolherPreset: (Filtros) -> Unit,
    aoAbrirFiltros: () -> Unit,
    aoAbrirOrdem: () -> Unit,
    aoRemoverEtiqueta: ((Filtros) -> Filtros) -> Unit,
    aoLimparFiltros: () -> Unit,
    aoAlternarSelecao: (String) -> Unit,
    aoAbrirFicha: (String) -> Unit,
    aoComparar: () -> Unit,
    aoDescartarSelecao: () -> Unit,
    aoAbrirGuia: () -> Unit,
) {
    val quantidade = modelos.size
    val selecionados = estado.selecionados.size
    val etiquetas = estado.filtros.etiquetas()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ----- cabeçalho fixo -----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Tamanhos.espacoGrande, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = estado.filtros.busca,
                        onValueChange = aoBuscar,
                        placeholder = {
                            Text("Marca ou modelo", style = MaterialTheme.typography.bodySmall)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp),
                    )
                    // Filtros em cor cheia: precisa ser visto antes de ser procurado.
                    Surface(
                        onClick = aoAbrirFiltros,
                        modifier = Modifier.heightIn(min = 56.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Cores.Verde,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_filtro),
                                contentDescription = null,
                                tint = Cores.Papel2,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = if (etiquetas.isEmpty()) {
                                    "Filtros"
                                } else {
                                    "Filtros (${etiquetas.size})"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = Cores.Papel2,
                            )
                        }
                    }
                }

                FileiraDeChips {
                    PRESETS.forEach { (rotulo, preset) ->
                        Chip(
                            texto = rotulo,
                            selecionado = estado.filtros == preset,
                            aoClicar = { aoEscolherPreset(if (estado.filtros == preset) Filtros() else preset) },
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            quantidade == totalNaBase -> "$quantidade carros"
                            quantidade == 1 -> "1 de $totalNaBase carros"
                            else -> "$quantidade de $totalNaBase carros"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Cores.Tinta,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = aoAbrirOrdem,
                        modifier = Modifier.heightIn(min = Tamanhos.alvo),
                    ) {
                        Text(
                            text = "Ordenar: ${estado.ordem.curto}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Cores.Azul,
                        )
                    }
                }
            }

            // ----- só os cartões rolam -----
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = Tamanhos.espacoGrande,
                    end = Tamanhos.espacoGrande,
                    top = 4.dp,
                    bottom = if (selecionados > 0) 150.dp else Tamanhos.espacoGrande,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (etiquetas.isNotEmpty()) {
                    item {
                        Column {
                            FileiraDeChips {
                                etiquetas.forEach { etiqueta ->
                                    EtiquetaRemovivel(etiqueta.texto, { aoRemoverEtiqueta(etiqueta.remover) })
                                }
                            }
                            TextButton(
                                onClick = aoLimparFiltros,
                                modifier = Modifier.heightIn(min = Tamanhos.alvo),
                            ) {
                                Text(
                                    "Limpar tudo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Cores.Azul,
                                )
                            }
                        }
                    }
                }

                estado.perfilAplicado?.let { resumo ->
                    item {
                        Aviso("Busca preenchida pelas suas respostas: $resumo. Tudo aqui pode ser ajustado.")
                    }
                }

                if (modelos.isEmpty()) {
                    item {
                        Aviso(
                            "Nenhum carro atende a esses filtros. " +
                                "Toque em “Limpar tudo” e tente uma faixa de preço maior.",
                        )
                    }
                }

                itemsIndexed(modelos, key = { _, modelo -> modelo.id }) { indice, modelo ->
                    LinhaDeModelo(
                        modelo = modelo,
                        posicao = Ordenacao.posicao(indice, estado.ordem),
                        fotos = fotos,
                        selecionado = modelo.id in estado.selecionados,
                        aoSelecionar = { aoAlternarSelecao(modelo.id) },
                        aoAbrir = { aoAbrirFicha(modelo.ideal.id) },
                    )
                }

                item {
                    BotaoGrande(texto = "Não sei por onde começar", aoClicar = aoAbrirGuia, secundario = true)
                }
            }
        }

        if (selecionados > 0) {
            BandejaDeComparacao(
                quantidade = selecionados,
                aoComparar = aoComparar,
                aoDescartar = aoDescartarSelecao,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private val PRESETS: List<Pair<String, Filtros>> = listOf(
    "Todos" to Filtros(),
    "Elétricos" to Filtros(motorizacao = Motorizacao.ELETRICO),
    "Híbridos flex" to Filtros(motorizacao = Motorizacao.HIBRIDO),
)

@Composable
private fun LinhaDeModelo(
    modelo: Modelo,
    posicao: String,
    fotos: Fotos,
    selecionado: Boolean,
    aoSelecionar: () -> Unit,
    aoAbrir: () -> Unit,
) {
    val foto = fotos.de(modelo.ideal)
    var imagem by remember(foto?.arquivo) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(foto?.arquivo) { imagem = foto?.let { fotos.carregar(it) } }

    val versoes = modelo.versoes.sortedBy { it.preco ?: Int.MAX_VALUE }

    Card(
        onClick = aoAbrir,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tamanhos.raio),
        colors = CardDefaults.cardColors(containerColor = Cores.Papel2),
        border = BorderStroke(1.dp, Cores.Borda),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Cores.VerdeClaro,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    val atual = imagem
                    if (atual != null) {
                        Image(
                            bitmap = atual,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = modelo.nome,
                        style = MaterialTheme.typography.titleMedium,
                        color = Cores.Tinta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (modelo.hibrido) {
                            "${modelo.versoesEmPalavras()} · roda também com etanol ou gasolina"
                        } else {
                            modelo.versoesEmPalavras()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (modelo.hibrido) Cores.Alerta else Cores.TintaSuave,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Comparação fica no topo do cartão: libera altura para as versões.
                Row(
                    modifier = Modifier
                        .toggleable(
                            value = selecionado,
                            onValueChange = { aoSelecionar() },
                            role = Role.Checkbox,
                        )
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = selecionado, onCheckedChange = { aoSelecionar() })
                    Text(
                        "Comparar",
                        style = MaterialTheme.typography.labelSmall,
                        color = Cores.Azul,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }

            // Cada versão com o seu preço e os seus números: nada de esconder versão.
            versoes.forEach { versao ->
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = versao.versao.ifEmpty { "versão única" },
                            style = MaterialTheme.typography.bodySmall,
                            color = Cores.Tinta,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = versao.preco?.let { Traducao.reais(it.toDouble()) } ?: "sem preço",
                            style = MaterialTheme.typography.titleSmall,
                            color = Cores.VerdeEscuro,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = resumo(versao),
                        style = MaterialTheme.typography.bodySmall,
                        color = Cores.TintaSuave,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Text(
                text = posicao,
                style = MaterialTheme.typography.labelSmall,
                color = Cores.Azul,
            )
        }
    }
}

private fun resumo(car: br.com.carrosverdes.data.Car): String {
    val custo = when (val calculo = Traducao.custoMensal(car)) {
        is CustoMensal.Completo -> "${Traducao.reais(calculo.reais)}/mês"
        is CustoMensal.Parcial -> "${Traducao.reais(calculo.reais)}/mês só de energia"
        is CustoMensal.Indisponivel -> "custo não estimado"
    }
    val autonomia = car.autonomia?.let { Traducao.km(it) } ?: "autonomia não publicada"
    val portaMalas = car.numero("porta_malas_l")?.let { Traducao.litros(it) } ?: "porta-malas não publicado"
    return "$custo · $autonomia · $portaMalas"
}

@Composable
private fun BandejaDeComparacao(
    quantidade: Int,
    aoComparar: () -> Unit,
    aoDescartar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(Tamanhos.espacoGrande),
        shape = RoundedCornerShape(Tamanhos.raio),
        color = Cores.Papel2,
        border = BorderStroke(1.dp, Cores.Borda),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Tamanhos.espaco, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (quantidade == 1) "1 selecionado" else "$quantidade selecionados",
                    style = MaterialTheme.typography.titleMedium,
                    color = Cores.Tinta,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = aoDescartar, modifier = Modifier.heightIn(min = Tamanhos.alvo)) {
                    Text("Limpar", style = MaterialTheme.typography.labelMedium, color = Cores.Azul)
                }
            }
            if (quantidade >= 2) {
                BotaoGrande(texto = "Comparar $quantidade carros", aoClicar = aoComparar)
            } else {
                Text(
                    "Selecione mais um carro para comparar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.TintaSuave,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
    }
}
