package br.com.carrosverdes.domain

/** As três perguntas do atalho guiado: elas não criam outro mundo, só preenchem a busca. */
enum class Uso(val rotulo: String, val pergunta: String) {
    CIDADE("Principalmente na cidade", "Poucas viagens longas, uso diário em trechos curtos"),
    MISTO("Cidade e estrada", "Semana na cidade, estrada de vez em quando"),
    ESTRADA("Muito na estrada", "Viagens longas com frequência"),
}

enum class Prioridade(val rotulo: String, val ajuda: String) {
    ECONOMIA("Gastar pouco", "Conta de energia e preço de compra"),
    ESPACO("Levar a família", "Porta-malas e lugar para todos"),
    VIAGEM("Viajar tranquilo", "Autonomia e recarga rápida"),
    NENHUMA("Tanto faz", "O app escolhe a ordem pelo seu uso"),
}

data class PerfilGuiado(
    val orcamentoMax: Int? = null,
    val uso: Uso = Uso.MISTO,
    val prioridade: Prioridade = Prioridade.NENHUMA,
) {

    fun paraFiltros(): Filtros = Filtros(precoMax = orcamentoMax)

    fun ordem(): Ordem = when {
        prioridade == Prioridade.ECONOMIA -> Ordem.CUSTO
        prioridade == Prioridade.ESPACO -> Ordem.PORTA_MALAS
        prioridade == Prioridade.VIAGEM -> Ordem.AUTONOMIA
        uso == Uso.ESTRADA -> Ordem.AUTONOMIA
        else -> Ordem.CUSTO
    }

    fun resumo(): String = buildString {
        append(orcamentoMax?.let { "Até ${Traducao.reais(it.toDouble())}" } ?: "Qualquer preço")
        append(" · ")
        append(uso.rotulo)
        if (prioridade != Prioridade.NENHUMA) {
            append(" · ")
            append(prioridade.rotulo)
        }
    }
}
