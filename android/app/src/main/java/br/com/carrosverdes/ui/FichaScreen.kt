package br.com.carrosverdes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import br.com.carrosverdes.data.Car
import br.com.carrosverdes.data.Fotos
import br.com.carrosverdes.domain.Bloco
import br.com.carrosverdes.domain.CampoCatalogo
import br.com.carrosverdes.domain.CustoMensal
import br.com.carrosverdes.domain.Traducao

@Composable
fun FichaScreen(
    car: Car,
    todasAsVersoes: List<Car>,
    fotos: Fotos,
    aoAbrirFicha: (String) -> Unit,
) {
    val blocos = CampoCatalogo.blocos(car)
    val irmaos = irmaosDe(car, todasAsVersoes)
    val tratador = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tamanhos.espacoGrande, vertical = Tamanhos.espaco),
        verticalArrangement = Arrangement.spacedBy(Tamanhos.espaco),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = car.nome,
                style = MaterialTheme.typography.headlineLarge,
                color = Cores.Tinta,
            )
            Text(
                text = car.versao,
                style = MaterialTheme.typography.titleMedium,
                color = Cores.TintaSuave,
            )
            Text(
                text = Traducao.precoEmPalavras(car),
                style = MaterialTheme.typography.headlineSmall,
                color = Cores.VerdeEscuro,
            )
            Text(
                text = etiquetaDeMotorizacao(car),
                style = MaterialTheme.typography.bodyMedium,
                color = if (car.hibrido) Cores.Alerta else Cores.VerdeEscuro,
            )
            Selo(Traducao.confiancaEmPalavras(car))
        }

        // Seletor de versão: deixa explícito que a ficha mostra UMA versão do modelo.
        if (irmaos.isNotEmpty()) {
            val todas = (listOf(car) + irmaos).sortedBy { it.preco ?: Int.MAX_VALUE }
            Text(
                text = "Este modelo tem ${todas.size} versões. Você está vendo:",
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.TintaSuave,
            )
            FileiraDeChips {
                todas.forEach { versao ->
                    Chip(
                        texto = buildString {
                            append(versao.versao.ifEmpty { "versão" })
                            versao.preco?.let { append(" · ${Traducao.reais(it.toDouble())}") }
                        },
                        selecionado = versao.id == car.id,
                        aoClicar = { if (versao.id != car.id) aoAbrirFicha(versao.id) },
                    )
                }
            }
        }

        FotoDoCarro(fotos, car)

        (Traducao.custoMensal(car) as? CustoMensal.Parcial)?.let { parcial ->
            Aviso("Gasto mensal estimado em ${Traducao.reais(parcial.reais)}. ${parcial.motivo}")
        }

        BotaoOuvir { resumoDaFicha(car) }

        Column(verticalArrangement = Arrangement.spacedBy(Tamanhos.espaco)) {
            Text(
                text = "O essencial, em uma frase cada",
                style = MaterialTheme.typography.titleLarge,
                color = Cores.Tinta,
            )
            Text(Traducao.fraseAutonomia(car), style = MaterialTheme.typography.bodyLarge, color = Cores.Tinta)
            val recarga = Traducao.recarga(car)
            Text(recarga.emCasa, style = MaterialTheme.typography.bodyLarge, color = Cores.Tinta)
            Text(recarga.naEstrada, style = MaterialTheme.typography.bodyLarge, color = Cores.Tinta)
            Text(Traducao.frasePortaMalas(car), style = MaterialTheme.typography.bodyLarge, color = Cores.Tinta)
            Text(Traducao.fraseGarantiaBateria(car), style = MaterialTheme.typography.bodyLarge, color = Cores.Tinta)
        }

        Text(
            text = "Ficha técnica completa",
            style = MaterialTheme.typography.headlineMedium,
            color = Cores.Tinta,
            modifier = Modifier.padding(top = Tamanhos.espaco),
        )
        Text(
            text = "Toque no nome de cada bloco para abrir. Campo sem informação aparece com uma frase, " +
                "nunca com um traço.",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.TintaSuave,
        )

        blocos.forEachIndexed { indice, bloco ->
            BlocoDaFicha(bloco = bloco, abertoInicial = indice == 0)
        }

        if (car.fontes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "De onde vêm estes dados",
                    style = MaterialTheme.typography.titleLarge,
                    color = Cores.Tinta,
                )
                car.fontes.forEach { fonte ->
                    Text(
                        text = fonte,
                        style = MaterialTheme.typography.bodySmall,
                        color = Cores.Azul,
                        modifier = Modifier.clickable {
                            runCatching { tratador.openUri(fonte) }
                        },
                    )
                }            }
        }

    }
}

/** "1 item" e "2 itens": o app inteiro evita texto que soe automático. */
private fun quantidadeDeItens(quantidade: Int): String =
    if (quantidade == 1) "1 item" else "$quantidade itens"

/** A diferença que mais importa para quem está comprando: este carro queima combustível ou não. */
fun etiquetaDeMotorizacao(car: Car): String =
    if (car.hibrido) {
        "Híbrido plug-in flex: além do motor elétrico, tem motor a combustão que roda com etanol ou gasolina."
    } else {
        "Elétrico: roda só com bateria, sem combustível."
    }

@Composable
private fun BlocoDaFicha(bloco: Bloco, abertoInicial: Boolean) {
    var aberto by rememberSaveable(bloco.grupo) { mutableStateOf(abertoInicial) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tamanhos.raio),
        colors = CardDefaults.cardColors(containerColor = Cores.Papel2),
        border = BorderStroke(1.dp, Cores.Borda),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { aberto = !aberto }
                    .heightIn(min = Tamanhos.alvo)
                    .padding(Tamanhos.espaco),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = bloco.grupo.titulo,
                        style = MaterialTheme.typography.titleLarge,
                        color = Cores.VerdeEscuro,
                    )
                    Text(
                        text = if (aberto) bloco.grupo.leigo else quantidadeDeItens(bloco.linhas.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = Cores.TintaSuave,
                    )
                }
                Text(
                    text = if (aberto) "Fechar" else "Abrir",
                    style = MaterialTheme.typography.labelMedium,
                    color = Cores.Azul,
                )
            }

            if (aberto) {
                HorizontalDivider(color = Cores.Borda)
                Column(Modifier.padding(Tamanhos.espaco), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    bloco.linhas.forEach { linha ->
                        Column {
                            Text(
                                text = linha.rotulo,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Cores.TintaSuave,
                            )
                            val valor = linha.valor
                            if (valor != null) {
                                Text(
                                    text = valor,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Cores.Tinta,
                                )
                            } else {
                                Text(
                                    text = linha.ausente,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Cores.Alerta,
                                    fontStyle = FontStyle.Italic,
                                )
                            }
                            linha.explicacao?.let { explicacao ->
                                Text(
                                    text = explicacao,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Cores.TintaSuave,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun resumoDaFicha(car: Car): String {
    val partes = mutableListOf<String>()
    partes += "${car.nome}, versão ${car.versao}."
    car.preco?.let { partes += "Preço de tabela ${Traducao.reais(it.toDouble())}." }
    partes += Traducao.fraseAutonomia(car)
    val recarga = Traducao.recarga(car)
    partes += recarga.emCasa
    partes += recarga.naEstrada
    partes += Traducao.frasePortaMalas(car)
    partes += Traducao.fraseGarantiaBateria(car)
    if (car.hibrido) {
        partes += "Este é um híbrido plug-in flex: além do motor elétrico, tem motor a combustão que roda com etanol ou gasolina."
    }
    return partes.joinToString(" ")
}
