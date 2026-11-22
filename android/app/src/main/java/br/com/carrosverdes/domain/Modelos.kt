package br.com.carrosverdes.domain

import br.com.carrosverdes.data.Car

/**
 * Um carro à venda, com as suas versões.
 * A lista mostra modelos, não 92 versões soltas: "Dolphin Mini, a partir de R$ 119.990, 2 versões".
 */
class Modelo(val marca: String, val modelo: String, val versoes: List<Car>) {

    val id: String = "$marca|$modelo"
    val nome: String get() = "$marca $modelo"

    val eletrico: Boolean get() = versoes.any { it.eletrico }
    val hibrido: Boolean get() = versoes.any { it.hibrido }

    /** Versão que representa o modelo: a mais barata com preço publicado. */
    val ideal: Car = versoes.filter { it.preco != null }.minByOrNull { it.preco!! } ?: versoes.first()

    val precoMin: Int? = versoes.mapNotNull { it.preco }.minOrNull()
    val precoMax: Int? = versoes.mapNotNull { it.preco }.maxOrNull()

    val outrasVersoes: List<Car> get() = versoes.filter { it.id != ideal.id }

    /** Como a lista apresenta o preço: "a partir de" quando há mais de uma versão. */
    fun precoEmPalavras(): String = when {
        precoMin == null -> "Preço não publicado"
        versoes.size == 1 -> Traducao.reais(precoMin.toDouble())
        precoMin != precoMax -> "a partir de ${Traducao.reais(precoMin.toDouble())}"
        else -> Traducao.reais(precoMin.toDouble())
    }

    fun versoesEmPalavras(): String =
        if (versoes.size == 1) "1 versão" else "${versoes.size} versões"
}

fun agruparEmModelos(cars: List<Car>): List<Modelo> =
    cars.groupBy { "${it.marca}|${it.modelo}" }
        .map { (_, versoes) -> Modelo(versoes.first().marca, versoes.first().modelo, versoes) }
