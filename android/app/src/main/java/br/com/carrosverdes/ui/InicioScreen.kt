package br.com.carrosverdes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.carrosverdes.R

/**
 * Tela inicial de direcionamento. A composição é compacta de propósito: a marca, as
 * boas-vindas e os três caminhos cabem na tela sem rolar em um celular comum.
 * A rolagem continua existindo como rede de segurança para fonte do sistema grande.
 */
@Composable
fun InicioScreen(
    totalCarros: Int,
    totalLicoes: Int,
    aoEscolherGuia: () -> Unit,
    aoAprender: () -> Unit,
    aoVerLista: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tamanhos.espacoGrande, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(Tamanhos.raio),
            color = Cores.VerdeEscuro,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(shape = CircleShape, color = Cores.Papel2, modifier = Modifier.size(88.dp)) {
                    Image(
                        painter = painterResource(R.drawable.marca_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp),
                    )
                }
                Text(
                    text = "Carros Verdes",
                    style = MaterialTheme.typography.displayMedium,
                    color = Cores.Papel2,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "$totalCarros versões · elétricos e híbridos flex",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.VerdeClaro,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Bem-vindo",
                style = MaterialTheme.typography.titleLarge,
                color = Cores.VerdeEscuro,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Escolha um carro elétrico ou híbrido plug-in flex sem entender de bateria: " +
                    "o app mostra o custo por mês e quantos quilômetros o carro anda com uma carga.",
                style = MaterialTheme.typography.bodySmall,
                color = Cores.TintaSuave,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ItemDeMenu(
                titulo = "Não sei por onde começar",
                descricao = "Três perguntas rápidas e a lista já sai filtrada.",
                aoClicar = aoEscolherGuia,
            )

            ItemDeMenu(
                titulo = "Entender antes de comprar",
                descricao = "$totalLicoes lições curtas: kWh, autonomia, recarga e custo.",
                aoClicar = aoAprender,
            )

            ItemDeMenu(
                titulo = "Ver e comparar os carros",
                descricao = "Filtros e comparação lado a lado das $totalCarros versões.",
                aoClicar = aoVerLista,
            )
        }
    }
}

@Composable
private fun ItemDeMenu(titulo: String, descricao: String, aoClicar: () -> Unit) {
    Card(
        onClick = aoClicar,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp),
        shape = RoundedCornerShape(Tamanhos.raio),
        colors = CardDefaults.cardColors(containerColor = Cores.Papel2),
        border = BorderStroke(1.dp, Cores.Borda),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, color = Cores.VerdeEscuro)
                Text(descricao, style = MaterialTheme.typography.bodySmall, color = Cores.TintaSuave)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = Cores.Azul)
        }
    }
}
