package br.com.carrosverdes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.carrosverdes.domain.Traducao

/**
 * Botão principal: largura total, altura mínima de 64 dp, rótulo de 20 sp.
 * Nunca tem ícone solto nem ação escondida.
 */
@Composable
fun BotaoGrande(
    texto: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    secundario: Boolean = false,
    altura: Boolean = false,
) {
    val alturaMinima = if (altura) Tamanhos.alvoGrande else Tamanhos.alvo
    val forma = RoundedCornerShape(Tamanhos.raio)
    val descricao = texto

    if (secundario) {
        OutlinedButton(
            onClick = aoClicar,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = alturaMinima)
                .semantics { contentDescription = descricao },
            shape = forma,
            border = BorderStroke(2.dp, Cores.Verde),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cores.VerdeEscuro),
        ) {
            Text(texto, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    } else {
        Button(
            onClick = aoClicar,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = alturaMinima)
                .semantics { contentDescription = descricao },
            shape = forma,
            colors = ButtonDefaults.buttonColors(containerColor = Cores.Verde, contentColor = Color.White),
        ) {
            Text(texto, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

/** Botão de ouvir: sempre visível onde há texto para ler. */
@Composable
fun BotaoOuvir(modifier: Modifier = Modifier, textoParaLer: () -> String) {
    val voz = LocalVoz.current
    if (voz?.disponivel != true) return
    BotaoGrande(
        texto = "Ouvir em voz alta",
        aoClicar = { voz.falar(textoParaLer()) },
        modifier = modifier,
        secundario = true,
    )
}

@Composable
fun Cartao(
    modifier: Modifier = Modifier,
    cor: Color = Cores.Papel2,
    conteudo: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tamanhos.raio),
        colors = CardDefaults.cardColors(containerColor = cor),
        border = BorderStroke(1.dp, Cores.Borda),
    ) {
        Column(Modifier.padding(Tamanhos.espaco)) { conteudo() }
    }
}

/** Um dos três números grandes do cartão de resultado. */
@Composable
fun NumeroDestaque(rotulo: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = valor,
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            color = Cores.VerdeEscuro,
        )
        Text(
            text = rotulo,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = Cores.TintaSuave,
        )
    }
}

/** Aviso honesto: usado quando a conta está incompleta ou o dado não foi publicado. */
@Composable
fun Aviso(texto: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tamanhos.raio),
        colors = CardDefaults.cardColors(containerColor = Cores.AlertaFundo),
        border = BorderStroke(1.dp, Color(0xFFE3C489)),
    ) {
        Text(
            text = texto,
            modifier = Modifier.padding(Tamanhos.espaco),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = Cores.Alerta,
        )
    }
}

@Composable
fun Selo(texto: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(
            text = texto,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = Cores.VerdeEscuro,
        )
    }
}

/** Texto do custo mensal, com a ressalva quando a conta é parcial. */
fun custoEmTexto(calculo: br.com.carrosverdes.domain.CustoMensal): String = when (calculo) {
    is br.com.carrosverdes.domain.CustoMensal.Completo -> Traducao.reais(calculo.reais)
    is br.com.carrosverdes.domain.CustoMensal.Parcial -> Traducao.reais(calculo.reais)
    is br.com.carrosverdes.domain.CustoMensal.Indisponivel -> "Não estimado"
}
