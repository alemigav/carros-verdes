package br.com.carrosverdes

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import br.com.carrosverdes.ui.App
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Percorre o app na JVM com Robolectric: menu inicial, lições, filtros, ordenação,
 * seleção para comparação e ficha técnica.
 *
 * A janela de teste é baixa, então quase sempre é preciso rolar antes de tocar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FluxoDoAppTest {

    @get:Rule
    val compose = createComposeRule()

    private fun esperarTexto(texto: String, tempo: Long = 10_000) {
        compose.waitUntil(tempo) {
            compose.onAllNodesWithText(texto, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun existe(texto: String): Boolean =
        compose.onAllNodesWithText(texto, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun rolarAte(texto: String) {
        compose.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText(texto, substring = true))
    }

    /**
     * Rola até o alvo e toca nele. O que está fora da janela não recebe toque, e
     * performScrollTo garante que o nó fique inteiro dentro da área visível.
     */
    private fun tocar(texto: String) {
        runCatching { rolarAte(texto) }
        val no = compose.onAllNodesWithText(texto, substring = true)[0]
        runCatching { no.performScrollTo() }
        no.performClick()
    }

    /** A caixa de comparação é o único controle alternável da lista. */
    private fun marcarPrimeiroComparar() {
        val caixa = compose.onAllNodes(isToggleable())[0]
        runCatching { caixa.performScrollTo() }
        caixa.performClick()
    }

    private fun abrirInicio() {
        compose.setContent { App() }
        esperarTexto("Bem-vindo")
    }

    private fun abrirLista() {
        abrirInicio()
        tocar("Ver e comparar os carros")
        esperarTexto("Filtros")
    }

    @Test
    fun `abre no menu inicial com marca e caminhos`() {
        abrirInicio()

        compose.onNodeWithText("Carros Verdes").assertExists()
        compose.onNodeWithText("Não sei por onde começar").assertExists()
        compose.onNodeWithText("Entender antes de comprar").assertExists()

        // a barra de baixo nunca some
        compose.onNodeWithText("Início").assertExists()
        compose.onNodeWithText("Carros").assertExists()
        compose.onNodeWithText("Entender").assertExists()
        compose.onNodeWithText("Sobre").assertExists()

        rolarAte("Ver e comparar os carros")
        compose.onNodeWithText("Ver e comparar os carros").assertExists()
        assertTrue(existe("Bem-vindo"))
    }

    @Test
    fun `barra de baixo leva a lista e as licoes sem rolar`() {
        abrirInicio()

        compose.onNodeWithText("Carros").performClick()
        esperarTexto("Filtros")

        compose.onNodeWithText("Entender").performClick()
        esperarTexto("Entender antes de comprar")

        compose.onNodeWithText("Início").performClick()
        esperarTexto("Bem-vindo")
    }

    @Test
    fun `abre na lista com ordenacao e filtros visiveis`() {
        abrirLista()

        assertTrue(existe("menor custo por km"))
        compose.onNodeWithText("Filtros").assertExists()
        compose.onNodeWithText("Ordenar:", substring = true).assertExists()
        compose.onNodeWithText("Híbridos flex").assertExists()
    }

    @Test
    fun `licao ensina e termina em um filtro aplicado`() {
        abrirInicio()
        tocar("Entender antes de comprar")
        esperarTexto("lições curtas")
        tocar("O que é kWh")

        esperarTexto("Duas palavras parecidas")
        tocar("Ver os carros que gastam menos")
        esperarTexto("Ordenar:")
    }

    @Test
    fun `licao de hibrido leva a lista filtrada`() {
        abrirInicio()
        tocar("Entender antes de comprar")
        esperarTexto("lições curtas")

        tocar("Elétrico, híbrido e híbrido plug-in")
        esperarTexto("Híbrido plug-in flex")

        tocar("Ver os híbridos flex")
        esperarTexto("Filtros")
        rolarAte("etanol ou gasolina")
        assertTrue(existe("etanol ou gasolina"))
    }

    @Test
    fun `atalho de hibrido filtra a mesma lista`() {
        abrirLista()
        compose.onNodeWithText("Híbridos flex").performClick()

        esperarTexto("Híbridos flex")
        rolarAte("etanol ou gasolina")
        assertTrue(existe("etanol ou gasolina"))
    }

    @Test
    fun `selecionar um carro abre a bandeja de comparacao`() {
        abrirLista()
        marcarPrimeiroComparar()

        esperarTexto("1 selecionado")
        compose.onNodeWithText("Selecione mais um carro", substring = true).assertExists()
    }

    @Test
    fun `ordenar troca o criterio da lista`() {
        abrirLista()
        tocar("Ordenar:")

        esperarTexto("Ordenar por")
        tocar("Maior autonomia")

        esperarTexto("maior autonomia")
    }

    @Test
    fun `filtro de preco cria etiqueta removivel`() {
        abrirLista()
        tocar("Filtros")
        esperarTexto("Preço")
        compose.onAllNodesWithText("150 mil")[0].performClick()
        tocar("Ver ")

        rolarAte("150.000")
        compose.onAllNodesWithText("150.000", substring = true)[0].performClick()

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("150.000", substring = true).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `guia preenche os filtros e volta para a mesma lista`() {
        abrirInicio()
        tocar("Não sei por onde começar")

        esperarTexto("Quanto você pode gastar no carro?")
        compose.onNodeWithText("Até R\$ 200 mil").performClick()

        esperarTexto("Como você vai usar o carro?")
        compose.onNodeWithText("Principalmente na cidade").performClick()

        esperarTexto("O que mais importa para você?")
        compose.onNodeWithText("Gastar pouco").performClick()

        rolarAte("Busca preenchida pelas suas respostas")
        assertTrue(existe("Busca preenchida pelas suas respostas"))
        assertTrue(existe("200.000"))
    }

    @Test
    fun `ficha tecnica abre a partir do cartao da lista`() {
        abrirLista()
        tocar("1º em")

        esperarTexto("Ficha técnica completa")
        assertTrue(existe("Segurança"))
    }

    @Test
    fun `ficha de hibrido avisa que a conta e parcial`() {
        abrirLista()
        compose.onNodeWithText("Híbridos flex").performClick()
        rolarAte("etanol ou gasolina")

        tocar("etanol ou gasolina")

        esperarTexto("Híbrido plug-in flex")
        assertTrue(existe("Gasto mensal estimado em"))
        assertTrue(existe("não publica o consumo de gasolina"))
    }
}
