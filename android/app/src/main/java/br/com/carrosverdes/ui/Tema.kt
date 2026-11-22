package br.com.carrosverdes.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Cores {
    val Verde = Color(0xFF0D6B57)
    val VerdeEscuro = Color(0xFF07463A)
    val VerdeClaro = Color(0xFFE1F2EB)
    val Tinta = Color(0xFF10201C)
    val TintaSuave = Color(0xFF3D4F49)
    val Papel = Color(0xFFF4F7F5)
    val Papel2 = Color(0xFFFFFFFF)
    val Azul = Color(0xFF0B4FD1)
    val Alerta = Color(0xFF7A4B00)
    val AlertaFundo = Color(0xFFFFF3D6)
    val Borda = Color(0xFFCBD9D3)
}

object Tamanhos {
    /** Piso de toque da interface inteira. */
    val alvo = 64.dp
    val alvoGrande = 76.dp
    val espaco = 16.dp
    val espacoGrande = 24.dp
    val raio = 20.dp
}

/**
 * Tipografia com piso de 18 sp no texto secundário e 20 sp no corpo.
 * Todo tamanho é em sp de propósito: a escala de fonte do Android continua valendo
 * e o layout precisa aguentar até 200%.
 */
private val TipografiaCarrosVerdes = Typography(
    displayLarge = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 23.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 31.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 28.sp),
    bodySmall = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
)

/**
 * Tema claro fixo. Não há modo escuro no app: contraste alto e fundo claro
 * é o que funciona melhor para quem tem catarata ou baixa visão.
 */
@Composable
fun TemaCarrosVerdes(conteudo: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Cores.Verde,
            onPrimary = Color.White,
            primaryContainer = Cores.VerdeClaro,
            onPrimaryContainer = Cores.VerdeEscuro,
            secondary = Cores.Azul,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE3ECFF),
            onSecondaryContainer = Color(0xFF0A2E78),
            background = Cores.Papel,
            onBackground = Cores.Tinta,
            surface = Cores.Papel2,
            onSurface = Cores.Tinta,
            surfaceVariant = Cores.VerdeClaro,
            onSurfaceVariant = Cores.TintaSuave,
            outline = Cores.Borda,
            outlineVariant = Cores.Borda,
            error = Color(0xFF8C1D18),
            onError = Color.White,
        ),
        typography = TipografiaCarrosVerdes,
        content = conteudo,
    )
}
