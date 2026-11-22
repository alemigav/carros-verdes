package br.com.carrosverdes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.carrosverdes.data.AcaoDaLicao
import br.com.carrosverdes.data.Licao

/** Hub das lições: título, resumo e tipo de cada uma. */
@Composable
fun AprenderScreen(
    licoes: List<Licao>,
    aoAbrir: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Tamanhos.espacoGrande),
        contentPadding = PaddingValues(vertical = Tamanhos.espaco),
        verticalArrangement = Arrangement.spacedBy(Tamanhos.espaco),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Entender antes de comprar", style = MaterialTheme.typography.headlineLarge, color = Cores.Tinta)
                Text(
                    "${licoes.size} lições curtas, em português de gente. Cada uma termina em um botão " +
                        "que já leva aos carros de que ela fala.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.TintaSuave,
                )
            }
        }

        items(licoes, key = { it.id }) { licao ->
            Card(
                onClick = { aoAbrir(licao.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Tamanhos.alvoGrande),
                shape = RoundedCornerShape(Tamanhos.raio),
                colors = CardDefaults.cardColors(containerColor = Cores.Papel2),
                border = BorderStroke(1.dp, Cores.Borda),
            ) {
                Row(
                    modifier = Modifier.padding(Tamanhos.espaco),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(licao.tipo, style = MaterialTheme.typography.labelSmall, color = Cores.Azul)
                        Text(licao.titulo, style = MaterialTheme.typography.titleLarge, color = Cores.VerdeEscuro)
                        Text(licao.resumo, style = MaterialTheme.typography.bodySmall, color = Cores.TintaSuave)
                    }
                    Text("›", style = MaterialTheme.typography.headlineLarge, color = Cores.Azul)
                }
            }
        }
    }
}

/** Uma lição: texto grande, seções curtas e uma ação concreta no fim. */
@Composable
fun LicaoScreen(
    licao: Licao,
    aoAplicarAcao: (AcaoDaLicao) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tamanhos.espacoGrande, vertical = Tamanhos.espaco),
        verticalArrangement = Arrangement.spacedBy(Tamanhos.espaco),
    ) {
        Text(licao.tipo, style = MaterialTheme.typography.labelMedium, color = Cores.Azul)
        Text(licao.titulo, style = MaterialTheme.typography.headlineLarge, color = Cores.Tinta)
        Text(licao.resumo, style = MaterialTheme.typography.bodyLarge, color = Cores.TintaSuave)

        BotaoOuvir {
            buildString {
                append("${licao.titulo}. ${licao.resumo} ")
                licao.secoes.forEach { secao ->
                    append("${secao.titulo}. ")
                    secao.paragrafos.forEach { append("$it ") }
                }
            }
        }

        HorizontalDivider(color = Cores.Borda)

        licao.secoes.forEach { secao ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(secao.titulo, style = MaterialTheme.typography.titleLarge, color = Cores.VerdeEscuro)
                secao.paragrafos.forEach { paragrafo ->
                    Text(paragrafo, style = MaterialTheme.typography.bodyLarge, color = Cores.Tinta)
                }
            }
        }

        licao.acao?.let { acao ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Tamanhos.raio),
                colors = CardDefaults.cardColors(containerColor = Cores.VerdeClaro),
                border = BorderStroke(1.dp, Cores.Borda),
            ) {
                Column(Modifier.padding(Tamanhos.espaco), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "O que fazer com isso",
                        style = MaterialTheme.typography.titleMedium,
                        color = Cores.VerdeEscuro,
                    )
                    BotaoGrande(texto = acao.rotulo, aoClicar = { aoAplicarAcao(acao) })
                }
            }
        }
    }
}
