package br.com.carrosverdes.domain

/**
 * Ordenações objetivas, escritas na tela com o nome do critério.
 * Nada de "nota 87": a pessoa precisa poder conferir de onde veio a ordem.
 */
enum class Ordem(val rotulo: String, val curto: String, val grupo: String) {
    // pares inversos ficam lado a lado, agrupados pelo assunto do critério
    PRECO("Menor preço", "menor preço", "Preço e custo"),
    MAIOR_PRECO("Maior preço", "maior preço", "Preço e custo"),
    CUSTO("Menor custo por km", "menor custo por km", "Preço e custo"),
    CONSUMO("Menor consumo de energia", "menor consumo", "Preço e custo"),

    AUTONOMIA("Maior autonomia", "maior autonomia", "Autonomia e recarga"),
    BATERIA("Maior bateria", "maior bateria", "Autonomia e recarga"),
    RECARGA("Recarga mais rápida", "recarga mais rápida", "Autonomia e recarga"),

    POTENCIA("Mais potência", "mais potência", "Motor e espaço"),
    PORTA_MALAS("Maior porta-malas", "maior porta-malas", "Motor e espaço"),
    LUGARES("Mais lugares", "mais lugares", "Motor e espaço"),

    GARANTIA("Maior garantia de bateria", "maior garantia", "Garantia"),
}

object Ordenacao {

    fun aplicar(modelos: List<Modelo>, ordem: Ordem): List<Modelo> = when (ordem) {
        Ordem.CUSTO -> modelos.sortedWith(menorPrimeiro { Traducao.custoPor100km(it.ideal) })
        Ordem.PRECO -> modelos.sortedWith(menorPrimeiro { it.ideal.preco?.toDouble() })
        Ordem.AUTONOMIA -> modelos.sortedWith(maiorPrimeiro { it.ideal.autonomia?.toDouble() })
        Ordem.PORTA_MALAS -> modelos.sortedWith(maiorPrimeiro { it.ideal.numero("porta_malas_l") })
        Ordem.RECARGA -> modelos.sortedWith(maiorPrimeiro { it.ideal.numero("recarga_dc_kw") })
        Ordem.POTENCIA -> modelos.sortedWith(
            maiorPrimeiro { it.ideal.numero("potencia_cv") ?: it.ideal.numero("potencia_combinada_cv") },
        )
        Ordem.MAIOR_PRECO -> modelos.sortedWith(maiorPrimeiro { it.ideal.preco?.toDouble() })
        Ordem.BATERIA -> modelos.sortedWith(maiorPrimeiro { it.ideal.bateria })
        Ordem.CONSUMO -> modelos.sortedWith(menorPrimeiro { Traducao.consumoKwh100(it.ideal) })
        Ordem.LUGARES -> modelos.sortedWith(
            maiorPrimeiro { it.ideal.inteiro("lugares")?.toDouble() },
        )
        Ordem.GARANTIA -> modelos.sortedWith(
            maiorPrimeiro { it.ideal.inteiro("garantia_bateria_anos")?.toDouble() },
        )
    }

    /** Selo do cartão: "3º em menor custo por km". */
    fun posicao(indice: Int, ordem: Ordem): String = "${indice + 1}º em ${ordem.curto}"

    private fun menorPrimeiro(valor: (Modelo) -> Double?) =
        compareBy<Modelo> { valor(it) ?: Double.MAX_VALUE }.thenBy { it.nome }

    private fun maiorPrimeiro(valor: (Modelo) -> Double?) =
        compareByDescending<Modelo> { valor(it) ?: -1.0 }.thenBy { it.nome }
}
