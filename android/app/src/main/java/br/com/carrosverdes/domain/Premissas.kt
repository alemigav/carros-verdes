package br.com.carrosverdes.domain

/** Premissas de custo, em um lugar só. Aparecem na tela "Como calculamos". */
object Premissas {
    const val KM_ANO = 15_000
    const val PRECO_KWH_CASA = 0.95
    const val PRECO_KWH_PUBLICO = 1.80
    const val FRACAO_CASA = 0.80
    const val PRECO_GASOLINA = 6.10
    const val KM_POR_LITRO_GASOLINA = 10.0
    const val MANUTENCAO_ANO = 1_800.0

    /** Quanto dos quilômetros de um híbrido plug-in roda no modo elétrico. */
    const val FRACAO_ELETRICA_PHEV = 0.70

    /** Potência a partir da qual a recarga em casa exige carregador de parede. */
    const val AC_WALLBOX_KW = 7.4

    val precoKwhMisturado: Double
        get() = FRACAO_CASA * PRECO_KWH_CASA + (1 - FRACAO_CASA) * PRECO_KWH_PUBLICO

    val kmMes: Double get() = KM_ANO / 12.0

    /** Gasto mensal de um carro a gasolina que faz 10 km/L, para servir de comparação. */
    val custoGasolinaMes: Double
        get() = (kmMes / KM_POR_LITRO_GASOLINA) * PRECO_GASOLINA
}
