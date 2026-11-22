package br.com.carrosverdes.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** As quatro portas permanentes do app. */
enum class Aba(val rotulo: String, val icone: ImageVector) {
    INICIO("Início", Icons.Filled.Home),
    CARROS("Carros", Icons.Filled.Search),
    ENTENDER("Entender", Icons.AutoMirrored.Filled.List),
    SOBRE("Sobre", Icons.Filled.Info),
}

/**
 * Barra que nunca sobe: as quatro áreas do app ficam sempre ao alcance do polegar,
 * sem precisar rolar até o fim da tela.
 */
@Composable
fun BarraInferior(atual: Aba, aoEscolher: (Aba) -> Unit) {
    NavigationBar(containerColor = Cores.Papel2, tonalElevation = 0.dp) {
        Aba.entries.forEach { aba ->
            NavigationBarItem(
                selected = aba == atual,
                onClick = { aoEscolher(aba) },
                icon = { Icon(aba.icone, contentDescription = null) },
                label = { Text(aba.rotulo, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Cores.VerdeEscuro,
                    selectedTextColor = Cores.VerdeEscuro,
                    indicatorColor = Cores.VerdeClaro,
                    unselectedIconColor = Cores.TintaSuave,
                    unselectedTextColor = Cores.TintaSuave,
                ),
            )
        }
    }
}

/**
 * Cabeçalho fixo das telas de detalhe, com o voltar sempre no mesmo lugar,
 * em vez de um botão que só aparece depois de rolar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraTopo(titulo: String, aoVoltar: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleLarge,
                color = Cores.Tinta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = aoVoltar, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Cores.VerdeEscuro,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Cores.Papel),
    )
}
