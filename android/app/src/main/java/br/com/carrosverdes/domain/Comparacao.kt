package br.com.carrosverdes.domain

import br.com.carrosverdes.data.Car

enum class GrupoComparacao(val titulo: String) {
    CUSTO("Preço e custo"),
    BATERIA("Bateria e recarga"),
    ESPACO("Espaço e motor"),
    SEGURANCA("Segurança"),
    GARANTIA("Garantia"),
}

class LinhaComparada(
    val grupo: GrupoComparacao,
    val rotulo: String,
    val valores: List<String?>,
    /** Índices das colunas com o melhor valor da linha. */
    val melhores: Set<Int>,
)

/**
 * Comparação de 2 a 4 carros, linha a linha, com o melhor valor de cada linha marcado.
 * Só destaca quando há diferença real: empatado, ninguém é "melhor".
 */
object Comparacao {

    private class Criterio(
        val grupo: GrupoComparacao,
        val rotulo: String,
        val valor: (Car) -> String?,
        val numero: ((Car) -> Double?)? = null,
        val maiorEhMelhor: Boolean = true,
    )

    private val CRITERIOS = listOf(
        Criterio(GrupoComparacao.CUSTO, "Preço de tabela",
            { car -> car.preco?.let { Traducao.reais(it.toDouble()) } },
            { car -> car.preco?.toDouble() }, maiorEhMelhor = false),
        Criterio(GrupoComparacao.CUSTO, "Custo por km",
            { car -> Traducao.custoPor100km(car)?.let { Traducao.reaisComCentavos(it / 100) } },
            { car -> Traducao.custoPor100km(car) }, maiorEhMelhor = false),
        Criterio(GrupoComparacao.CUSTO, "Custo por mês",
            { car ->
                when (val calculo = Traducao.custoMensal(car)) {
                    is CustoMensal.Completo -> Traducao.reais(calculo.reais)
                    is CustoMensal.Parcial -> "${Traducao.reais(calculo.reais)} (só energia)"
                    is CustoMensal.Indisponivel -> null
                }
            },
            { car -> (Traducao.custoMensal(car) as? CustoMensal.Completo)?.reais }, maiorEhMelhor = false),

        Criterio(GrupoComparacao.BATERIA, "Autonomia",
            { car -> car.autonomia?.let { Traducao.km(it) } },
            { car -> car.autonomia?.toDouble() }),
        Criterio(GrupoComparacao.BATERIA, "Bateria",
            { car -> car.bateria?.let { "${Traducao.numero1(it)} kWh" } },
            { car -> car.bateria }),
        Criterio(GrupoComparacao.BATERIA, "Recarga rápida",
            { car -> car.numero("recarga_dc_kw")?.let { "${Traducao.inteiro(it)} kW" } },
            { car -> car.numero("recarga_dc_kw") }),
        Criterio(GrupoComparacao.BATERIA, "Recarga de 10% a 80%",
            { car -> car.numero("recarga_dc_10_80_min")?.let { "${Traducao.inteiro(it)} min" } },
            { car -> car.numero("recarga_dc_10_80_min") }, maiorEhMelhor = false),
        Criterio(GrupoComparacao.BATERIA, "Consumo de energia",
            { car -> Traducao.consumoKwh100(car)?.let { "${Traducao.numero1(it)} kWh/100 km" } },
            { car -> Traducao.consumoKwh100(car) }, maiorEhMelhor = false),

        Criterio(GrupoComparacao.ESPACO, "Porta-malas",
            { car -> car.numero("porta_malas_l")?.let { Traducao.litros(it) } },
            { car -> car.numero("porta_malas_l") }),
        Criterio(GrupoComparacao.ESPACO, "Lugares",
            { car -> car.inteiro("lugares")?.toString() },
            { car -> car.inteiro("lugares")?.toDouble() }),
        Criterio(GrupoComparacao.ESPACO, "Potência",
            { car ->
                val potencia = car.numero("potencia_cv") ?: car.numero("potencia_combinada_cv")
                potencia?.let { Traducao.potenciaCv(it.toInt()) }
            },
            { car -> car.numero("potencia_cv") ?: car.numero("potencia_combinada_cv") }),
        Criterio(GrupoComparacao.ESPACO, "Tração", { car -> car.texto("tracao") }),

        Criterio(GrupoComparacao.SEGURANCA, "Airbags",
            { car -> car.inteiro("airbags")?.toString() },
            { car -> car.inteiro("airbags")?.toDouble() }),
        Criterio(GrupoComparacao.SEGURANCA, "Piloto adaptativo (ACC)",
            { car -> simNao(car, "acc") }, { car -> simNaoNumero(car, "acc") }),
        Criterio(GrupoComparacao.SEGURANCA, "Freagem automática (AEB)",
            { car -> simNao(car, "aeb") }, { car -> simNaoNumero(car, "aeb") }),
        Criterio(GrupoComparacao.SEGURANCA, "Câmera 360°",
            { car -> simNao(car, "camera_360") }, { car -> simNaoNumero(car, "camera_360") }),

        Criterio(GrupoComparacao.GARANTIA, "Garantia da bateria",
            { car -> car.inteiro("garantia_bateria_anos")?.let { "$it anos" } },
            { car -> car.inteiro("garantia_bateria_anos")?.toDouble() }),
        Criterio(GrupoComparacao.GARANTIA, "Garantia do carro",
            { car -> car.inteiro("garantia_veiculo_anos")?.let { "$it anos" } },
            { car -> car.inteiro("garantia_veiculo_anos")?.toDouble() }),
    )

    fun linhas(cars: List<Car>, somenteDiferencas: Boolean): List<LinhaComparada> =
        CRITERIOS.mapNotNull { criterio ->
            val valores = cars.map { criterio.valor(it) }
            if (somenteDiferencas && valores.distinct().size <= 1) return@mapNotNull null

            val melhores = melhores(cars, criterio)
            LinhaComparada(criterio.grupo, criterio.rotulo, valores, melhores)
        }

    private fun melhores(cars: List<Car>, criterio: Criterio): Set<Int> {
        val numeros = cars.map { criterio.numero?.invoke(it) }
        val validos = numeros.mapIndexedNotNull { indice, valor -> valor?.let { indice to it } }
        if (validos.size < 2) return emptySet()

        val extremo = if (criterio.maiorEhMelhor) validos.maxOf { it.second } else validos.minOf { it.second }
        val empatados = validos.filter { it.second == extremo }.map { it.first }

        // Empate entre todos não é vantagem de ninguém.
        if (empatados.size == validos.size) return emptySet()
        return empatados.toSet()
    }

    private fun simNao(car: Car, chave: String): String? =
        car.simNao(chave)?.let { if (it) "Sim" else "Não" }

    private fun simNaoNumero(car: Car, chave: String): Double? =
        car.simNao(chave)?.let { if (it) 1.0 else 0.0 }
}
