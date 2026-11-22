package br.com.carrosverdes.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class SecaoDaLicao(val titulo: String, val paragrafos: List<String>)

class AcaoDaLicao(val rotulo: String, val filtro: String)

class Licao(
    val id: String,
    val tipo: String,
    val titulo: String,
    val resumo: String,
    val secoes: List<SecaoDaLicao>,
    val acao: AcaoDaLicao?,
)

/**
 * As lições são conteúdo, não código: ficam em `assets/aprender.json` e podem ser
 * reescritas, corrigidas ou ampliadas sem tocar no aplicativo.
 */
class Aprender(context: Context) {

    val licoes: List<Licao> = runCatching {
        val texto = context.assets.open("aprender.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val raiz = Json.parseToJsonElement(texto) as? JsonObject ?: return@runCatching emptyList()
        val lista = raiz["licoes"] as? JsonArray ?: return@runCatching emptyList()
        lista.mapNotNull { elemento ->
            val obj = elemento as? JsonObject ?: return@mapNotNull null
            fun texto(chave: String) = (obj[chave] as? JsonPrimitive)?.contentOrNull.orEmpty()

            val secoes = (obj["secoes"] as? JsonArray).orEmpty().mapNotNull { secaoElemento ->
                val secao = secaoElemento as? JsonObject ?: return@mapNotNull null
                SecaoDaLicao(
                    titulo = (secao["titulo"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    paragrafos = (secao["paragrafos"] as? JsonArray).orEmpty()
                        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        .filter { it.isNotBlank() },
                )
            }

            val acao = (obj["acao"] as? JsonObject)?.let { acaoObj ->
                AcaoDaLicao(
                    rotulo = (acaoObj["rotulo"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    filtro = (acaoObj["filtro"] as? JsonPrimitive)?.contentOrNull ?: "todos",
                )
            }

            Licao(
                id = texto("id"),
                tipo = texto("tipo"),
                titulo = texto("titulo"),
                resumo = texto("resumo"),
                secoes = secoes,
                acao = acao,
            )
        }
    }.getOrDefault(emptyList())

    fun porId(id: String): Licao? = licoes.firstOrNull { it.id == id }
}
