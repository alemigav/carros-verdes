package br.com.carrosverdes.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class Foto(val arquivo: String, val autor: String, val licenca: String, val origem: String)

/**
 * Fotos ilustrativas do Wikimedia Commons, já reduzidas para WebP dentro do APK.
 * A licença CC exige o crédito, então ele anda junto com a imagem.
 */
class Fotos(private val context: Context) {

    private val mapa: Map<String, Foto> = runCatching {
        val texto = context.assets.open("photos.json").bufferedReader().use { it.readText() }
        val raiz = Json.parseToJsonElement(texto) as JsonObject
        raiz.mapNotNull { (modelo, elemento) ->
            val obj = elemento as? JsonObject ?: return@mapNotNull null
            fun campo(nome: String) = (obj[nome] as? JsonPrimitive)?.contentOrNull.orEmpty()
            modelo to Foto(campo("arquivo"), campo("autor"), campo("licenca"), campo("origem"))
        }.toMap()
    }.getOrDefault(emptyMap())

    private val cache = object : LinkedHashMap<String, ImageBitmap>(6, 0.75f, true) {
        override fun removeEldestEntry(maisAntigo: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 6
    }

    fun de(car: Car): Foto? = mapa["${car.marca} ${car.modelo}"]

    suspend fun carregar(foto: Foto): ImageBitmap? {
        synchronized(cache) { cache[foto.arquivo] }?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(foto.arquivo).use { fluxo ->
                    BitmapFactory.decodeStream(fluxo)?.asImageBitmap()
                }
            }.getOrNull()?.also { imagem ->
                synchronized(cache) { cache[foto.arquivo] = imagem }
            }
        }
    }
}
