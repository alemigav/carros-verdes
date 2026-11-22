package br.com.carrosverdes

import br.com.carrosverdes.data.CarsFile
import br.com.carrosverdes.domain.CampoCatalogo
import br.com.carrosverdes.domain.CustoMensal
import br.com.carrosverdes.domain.Grupo
import br.com.carrosverdes.domain.Comparacao
import br.com.carrosverdes.domain.Equipamento
import br.com.carrosverdes.domain.Filtros
import br.com.carrosverdes.domain.Motorizacao
import br.com.carrosverdes.domain.OpcoesDeFiltro
import br.com.carrosverdes.domain.Ordem
import br.com.carrosverdes.domain.Ordenacao
import br.com.carrosverdes.domain.Premissas
import br.com.carrosverdes.domain.Traducao
import br.com.carrosverdes.domain.agruparEmModelos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Os testes rodam contra o `data/cars.json` de verdade, não contra uma base de mentira.
 * O objetivo é falhar quando o dado real mudar de forma que quebre o app.
 */
class BaseRealTest {

    private val base: CarsFile by lazy { CarsFile.parse(lerBaseReal()) }

    private fun lerBaseReal(): String {
        val caminhos = listOf("../../data/cars.json", "../data/cars.json", "data/cars.json")
        val arquivo = caminhos.map(::File).firstOrNull { it.exists() }
            ?: error("Não encontrei data/cars.json a partir de ${File(".").absolutePath}")
        return arquivo.readText()
    }

    @Test
    fun `a base real carrega com todas as versoes`() {
        assertTrue("esperava pelo menos 90 versões", base.cars.size >= 90)
        assertTrue("a base precisa ter as duas motorizações",
            base.cars.any { it.eletrico } && base.cars.any { it.hibrido })
    }

    @Test
    fun `todo carro tem identificacao e nome`() {
        base.cars.forEach { car ->
            assertTrue("carro sem marca: ${car.json}", car.marca.isNotEmpty())
            assertTrue("carro sem modelo: ${car.json}", car.modelo.isNotEmpty())
            assertTrue("carro sem id: ${car.json}", car.id.isNotEmpty())
        }
    }

    @Test
    fun `ids sao unicos`() {
        val ids = base.cars.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    /** Regressão: híbrido sem consumo de combustível não pode receber custo "completo". */
    @Test
    fun `hibrido sem consumo de combustivel tem custo parcial e nunca economia`() {
        val hibridosSemConsumo = base.cars.filter { it.hibrido && it.numero("consumo_l_100km") == null }
        assertTrue("a base deveria ter híbridos sem consumo de combustível", hibridosSemConsumo.isNotEmpty())

        hibridosSemConsumo.forEach { car ->
            val custo = Traducao.custoMensal(car)
            assertTrue("${car.nome} deveria ter custo parcial, veio $custo", custo is CustoMensal.Parcial)
            assertNull("${car.nome} não pode prometer economia com conta parcial", Traducao.economiaMensal(car))
            assertNull(Traducao.fraseEconomia(car))
        }
    }

    @Test
    fun `eletricos com consumo publicado tem custo completo`() {
        val eletricos = base.cars.filter { it.eletrico && Traducao.consumoKwh100(it) != null }
        assertTrue(eletricos.isNotEmpty())
        eletricos.forEach { car ->
            val custo = Traducao.custoMensal(car)
            assertTrue("${car.nome} deveria ter custo completo, veio $custo", custo is CustoMensal.Completo)
            assertTrue("custo mensal fora do razoável: $custo", (custo as CustoMensal.Completo).reais in 50.0..5000.0)
        }
    }

    @Test
    fun `custo mensal nunca fica abaixo da manutencao`() {
        base.cars.forEach { car ->
            val custo = Traducao.custoMensal(car)
            val valor = when (custo) {
                is CustoMensal.Completo -> custo.reais
                is CustoMensal.Parcial -> custo.reais
                is CustoMensal.Indisponivel -> return@forEach
            }
            assertTrue("${car.nome}: $valor", valor >= Premissas.MANUTENCAO_ANO / 12)
        }
    }

    @Test
    fun `autonomia tem sempre uma frase, nunca um traco`() {
        base.cars.forEach { car ->
            val frase = Traducao.fraseAutonomia(car)
            assertTrue("${car.nome}: frase vazia", frase.isNotBlank())
            assertTrue("${car.nome} mostrou traço: $frase", !frase.contains("—"))
            assertTrue("${car.nome} mostrou zero: $frase", !frase.contains(" 0 km"))
        }
    }

    @Test
    fun `recarga sempre diz o que fazer em casa e na estrada`() {
        base.cars.forEach { car ->
            val info = Traducao.recarga(car)
            assertTrue("${car.nome}: sem texto de casa", info.emCasa.isNotBlank())
            assertTrue("${car.nome}: sem texto de estrada", info.naEstrada.isNotBlank())
        }
    }

    @Test
    fun `conta de gasolina usa as premissas declaradas`() {
        val esperado = (Premissas.KM_ANO / 12.0 / Premissas.KM_POR_LITRO_GASOLINA) * Premissas.PRECO_GASOLINA
        assertEquals(esperado, Premissas.custoGasolinaMes, 0.01)
    }

    // ---------- filtros ----------

    @Test
    fun `filtro de preco respeita o teto`() {
        val filtrados = Filtros(precoMax = 200_000).aplicar(base.cars)
        assertTrue(filtrados.isNotEmpty())
        filtrados.forEach { assertTrue("${it.nome} passou do teto", it.preco!! <= 200_000) }
        assertTrue("o filtro deveria reduzir a base", filtrados.size < base.cars.size)
    }

    @Test
    fun `filtro de motorizacao separa as duas categorias`() {
        val eletricos = Filtros(motorizacao = Motorizacao.ELETRICO).aplicar(base.cars)
        val hibridos = Filtros(motorizacao = Motorizacao.HIBRIDO).aplicar(base.cars)
        val todos = Filtros().aplicar(base.cars)

        assertTrue(eletricos.all { it.eletrico })
        assertTrue(hibridos.all { it.hibrido })
        assertEquals(base.cars.size, todos.size)
        assertEquals(todos.size, eletricos.size + hibridos.size)
    }

    @Test
    fun `filtros se combinam e nunca devolvem carro que nao atende`() {
        val filtros = Filtros(
            precoMax = 250_000,
            motorizacao = Motorizacao.ELETRICO,
            carrocerias = setOf("suv"),
            equipamentos = setOf(Equipamento.CAMERA_360),
        )
        val filtrados = filtros.aplicar(base.cars)
        filtrados.forEach { car ->
            assertTrue(car.preco!! <= 250_000)
            assertTrue(car.eletrico)
            assertEquals("suv", car.carroceria)
            assertEquals(true, car.simNao("camera_360"))
        }
        assertTrue(filtrados.isNotEmpty())
    }

    @Test
    fun `preco minimo e maximo delimitam a faixa`() {
        val filtrados = Filtros(precoMin = 200_000, precoMax = 300_000).aplicar(base.cars)
        assertTrue(filtrados.isNotEmpty())
        filtrados.forEach {
            val preco = it.preco!!
            assertTrue("${it.nome}: $preco", preco in 200_000..300_000)
        }
    }

    @Test
    fun `potencia minima filtra pelos cavalos`() {
        val filtrados = Filtros(potenciaMin = 300).aplicar(base.cars)
        assertTrue(filtrados.isNotEmpty())
        filtrados.forEach { car ->
            val potencia = car.numero("potencia_cv") ?: car.numero("potencia_combinada_cv")!!
            assertTrue("${car.nome}: $potencia", potencia >= 300)
        }
    }

    @Test
    fun `filtro de tracao usa os valores da base`() {
        val opcoes = OpcoesDeFiltro(base.cars)
        val tracao = opcoes.tracoes.first { it != "não publicado" }
        val filtrados = Filtros(tracoes = setOf(tracao)).aplicar(base.cars)
        assertTrue(filtrados.isNotEmpty())
        filtrados.forEach { assertEquals(tracao, it.texto("tracao")) }
    }

    @Test
    fun `equipamentos obrigatorios exigem todos os itens marcados`() {
        val filtrados = Filtros(
            equipamentos = setOf(Equipamento.ACC, Equipamento.CAMERA_360),
        ).aplicar(base.cars)
        filtrados.forEach { car ->
            assertEquals(true, car.simNao("acc"))
            assertEquals(true, car.simNao("camera_360"))
        }
        assertTrue(filtrados.isNotEmpty())
    }

    @Test
    fun `busca ignora maiuscula e encontra por modelo`() {
        val maiuscula = Filtros(busca = "BYD").aplicar(base.cars)
        val minuscula = Filtros(busca = "byd").aplicar(base.cars)
        assertEquals(maiuscula.map { it.id }, minuscula.map { it.id })
        assertTrue(maiuscula.isNotEmpty())

        val porModelo = Filtros(busca = "dolphin").aplicar(base.cars)
        assertTrue(porModelo.isNotEmpty())
        assertTrue(porModelo.all { it.modelo.lowercase().contains("dolphin") })
    }

    @Test
    fun `etiquetas mostram o que esta ativo e cada uma remove o seu filtro`() {
        val filtros = Filtros(
            precoMax = 200_000,
            motorizacao = Motorizacao.HIBRIDO,
            carrocerias = setOf("suv"),
            equipamentos = setOf(Equipamento.ACC),
        )
        val etiquetas = filtros.etiquetas()
        assertEquals(4, etiquetas.size)
        assertTrue(etiquetas.any { it.texto.contains("Híbrido") })

        val semPreco = etiquetas.first { it.texto.startsWith("Até") }.remover(filtros)
        assertNull(semPreco.precoMax)
        assertEquals(Motorizacao.HIBRIDO, semPreco.motorizacao)

        assertTrue(!filtros.limpar().temAlgum)
    }

    @Test
    fun `opcoes de filtro saem dos dados`() {
        val opcoes = OpcoesDeFiltro(base.cars)
        assertTrue(opcoes.marcas.contains("BYD"))
        assertTrue(opcoes.carrocerias.contains("suv"))
        assertTrue(opcoes.marcas == opcoes.marcas.sorted())
    }

    // ---------- modelos e ordem ----------

    @Test
    fun `modelos agrupam versoes e usam a mais barata como referencia`() {
        val modelos = agruparEmModelos(base.cars)
        assertTrue("deveria haver menos modelos que versões", modelos.size < base.cars.size)

        val ids = modelos.map { it.id }
        assertEquals(ids.size, ids.toSet().size)

        modelos.forEach { modelo ->
            assertTrue(modelo.versoes.isNotEmpty())
            assertEquals(modelo.versoes.mapNotNull { it.preco }.minOrNull(), modelo.precoMin)
            val precos = modelo.versoes.mapNotNull { it.preco }
            if (precos.isNotEmpty()) assertEquals(precos.minOrNull(), modelo.ideal.preco)
        }
    }

    @Test
    fun `ordenacao por custo deixa o mais barato de rodar em primeiro`() {
        val modelos = Ordenacao.aplicar(agruparEmModelos(base.cars), Ordem.CUSTO)
        val custos = modelos.mapNotNull { Traducao.custoPor100km(it.ideal) }
        assertEquals(custos.sorted(), custos)
    }

    @Test
    fun `ordenacao por preco e autonomia respeitam a direcao`() {
        val porPreco = Ordenacao.aplicar(agruparEmModelos(base.cars), Ordem.PRECO)
            .mapNotNull { it.ideal.preco }
        assertEquals(porPreco.sorted(), porPreco)

        val porAutonomia = Ordenacao.aplicar(agruparEmModelos(base.cars), Ordem.AUTONOMIA)
            .mapNotNull { it.ideal.autonomia }
        assertEquals(porAutonomia.sortedDescending(), porAutonomia)
    }

    @Test
    fun `toda ordem devolve a mesma quantidade de modelos`() {
        val modelos = agruparEmModelos(base.cars)
        Ordem.entries.forEach { ordem ->
            assertEquals(ordem.name, modelos.size, Ordenacao.aplicar(modelos, ordem).size)
        }
    }

    // ---------- comparação ----------

    @Test
    fun `comparacao marca o melhor valor de cada linha`() {
        val caros = base.cars.filter { it.preco != null }.sortedBy { it.preco }.take(3)
        val linhas = Comparacao.linhas(caros, somenteDiferencas = false)

        val preco = linhas.first { it.rotulo == "Preço de tabela" }
        assertEquals(setOf(0), preco.melhores)

        val autonomia = linhas.first { it.rotulo == "Autonomia" }
        val maior = caros.indices.maxByOrNull { caros[it].autonomia ?: -1 }!!
        assertTrue(autonomia.melhores.contains(maior))
    }

    @Test
    fun `comparacao so com diferencas remove linha igual nos dois carros`() {
        val iguais = base.cars.take(2)
        val todas = Comparacao.linhas(iguais, somenteDiferencas = false)
        val diferentes = Comparacao.linhas(iguais, somenteDiferencas = true)
        assertTrue(diferentes.size <= todas.size)
        diferentes.forEach { linha -> assertTrue(linha.valores.distinct().size > 1) }
    }

    @Test
    fun `comparacao nunca marca todos como melhores`() {
        val caros = base.cars.filter { it.preco != null }.take(2)
        Comparacao.linhas(caros, somenteDiferencas = false).forEach { linha ->
            assertTrue("${linha.rotulo} marcou todas as colunas", linha.melhores.size < caros.size)
        }
    }

    @Test
    fun `ficha tecnica de todo carro tem os blocos essenciais`() {
        base.cars.forEach { car ->
            val blocos = CampoCatalogo.blocos(car)
            val grupos = blocos.map { it.grupo }.toSet()
            listOf(Grupo.PRECO, Grupo.BATERIA, Grupo.RECARGA, Grupo.MOTOR, Grupo.SEGURANCA, Grupo.GARANTIA)
                .forEach { essencial ->
                    assertTrue("${car.nome} sem $essencial", essencial in grupos)
                }
            blocos.forEach { bloco ->
                assertTrue("${car.nome}: bloco ${bloco.grupo} vazio", bloco.linhas.isNotEmpty())
                bloco.linhas.forEach { linha ->
                    assertTrue("${car.nome}: linha sem rótulo", linha.rotulo.isNotBlank())
                }
            }
        }
    }

    @Test
    fun `bloco de combustao so aparece em hibrido`() {
        base.cars.forEach { car ->
            val grupos = CampoCatalogo.blocos(car).map { it.grupo }
            assertEquals("${car.nome} (${car.hibrido})", car.hibrido, Grupo.COMBUSTIVEL in grupos)
        }
    }

    /** O teste que protege a regra "dado novo aparece sem código novo". */
    @Test
    fun `campo fora do catalogo aparece nos outros dados`() {
        val comCampoExtra = base.cars.first { car ->
            CampoCatalogo.blocos(car).any { it.grupo == Grupo.OUTROS }
        }
        val outros = CampoCatalogo.blocos(comCampoExtra).first { it.grupo == Grupo.OUTROS }
        assertTrue(outros.linhas.isNotEmpty())
        outros.linhas.forEach { linha ->
            assertTrue("rótulo cru: ${linha.rotulo}", !linha.rotulo.contains("_"))
        }
    }

    @Test
    fun `valores ausentes viram frase e nao traco`() {
        base.cars.forEach { car ->
            CampoCatalogo.blocos(car).flatMap { it.linhas }.forEach { linha ->
                val valor = linha.valor
                if (valor != null) {
                    assertTrue("${car.nome}/${linha.rotulo} mostrou traço", valor != "—")
                    assertTrue("${car.nome}/${linha.rotulo} mostrou traço", !valor.contains("—"))
                } else {
                    assertTrue("${car.nome}/${linha.rotulo} sem frase de ausência", linha.ausente.isNotBlank())
                }
            }
        }
    }

    @Test
    fun `metadados da base estao preenchidos`() {
        assertTrue(base.meta.geradoEm.isNotEmpty())
        assertTrue(base.meta.fontes.isNotEmpty())
        assertTrue(base.meta.schema <= CarsFile.SCHEMA_SUPORTADO)
    }
}
