package br.com.carrosverdes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Altura generosa para o dedo: etiquetas e chips são secundários, mas continuam grandes. */
private val ALTURA_CHIP = 48.dp

@Composable
fun Chip(
    texto: String,
    selecionado: Boolean,
    modifier: Modifier = Modifier,
    aoClicar: () -> Unit,
) {
    Surface(
        onClick = aoClicar,
        modifier = modifier.heightIn(min = ALTURA_CHIP),
        shape = RoundedCornerShape(28.dp),
        color = if (selecionado) Cores.VerdeClaro else Cores.Papel2,
        contentColor = if (selecionado) Cores.VerdeEscuro else Cores.Tinta,
        border = BorderStroke(if (selecionado) 2.dp else 1.dp, if (selecionado) Cores.Verde else Cores.Borda),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Text(texto, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun EtiquetaRemovivel(texto: String, aoRemover: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = aoRemover,
        modifier = modifier.heightIn(min = ALTURA_CHIP),
        shape = RoundedCornerShape(28.dp),
        color = Cores.AlertaFundo,
        contentColor = Cores.Alerta,
        border = BorderStroke(1.dp, Color(0xFFE3C489)),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.padding(horizontal = 18.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Text("$texto  ×", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Fileira de chips que quebra em linhas: nunca rola para o lado. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileiraDeChips(modifier: Modifier = Modifier, conteudo: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        conteudo()
    }
}
