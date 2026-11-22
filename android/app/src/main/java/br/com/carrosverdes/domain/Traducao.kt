package br.com.carrosverdes.domain

import br.com.carrosverdes.data.Car
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** O gasto mensal de um carro, e o quanto dele é confiável. */
sealed interface CustoMensal {
    /** Energia (e combustível, no híbrido) mais manutenção, com todos os números publicados. */
    data class Completo(val reais: Double) : CustoMensal

    /** Estimativa incompleta: falta um dos consumos, então o valor é um piso. */
    data class Parcial(val reais: Double, val motivo: String) : CustoMensal

    data class Indisponivel(val motivo: String) : CustoMensal
}

/** Como é recarregar este carro, em trabalho e não em quilowatt. */
data class RecargaInfo(
    val emCasa: String,
    val naEstrada: String,
    val precisaObraEmCasa: Boolean?,
)

object Traducao {

    private val localeBR = Locale("pt", "BR")

    private val moedaInteira: NumberFormat =
        NumberFormat.getCurrencyInstance(localeBR).apply { maximumFractionDigits = 0 }

    private val moedaCentavos: NumberFormat =
        NumberFormat.getCurrencyInstance(localeBR).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }

    private val fmtInteiro: NumberFormat = NumberFormat.getIntegerInstance(localeBR)

    private val fmtDecimal: NumberFormat =
        NumberFormat.getNumberInstance(localeBR).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }

    fun reais(valor: Double): String = moedaInteira.format(valor)
    fun reaisComCentavos(valor: Double): String = moedaCentavos.format(valor)
    fun km(valor: Int): String = "${fmtInteiro.format(valor)} km"
    fun litros(valor: Double): String = "${fmtInteiro.format(valor.roundToInt())} L"
    fun potenciaCv(valor: Int): String = "${fmtInteiro.format(valor)} cv"
    fun numero1(valor: Double): String = fmtDecimal.format(valor)
    fun inteiro(valor: Double): String = fmtInteiro.format(valor)

    // ---------- consumo e custo ----------

    /** Consumo no modo elétrico, em kWh por 100 km. */
    fun consumoKwh100(car: Car): Double? =
        car.numero("consumo_calc_kwh_100km")
            ?: car.numero("eficiencia_km_kwh")?.takeIf { it > 0 }?.let { 100 / it }

    private fun custoEletrico100(car: Car): Double? {
        val kwh100 = consumoKwh100(car) ?: return null
        val base = kwh100 * Premissas.precoKwhMisturado
        return if (car.eletrico) base else base * Premissas.FRACAO_ELETRICA_PHEV
    }

    private fun custoCombustivel100(car: Car): Double? {
        if (car.eletrico) return null
        val consumo = car.numero("consumo_l_100km") ?: return null
        return consumo / 100 * Premissas.PRECO_GASOLINA * (1 - Premissas.FRACAO_ELETRICA_PHEV)
    }

    fun custoPor100km(car: Car): Double? {
        val eletrico = custoEletrico100(car) ?: return null
        if (car.eletrico) return eletrico
        val combustivel = custoCombustivel100(car) ?: return null
        return eletrico + combustivel
    }

    private fun manutencaoMes(): Double = Premissas.MANUTENCAO_ANO / 12

    fun custoMensal(car: Car): CustoMensal {
        val por100 = custoPor100km(car)
        if (por100 != null) {
            return CustoMensal.Completo(por100 / 100 * Premissas.kmMes + manutencaoMes())
        }

        // Híbrido plug-in sem consumo de combustível publicado: cobrar só a eletricidade
        // daria um número otimista demais. O valor vira piso, e a tela diz isso.
        val eletrico = custoEletrico100(car)
        if (car.hibrido && eletrico != null) {
            return CustoMensal.Parcial(
                reais = eletrico / 100 * Premissas.kmMes + manutencaoMes(),
                motivo = "Esta conta tem só a energia elétrica. O fabricante não publica o consumo de " +
                    "gasolina desta versão, então o gasto real é maior que este número.",
            )
        }

        return CustoMensal.Indisponivel(
            "Faltam os dados de consumo desta versão para estimar o gasto mensal.",
        )
    }

    /** Só existe quando o custo é completo: comparar com gasolina usando um piso seria enganoso. */
    fun economiaMensal(car: Car): Double? =
        (custoMensal(car) as? CustoMensal.Completo)
            ?.let { Premissas.custoGasolinaMes - it.reais }
            ?.takeIf { it > 0 }

    fun fraseEconomia(car: Car): String? {
        val economia = economiaMensal(car) ?: return null
        val gasolina = reais(Premissas.custoGasolinaMes)
        return "Num carro a gasolina que faz 10 km por litro, você gastaria cerca de $gasolina por mês. " +
            "Aqui sobram aproximadamente ${reais(economia)} por mês."
    }

    // ---------- autonomia ----------

    fun fraseAutonomia(car: Car): String {
        val autonomia = car.autonomia
            ?: return "O fabricante não publica a autonomia desta versão."
        return "${km(autonomia)} com uma carga."
    }

    // ---------- recarga ----------

    fun recarga(car: Car): RecargaInfo {
        val acKw = car.numero("recarga_ac_kw")
        val dcMin = car.numero("recarga_dc_10_80_min")
        val dcKw = car.numero("recarga_dc_kw")

        val emCasa: String
        val precisaObra: Boolean?
        when {
            acKw == null -> {
                emCasa = "O fabricante não publica a potência de recarga em casa nesta versão."
                precisaObra = null
            }
            acKw >= Premissas.AC_WALLBOX_KW -> {
                emCasa = "Na garagem, é preciso instalar um carregador de parede (wallbox): " +
                    "a tomada comum não dá conta dos ${numero1(acKw)} kW."
                precisaObra = true
            }
            else -> {
                emCasa = "Carrega numa tomada comum, durante a noite, sem precisar de obra."
                precisaObra = false
            }
        }

        val naEstrada = when {
            dcMin != null -> "Num posto rápido, ${fmtInteiro.format(dcMin.roundToInt())} minutos para ir de 10% a 80%."
            dcKw != null -> "Tem recarga rápida de ${fmtInteiro.format(dcKw.roundToInt())} kW, mas o tempo não foi publicado."
            else -> "Não tem recarga rápida publicada: viagem longa pede planejamento."
        }

        return RecargaInfo(emCasa, naEstrada, precisaObra)
    }

    // ---------- espaço, garantia ----------

    fun frasePortaMalas(car: Car): String {
        val litros = car.numero("porta_malas_l")
            ?: return "O fabricante não publica o porta-malas desta versão."
        val comentario = when {
            litros >= 500 -> "Espaço de sobra para bagagem de família."
            litros >= 350 -> "Cabe a mala de uma viagem de quatro pessoas."
            else -> "É compacto: bom para a cidade, apertado em viagem longa."
        }
        return "${litros(litros)} de porta-malas. $comentario"
    }

    fun fraseGarantiaBateria(car: Car): String {
        val anos = car.inteiro("garantia_bateria_anos")
            ?: return "O fabricante não publica a garantia da bateria desta versão."
        val kmGarantia = car.inteiro("garantia_bateria_km")
        val limite = if (kmGarantia != null) " ou ${km(kmGarantia)}" else ""
        return "Bateria garantida por $anos anos$limite."
    }

    // ---------- selo de origem do dado ----------

    fun confiancaEmPalavras(car: Car): String = when (car.confianca) {
        "alta" -> "Dados conferidos na fonte do fabricante"
        "media" -> "Dados parcialmente conferidos"
        else -> "Poucas fontes para esta versão"
    }

    fun precoEmPalavras(car: Car): String =
        car.preco?.let { reais(it.toDouble()) } ?: "Preço não publicado"

    /** Diferença de preço/autonomia entre versões do mesmo modelo, para o texto "o que muda". */
    fun diferencaDeVersao(atual: Car, outra: Car): String {
        val partes = mutableListOf<String>()
        val precoAtual = atual.preco
        val precoOutra = outra.preco
        if (precoAtual != null && precoOutra != null && precoAtual != precoOutra) {
            val delta = (precoOutra - precoAtual).toDouble()
            partes += if (abs(delta) < 1) {
                "mesmo preço"
            } else {
                "${if (delta > 0) "mais" else "menos"} ${reais(abs(delta))}"
            }
        }
        if (atual.autonomia != null && outra.autonomia != null && atual.autonomia != outra.autonomia) {
            val delta = (outra.autonomia!! - atual.autonomia!!)
            partes += "${if (delta > 0) "mais" else "menos"} ${km(abs(delta))} de autonomia"
        }
        if (atual.bateria != null && outra.bateria != null && atual.bateria != outra.bateria) {
            partes += "bateria de ${numero1(outra.bateria!!)} kWh"
        }
        return partes.joinToString(" e ").ifEmpty { "mesma ficha técnica" }
    }
}
