package br.com.carrosverdes.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.carrosverdes.data.Car
import br.com.carrosverdes.data.Fotos

/**
 * Foto ilustrativa com o crédito obrigatório da licença Creative Commons.
 * Sem foto licenciada, o app não inventa imagem: mostra o nome do carro em letra grande.
 */
@Composable
fun FotoDoCarro(fotos: Fotos, car: Car, modifier: Modifier = Modifier) {
    val foto = fotos.de(car)
    var imagem by remember(foto?.arquivo) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(foto?.arquivo) {
        imagem = foto?.let { fotos.carregar(it) }
    }

    Column(modifier) {
        Surface(
            shape = RoundedCornerShape(Tamanhos.raio),
            color = Cores.VerdeClaro,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.7f)
                .clip(RoundedCornerShape(Tamanhos.raio)),
        ) {
            val atual = imagem
            if (atual != null) {
                Image(
                    bitmap = atual,
                    contentDescription = "Foto ilustrativa do ${car.nome}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = car.nome,
                        style = MaterialTheme.typography.titleLarge,
                        color = Cores.VerdeEscuro,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(Tamanhos.espaco),
                    )
                }
            }
        }
        if (foto != null) {
            Text(
                text = "Foto ilustrativa: ${foto.autor} · ${foto.licenca}",
                style = MaterialTheme.typography.labelSmall,
                color = Cores.TintaSuave,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
