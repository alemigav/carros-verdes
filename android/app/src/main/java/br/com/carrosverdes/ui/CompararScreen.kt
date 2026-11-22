package br.com.carrosverdes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.carrosverdes.data.Car
import br.com.carrosverdes.domain.Comparacao
import br.com.carrosverdes.domain.GrupoComparacao
import br.com.carrosverdes.domain.LinhaComparada

/**
 * Comparação lado a lado com o melhor de cada linha.
 * Com três ou quatro carros — ou com a fonte do sistema grande — as linhas viram blocos
 * empilhados, porque quatro colunas em celular não cabem de forma legível.
 */
@Composable
fun CompararScreen(
    cars: List<Car>,
    aoAbrirFicha: (String) -> Unit,
    aoLimparSelecao: () -> Unit,
) {
    var somenteDiferencas by rememberSaveable { mutableStateOf(false) }
    val escala = LocalDensity.current.fontScale
    val empilhado = cars.size > 2 || escala >= 1.3f

    val linhas = Comparacao.linhas(cars, somenteDiferencas)
    val grupos = GrupoComparacao.entries.mapNotNull { grupo ->
        linhas.filter { it.grupo == grupo }.takeIf { it.isNotEmpty() }?.let { grupo to it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tamanhos.espacoGrande, vertical = Tamanhos.espaco),
        verticalArrangement = Arrangement.spacedBy(Tamanhos.espaco),
    ) {
        Text(
            text = if (cars.size == 2) "Comparando 2 carros" else "Comparando ${cars.size} carros",
            style = MaterialTheme.typography.headlineLarge,
            color = Cores.Tinta,
        )
        Text(
            "O melhor valor de cada linha aparece destacado em verde. Empate não destaca ninguém.",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.TintaSuave,
        )

        FileiraDeChips {
            Chip("Só as diferenças", somenteDiferencas) { somenteDiferencas = !somenteDiferencas }
        }

        // Quem está sendo comparado, com atalho para a ficha.
        cars.forEach { car ->
            Column {
                TextButton(
                    onClick = { aoAbrirFicha(car.id) },
                    modifier = Modifier.heightIn(min = Tamanhos.alvo),
                ) {
                    Text(
                        text = "${car.nome} · ${car.versao.ifEmpty { "versão única" }}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Cores.Azul,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }

        if (linhas.isEmpty()) {
            Aviso("Com “só as diferenças” ligado, estes carros ficaram iguais em tudo o que comparamos.")
        }

        grupos.forEach { (grupo, linhasDoGrupo) ->
            Text(grupo.titulo, style = MaterialTheme.typography.titleLarge, color = Cores.VerdeEscuro)
            HorizontalDivider(color = Cores.Borda)

            if (empilhado) {
                linhasDoGrupo.forEach { linha ->
                    BlocoEmpilhado(linha, cars)
                }
            } else {
                CabecalhoDeDuasColunas(cars)
                linhasDoGrupo.forEach { linha ->
                    LinhaDeDuasColunas(linha, cars)
                }
            }
        }

        BotaoGrande(texto = "Limpar a seleção", aoClicar = aoLimparSelecao, secundario = true)
    }
}

@Composable
private fun CabecalhoDeDuasColunas(cars: List<Car>) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
        Text(
            "",
            modifier = Modifier.weight(1.1f),
        )
        cars.forEach { car ->
            Text(
                text = car.modelo,
                style = MaterialTheme.typography.titleMedium,
                color = Cores.Tinta,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun LinhaDeDuasColunas(linha: LinhaComparada, cars: List<Car>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = linha.rotulo,
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.TintaSuave,
            modifier = Modifier.weight(1.1f),
        )
        linha.valores.forEachIndexed { indice, valor ->
            val melhor = indice in linha.melhores
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Text(
                    text = valor ?: "não publicado",
                    style = if (melhor) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    color = if (valor == null) Cores.TintaSuave else if (melhor) Cores.VerdeEscuro else Cores.Tinta,
                    textAlign = TextAlign.Center,
                    modifier = if (melhor) {
                        Modifier
                            .background(Cores.VerdeClaro, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    } else {
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    },
                )
            }
        }
    }
}

@Composable
private fun BlocoEmpilhado(linha: LinhaComparada, cars: List<Car>) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(linha.rotulo, style = MaterialTheme.typography.titleMedium, color = Cores.TintaSuave)
        cars.forEachIndexed { indice, car ->
            val melhor = indice in linha.melhores
            val valor = linha.valores.getOrNull(indice)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (melhor) {
                            Modifier
                                .background(Cores.VerdeClaro, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        } else {
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        },
                    ),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text = car.modelo,
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.TintaSuave,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = valor ?: "não publicado",
                    style = if (melhor) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    color = if (valor == null) Cores.TintaSuave else if (melhor) Cores.VerdeEscuro else Cores.Tinta,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
