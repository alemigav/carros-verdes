package br.com.carrosverdes.domain

import br.com.carrosverdes.data.Car
import java.text.Normalizer

enum class Motorizacao(val rotulo: String) {
    TODOS("Todos"),
    ELETRICO("Elétrico"),
    HIBRIDO("Híbrido flex"),
}

enum class RecargaCasa(val rotulo: String) {
    TODOS("Tanto faz"),
    TOMADA("Tomada comum"),
    WALLBOX("Carregador de parede"),
}

enum class Equipamento(val rotulo: String, val chave: String) {
    ACC("Piloto adaptativo", "acc"),
    AEB("Freagem automática", "aeb"),
    LKA("Centralização de faixa", "lka"),
    LDW("Aviso de saída de faixa", "ldw"),
    BSM("Alerta de ponto cego", "bsm"),
    RCTA("Tráfego cruzado traseiro", "rcta"),
    TSR("Leitura de placas", "tsr"),
    CAMERA_360("Câmera 360°", "camera_360"),
    CAMERA_RE("Câmera de ré", "camera_re"),
    SENSOR_ESTACIONAMENTO("Sensor de estacionamento", "sensor_estacionamento"),
    V2L("Tomada no carro", "v2l"),
    BOMBA_CALOR("Bomba de calor", "bomba_de_calor"),
    TETO("Teto panorâmico", "teto_panoramico"),
    BANCOS_VENTILADOS("Bancos ventilados", "bancos_ventilados"),
    BANCOS_AQUECIDOS("Bancos aquecidos", "bancos_aquecidos"),
    CARREGADOR_SEM_FIO("Carregador por indução", "carregador_sem_fio"),
    CARPLAY("Apple CarPlay", "carplay"),
    ANDROID_AUTO("Android Auto", "android_auto"),
    HEAD_UP("Projeção no para-brisa", "head_up_display"),
}

/** Rótulos de carroceria usados nos filtros e nos cartões. */
val CARROCERIAS: List<Pair<String, String>> = listOf(
    "suv" to "SUV",
    "hatch" to "Hatch",
    "sedan" to "Sedan",
    "picape" to "Picape",
    "minivan" to "Minivan",
    "perua" to "Perua",
    "cupe" to "Cupê",
)

fun rotuloCarroceria(chave: String): String =
    CARROCERIAS.firstOrNull { it.first == chave }?.second ?: chave.replaceFirstChar { it.uppercase() }

/** Uma etiqueta removível no topo da lista: o filtro ativo e como desligá-lo. */
data class Etiqueta(val texto: String, val remover: (Filtros) -> Filtros)

/**
 * Todo o cenário de filtragem em um objeto imutável.
 * As listas de opções (marcas, carrocerias, trações) saem dos próprios dados,
 * então carro novo com marca nova aparece no filtro sem código.
 */
data class Filtros(
    val busca: String = "",
    val precoMin: Int? = null,
    val precoMax: Int? = null,
    val motorizacao: Motorizacao = Motorizacao.TODOS,
    val autonomiaMin: Int? = null,
    val bateriaMin: Double? = null,
    val recargaDcMin: Int? = null,
    val recargaCasa: RecargaCasa = RecargaCasa.TODOS,
    val carrocerias: Set<String> = emptySet(),
    val marcas: Set<String> = emptySet(),
    val tracoes: Set<String> = emptySet(),
    val equipamentos: Set<Equipamento> = emptySet(),
    val lugaresMin: Int? = null,
    val portaMalasMin: Int? = null,
    val potenciaMin: Int? = null,
) {

    val temAlgum: Boolean
        get() = busca.isNotBlank() || precoMax != null || precoMin != null || motorizacao != Motorizacao.TODOS ||
            autonomiaMin != null || bateriaMin != null || recargaDcMin != null ||
            recargaCasa != RecargaCasa.TODOS ||
            carrocerias.isNotEmpty() || marcas.isNotEmpty() || tracoes.isNotEmpty() ||
            equipamentos.isNotEmpty() || lugaresMin != null || portaMalasMin != null || potenciaMin != null

    fun aplicar(cars: List<Car>): List<Car> = cars.filter { car ->
        val alvo = semAcento(busca.trim())
        val atendeBusca = alvo.isEmpty() ||
            semAcento("${car.marca} ${car.modelo} ${car.versao}").contains(alvo)

        val atendeMotorizacao = when (motorizacao) {
            Motorizacao.TODOS -> true
            Motorizacao.ELETRICO -> car.eletrico
            Motorizacao.HIBRIDO -> car.hibrido
        }

        atendeBusca &&
            atendeMotorizacao &&
            (precoMax == null || (car.preco != null && car.preco!! <= precoMax)) &&
            (precoMin == null || (car.preco != null && car.preco!! >= precoMin)) &&
            (autonomiaMin == null || (car.autonomia != null && car.autonomia!! >= autonomiaMin)) &&
            (bateriaMin == null || (car.bateria != null && car.bateria!! >= bateriaMin)) &&
            (recargaDcMin == null || ((car.numero("recarga_dc_kw") ?: 0.0) >= recargaDcMin)) &&
            when (recargaCasa) {
                RecargaCasa.TODOS -> true
                RecargaCasa.TOMADA -> (car.numero("recarga_ac_kw") ?: 99.0) < 7.4
                RecargaCasa.WALLBOX -> (car.numero("recarga_ac_kw") ?: 0.0) >= 7.4
            } &&
            (carrocerias.isEmpty() || car.carroceria in carrocerias) &&
            (marcas.isEmpty() || car.marca in marcas) &&
            (tracoes.isEmpty() || (car.texto("tracao") ?: "não publicado") in tracoes) &&
            equipamentos.all { car.simNao(it.chave) == true } &&
            (lugaresMin == null || ((car.inteiro("lugares") ?: 0) >= lugaresMin)) &&
            (portaMalasMin == null || ((car.numero("porta_malas_l") ?: 0.0) >= portaMalasMin)) &&
            (potenciaMin == null || ((car.numero("potencia_cv") ?: car.numero("potencia_combinada_cv") ?: 0.0) >= potenciaMin))
    }

    fun etiquetas(): List<Etiqueta> = buildList {
        if (busca.isNotBlank()) add(Etiqueta("Busca: $busca") { it.copy(busca = "") })
        precoMax?.let { teto -> add(Etiqueta("Até ${Traducao.reais(teto.toDouble())}") { it.copy(precoMax = null) }) }
        precoMin?.let { piso -> add(Etiqueta("A partir de ${Traducao.reais(piso.toDouble())}") { it.copy(precoMin = null) }) }
        potenciaMin?.let { piso -> add(Etiqueta("A partir de $piso cv") { it.copy(potenciaMin = null) }) }
        if (motorizacao != Motorizacao.TODOS) {
            add(Etiqueta(motorizacao.rotulo) { it.copy(motorizacao = Motorizacao.TODOS) })
        }
        autonomiaMin?.let { minimo -> add(Etiqueta("Autonomia a partir de ${Traducao.km(minimo)}") { it.copy(autonomiaMin = null) }) }
        bateriaMin?.let { minimo -> add(Etiqueta("Bateria a partir de ${Traducao.numero1(minimo)} kWh") { it.copy(bateriaMin = null) }) }
        recargaDcMin?.let { minimo -> add(Etiqueta("Recarga rápida a partir de $minimo kW") { it.copy(recargaDcMin = null) }) }
        if (recargaCasa != RecargaCasa.TODOS) {
            add(Etiqueta("Carrega em ${recargaCasa.rotulo.lowercase()}") { it.copy(recargaCasa = RecargaCasa.TODOS) })
        }
        carrocerias.sorted().forEach { chave ->
            add(Etiqueta(rotuloCarroceria(chave)) { it.copy(carrocerias = it.carrocerias - chave) })
        }
        marcas.sorted().forEach { marca ->
            add(Etiqueta(marca) { it.copy(marcas = it.marcas - marca) })
        }
        tracoes.sorted().forEach { tracao ->
            add(Etiqueta(tracao) { it.copy(tracoes = it.tracoes - tracao) })
        }
        equipamentos.sortedBy { it.rotulo }.forEach { equipamento ->
            add(Etiqueta(equipamento.rotulo) { it.copy(equipamentos = it.equipamentos - equipamento) })
        }
        lugaresMin?.let { minimo -> add(Etiqueta("$minimo lugares ou mais") { it.copy(lugaresMin = null) }) }
        portaMalasMin?.let { minimo -> add(Etiqueta("Porta-malas a partir de ${Traducao.litros(minimo.toDouble())}") { it.copy(portaMalasMin = null) }) }
    }

    fun limpar(): Filtros = Filtros()
}

private fun semAcento(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase()

/** Marcas, carrocerias e trações que existem de fato na base. */
class OpcoesDeFiltro(cars: List<Car>) {
    val marcas: List<String> = cars.map { it.marca }.filter { it.isNotBlank() }.distinct().sorted()
    val carrocerias: List<String> = cars.map { it.carroceria }.filter { it.isNotBlank() }.distinct()
        .sortedBy { rotuloCarroceria(it) }
    val tracoes: List<String> = cars.map { it.texto("tracao") ?: "não publicado" }.distinct().sorted()
}
