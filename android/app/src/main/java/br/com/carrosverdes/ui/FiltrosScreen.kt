package br.com.carrosverdes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.carrosverdes.data.Car
import br.com.carrosverdes.domain.CARROCERIAS
import br.com.carrosverdes.domain.Equipamento
import br.com.carrosverdes.domain.Filtros
import br.com.carrosverdes.domain.Motorizacao
import br.com.carrosverdes.domain.OpcoesDeFiltro
import br.com.carrosverdes.domain.RecargaCasa
import br.com.carrosverdes.domain.agruparEmModelos
import br.com.carrosverdes.domain.rotuloCarroceria

/**
 * O cenário de filtragem completo, herdado da versão web: preço com piso e teto,
 * autonomia, bateria, recarga, potência, espaço, carroceria, tração, equipamentos e marca.
 * Tudo em chip: nada de arrastar controle pequeno com o dedo trêmulo.
 */
@Composable
fun FiltrosScreen(
    filtrosIniciais: Filtros,
    cars: List<Car>,
    opcoes: OpcoesDeFiltro,
    aoAplicar: (Filtros) -> Unit,
) {
    var rascunho by remember { mutableStateOf(filtrosIniciais) }
    // A lista mostra modelos, então a contagem aqui também é de modelos.
    val quantidade = remember(rascunho, cars) { agruparEmModelos(rascunho.aplicar(cars)).size }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Tamanhos.espacoGrande,
                end = Tamanhos.espacoGrande,
                top = Tamanhos.espaco,
                bottom = 130.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Tamanhos.espacoGrande),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { rascunho = Filtros() },
                        modifier = Modifier.heightIn(min = Tamanhos.alvo),
                    ) {
                        Text("Limpar tudo", style = MaterialTheme.typography.labelMedium, color = Cores.Azul)
                    }
                }
            }

            item {
                Secao("Preço", "O valor à vista, de tabela.") {
                    Text("Até", style = MaterialTheme.typography.labelSmall, color = Cores.Azul)
                    FileiraDeChips {
                        listOf(150_000, 200_000, 300_000, 400_000, 500_000).forEach { valor ->
                            Chip(
                                "${valor / 1000} mil",
                                rascunho.precoMax == valor,
                            ) {
                                rascunho = rascunho.copy(
                                    precoMax = if (rascunho.precoMax == valor) null else valor,
                                    precoMin = null,
                                )
                            }
                        }
                    }
                    Text("A partir de", style = MaterialTheme.typography.labelSmall, color = Cores.Azul)
                    FileiraDeChips {
                        listOf(100_000, 150_000, 200_000, 300_000).forEach { valor ->
                            Chip(
                                "${valor / 1000} mil",
                                rascunho.precoMin == valor,
                            ) {
                                rascunho = rascunho.copy(
                                    precoMin = if (rascunho.precoMin == valor) null else valor,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Secao("Motorização", "Elétricos e híbridos flex, na mesma lista. O selo do cartão avisa quem também usa combustível.") {
                    FileiraDeChips {
                        Motorizacao.entries.forEach { motorizacao ->
                            Chip(motorizacao.rotulo, rascunho.motorizacao == motorizacao) {
                                rascunho = rascunho.copy(motorizacao = motorizacao)
                            }
                        }
                    }
                }
            }

            item {
                Secao("Autonomia com uma carga", "A partir do número certificado, em quilômetros.") {
                    FileiraDeChips {
                        listOf(200, 250, 300, 400, 500).forEach { minimo ->
                            Chip("$minimo km", rascunho.autonomiaMin == minimo) {
                                rascunho = rascunho.copy(
                                    autonomiaMin = if (rascunho.autonomiaMin == minimo) null else minimo,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Secao("Bateria", "Capacidade a partir de, em kWh.") {
                    FileiraDeChips {
                        listOf(30.0, 40.0, 50.0, 60.0, 80.0).forEach { minimo ->
                            Chip("${minimo.toInt()} kWh", rascunho.bateriaMin == minimo) {
                                rascunho = rascunho.copy(
                                    bateriaMin = if (rascunho.bateriaMin == minimo) null else minimo,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Secao("Recarga rápida", "Potência que o carro aceita em posto de estrada.") {
                    FileiraDeChips {
                        listOf(50, 80, 100, 150, 200).forEach { potencia ->
                            Chip("$potencia kW", rascunho.recargaDcMin == potencia) {
                                rascunho = rascunho.copy(
                                    recargaDcMin = if (rascunho.recargaDcMin == potencia) null else potencia,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Secao("Recarga em casa", "Tomada comum não precisa de obra; carregador de parede precisa de instalação.") {
                    FileiraDeChips {
                        RecargaCasa.entries.forEach { opcao ->
                            Chip(opcao.rotulo, rascunho.recargaCasa == opcao) {
                                rascunho = rascunho.copy(recargaCasa = opcao)
                            }
                        }
                    }
                }
            }

            item {
                Secao("Potência", "Cavalos de potência, somando os motores no caso do híbrido.") {
                    FileiraDeChips {
                        listOf(100, 150, 200, 300).forEach { minimo ->
                            Chip("$minimo cv", rascunho.potenciaMin == minimo) {
                                rascunho = rascunho.copy(
                                    potenciaMin = if (rascunho.potenciaMin == minimo) null else minimo,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Secao("Lugares e porta-malas", "Quantidade mínima e litros mínimos.") {
                    FileiraDeChips {
                        listOf(5, 7).forEach { lugares ->
                            Chip("$lugares lugares", rascunho.lugaresMin == lugares) {
                                rascunho = rascunho.copy(
                                    lugaresMin = if (rascunho.lugaresMin == lugares) null else lugares,
                                )
                            }
                        }
                        listOf(300, 400, 500, 600).forEach { litros ->
                            Chip("${litros} L", rascunho.portaMalasMin == litros) {
                                rascunho = rascunho.copy(
                                    portaMalasMin = if (rascunho.portaMalasMin == litros) null else litros,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Secao("Carroceria") {
                    FileiraDeChips {
                        CARROCERIAS.filter { par -> opcoes.carrocerias.contains(par.first) }.forEach { (chave, _) ->
                            Chip(rotuloCarroceria(chave), chave in rascunho.carrocerias) {
                                rascunho = rascunho.copy(
                                    carrocerias = if (chave in rascunho.carrocerias) {
                                        rascunho.carrocerias - chave
                                    } else {
                                        rascunho.carrocerias + chave
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Secao("Tração", "Onde a força chega: dianteira, traseira ou nas quatro rodas.") {
                    FileiraDeChips {
                        opcoes.tracoes.forEach { tracao ->
                            Chip(tracao, tracao in rascunho.tracoes) {
                                rascunho = rascunho.copy(
                                    tracoes = if (tracao in rascunho.tracoes) {
                                        rascunho.tracoes - tracao
                                    } else {
                                        rascunho.tracoes + tracao
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Secao(
                    "Equipamentos obrigatórios",
                    "Só entram carros que tenham todos os itens marcados. Campo não publicado pelo fabricante conta como ausente.",
                ) {
                    FileiraDeChips {
                        Equipamento.entries.forEach { equipamento ->
                            Chip(equipamento.rotulo, equipamento in rascunho.equipamentos) {
                                rascunho = rascunho.copy(
                                    equipamentos = if (equipamento in rascunho.equipamentos) {
                                        rascunho.equipamentos - equipamento
                                    } else {
                                        rascunho.equipamentos + equipamento
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Secao("Marca", "Marque uma ou várias.") {
                    FileiraDeChips {
                        opcoes.marcas.forEach { marca ->
                            Chip(marca, marca in rascunho.marcas) {
                                rascunho = rascunho.copy(
                                    marcas = if (marca in rascunho.marcas) {
                                        rascunho.marcas - marca
                                    } else {
                                        rascunho.marcas + marca
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(Tamanhos.espacoGrande),
            shape = RoundedCornerShape(Tamanhos.raio),
            color = Cores.Papel2,
            border = androidx.compose.foundation.BorderStroke(1.dp, Cores.Borda),
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(Tamanhos.espaco)) {
                BotaoGrande(
                    texto = when (quantidade) {
                        0 -> "Nenhum carro com esses filtros"
                        1 -> "Ver 1 carro"
                        else -> "Ver $quantidade carros"
                    },
                    aoClicar = { aoAplicar(rascunho) },
                )
            }
        }
    }
}

@Composable
private fun Secao(titulo: String, ajuda: String? = null, conteudo: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleLarge, color = Cores.VerdeEscuro)
        ajuda?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Cores.TintaSuave)
        }
        conteudo()
    }
}

@Composable
fun OrdenarScreen(
    ordemAtual: br.com.carrosverdes.domain.Ordem,
    aoEscolher: (br.com.carrosverdes.domain.Ordem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tamanhos.espacoGrande, vertical = Tamanhos.espaco),
        verticalArrangement = Arrangement.spacedBy(Tamanhos.espaco),
    ) {
        Text("Ordenar por", style = MaterialTheme.typography.headlineLarge, color = Cores.Tinta)
        Text(
            "A ordem é sempre um critério escrito, que você pode conferir na ficha de cada carro.",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.TintaSuave,
        )

        br.com.carrosverdes.domain.Ordem.entries
            .groupBy { it.grupo }
            .forEach { (grupo, ordens) ->
                Text(grupo, style = MaterialTheme.typography.titleMedium, color = Cores.VerdeEscuro)
                ordens.forEach { ordem ->
                    Chip(
                        texto = ordem.rotulo,
                        selecionado = ordem == ordemAtual,
                        aoClicar = { aoEscolher(ordem) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
    }
}
