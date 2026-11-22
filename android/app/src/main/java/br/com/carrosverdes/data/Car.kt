package br.com.carrosverdes.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Uma versão de carro, lida direto do JSON.
 *
 * O objeto guarda o JSON cru de propósito: assim um campo novo em `data/cars.json`
 * já chega ao app (ficha técnica, busca, cartão) sem precisar de código novo.
 * Só o que a lógica de decisão usa tem atalho tipado.
 */
class Car(val json: JsonObject) {

    private fun primitiva(chave: String): JsonPrimitive? {
        val valor = json[chave] as? JsonPrimitive ?: return null
        return if (valor is JsonNull) null else valor
    }

    fun tem(chave: String): Boolean = primitiva(chave) != null

    fun texto(chave: String): String? = primitiva(chave)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    fun numero(chave: String): Double? = texto(chave)?.replace(",", ".")?.toDoubleOrNull()

    fun inteiro(chave: String): Int? = numero(chave)?.toInt()

    fun simNao(chave: String): Boolean? = texto(chave)?.lowercase()?.let {
        when (it) {
            "true", "sim" -> true
            "false", "não", "nao" -> false
            else -> null
        }
    }

    fun lista(chave: String): List<String> =
        (json[chave] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.contentOrNull }
            ?.filter { it.isNotBlank() }
            .orEmpty()

    val chaves: Set<String> get() = json.keys

    val id: String
        get() = texto("id") ?: "${marca}|${modelo}|${versao}".lowercase().replace(" ", "-")

    val marca: String get() = texto("marca").orEmpty()
    val modelo: String get() = texto("modelo").orEmpty()
    val versao: String get() = texto("versao") ?: texto("versao_pesquisada").orEmpty()

    val nome: String get() = listOf(marca, modelo).filter { it.isNotEmpty() }.joinToString(" ")

    val preco: Int? get() = inteiro("preco_brl")
    val autonomia: Int? get() = inteiro("autonomia_referencia_km")
    val bateria: Double? get() = numero("bateria_kwh")
    val carroceria: String get() = texto("carroceria").orEmpty()
    val combustivel: String get() = texto("combustivel").orEmpty()

    val eletrico: Boolean get() = texto("motorizacao") != "PHEV"
    val hibrido: Boolean get() = !eletrico

    val confianca: String get() = texto("confianca") ?: "baixa"

    val fontes: List<String>
        get() = (listOfNotNull(texto("fonte_principal")) + lista("fontes")).distinct()

    val camposPreenchidos: Int? get() = inteiro("campos_preenchidos")

    override fun equals(other: Any?): Boolean = other is Car && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = nome
}

data class Fonte(val nome: String, val url: String, val licenca: String)

class CarsMeta(private val json: JsonObject) {

    val geradoEm: String = (json["gerado_em"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val precoReferencia: String = (json["preco_referencia"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val planilha: String = (json["planilha"] as? JsonPrimitive)?.contentOrNull.orEmpty()

    /** Ausente em bases antigas: 1 é o contrato original. */
    val schema: Int = (json["schema"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 1

    val fontes: List<Fonte> =
        (json["fontes_abertas"] as? JsonArray).orEmpty().mapNotNull { elemento ->
            val obj = elemento as? JsonObject ?: return@mapNotNull null
            fun campo(nome: String) = (obj[nome] as? JsonPrimitive)?.contentOrNull.orEmpty()
            Fonte(campo("nome"), campo("url"), campo("licenca"))
        }
}

class CarsFile(val meta: CarsMeta, val cars: List<Car>) {

    companion object {
        /** Versão de contrato que esta build do app sabe ler. */
        const val SCHEMA_SUPORTADO = 1

        private val parser = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(conteudo: String): CarsFile {
            val raiz = parser.parseToJsonElement(conteudo) as? JsonObject
                ?: error("A base de dados não é um objeto JSON.")
            val metaObj = raiz["meta"] as? JsonObject ?: JsonObject(emptyMap())
            val lista = raiz["cars"] as? JsonArray
                ?: error("A base de dados não tem a lista de carros.")

            val meta = CarsMeta(metaObj)
            if (meta.schema > SCHEMA_SUPORTADO) {
                error(
                    "Esta base usa o formato ${meta.schema} e este app entende até o " +
                        "$SCHEMA_SUPORTADO. Atualize o app na loja para receber os dados novos."
                )
            }

            val cars = lista.mapNotNull { it as? JsonObject }.map(::Car)
            if (cars.isEmpty()) error("A base de dados está vazia.")
            return CarsFile(meta, cars)
        }
    }
}
