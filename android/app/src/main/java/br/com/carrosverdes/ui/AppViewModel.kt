package br.com.carrosverdes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.carrosverdes.data.Aprender
import br.com.carrosverdes.data.CarsFile
import br.com.carrosverdes.data.CarsRepository
import br.com.carrosverdes.data.Fotos
import br.com.carrosverdes.data.Origem
import br.com.carrosverdes.data.ResultadoAtualizacao
import br.com.carrosverdes.domain.Filtros
import br.com.carrosverdes.domain.Ordem
import br.com.carrosverdes.domain.PerfilGuiado
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EstadoDados(
    val carregando: Boolean = true,
    val arquivo: CarsFile? = null,
    val origem: Origem = Origem.EMBUTIDO,
    val erro: String? = null,
    val atualizando: Boolean = false,
    val avisoAtualizacao: String? = null,
)

/** Tudo o que a lista precisa para se comportar como uma busca viva. */
data class EstadoExploracao(
    val filtros: Filtros = Filtros(),
    val ordem: Ordem = Ordem.CUSTO,
    val selecionados: Set<String> = emptySet(),
    val perfilAplicado: String? = null,
) {
    val podeComparar: Boolean get() = selecionados.size >= 2
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repositorio = CarsRepository(application)
    val fotos = Fotos(application)
    val aprender = Aprender(application)

    private val _estado = MutableStateFlow(EstadoDados())
    val estado: StateFlow<EstadoDados> = _estado.asStateFlow()

    private val _exploracao = MutableStateFlow(EstadoExploracao())
    val exploracao: StateFlow<EstadoExploracao> = _exploracao.asStateFlow()

    init {
        carregar()
    }

    private fun carregar() {
        viewModelScope.launch {
            val carregado = withContext(Dispatchers.IO) { runCatching { repositorio.carregar() } }
            _estado.update { atual ->
                carregado.fold(
                    onSuccess = { atual.copy(carregando = false, arquivo = it.arquivo, origem = it.origem, erro = null) },
                    onFailure = {
                        atual.copy(
                            carregando = false,
                            erro = it.message ?: "Não foi possível abrir a base de dados.",
                        )
                    },
                )
            }
        }
    }

    // ---------- busca ----------

    fun definirFiltros(novos: Filtros) {
        _exploracao.update { it.copy(filtros = novos, perfilAplicado = null) }
    }

    fun removerEtiqueta(remover: (Filtros) -> Filtros) {
        _exploracao.update { it.copy(filtros = remover(it.filtros), perfilAplicado = null) }
    }

    fun limparFiltros() {
        _exploracao.update { it.copy(filtros = Filtros(), perfilAplicado = null) }
    }

    fun definirOrdem(ordem: Ordem) {
        _exploracao.update { it.copy(ordem = ordem) }
    }

    /** O atalho guiado preenche a busca: daqui para frente é a mesma lista de sempre. */
    fun aplicarPerfil(perfil: PerfilGuiado) {
        _exploracao.update {
            it.copy(filtros = perfil.paraFiltros(), ordem = perfil.ordem(), perfilAplicado = perfil.resumo())
        }
    }

    // ---------- comparação ----------

    fun alternarSelecao(modeloId: String) {
        _exploracao.update { atual ->
            val selecionados = when {
                modeloId in atual.selecionados -> atual.selecionados - modeloId
                atual.selecionados.size >= MAXIMO_COMPARACAO -> atual.selecionados
                else -> atual.selecionados + modeloId
            }
            atual.copy(selecionados = selecionados)
        }
    }

    fun limparSelecao() {
        _exploracao.update { it.copy(selecionados = emptySet()) }
    }

    fun atingiuLimiteDeComparacao(): Boolean = _exploracao.value.selecionados.size >= MAXIMO_COMPARACAO

    // ---------- atualização da base ----------

    fun atualizar() {
        val atual = _estado.value.arquivo ?: return
        if (_estado.value.atualizando) return
        viewModelScope.launch {
            _estado.update { it.copy(atualizando = true, avisoAtualizacao = null) }
            val resultado = repositorio.atualizar(atual)
            _estado.update { estado ->
                when (resultado) {
                    is ResultadoAtualizacao.Atualizado -> estado.copy(
                        atualizando = false,
                        arquivo = resultado.arquivo,
                        origem = Origem.BAIXADO,
                        avisoAtualizacao = "Dados atualizados. Base de ${resultado.arquivo.meta.geradoEm}.",
                    )
                    is ResultadoAtualizacao.JaAtual -> estado.copy(
                        atualizando = false,
                        avisoAtualizacao = "Já está com os dados mais novos, de ${resultado.geradoEm}.",
                    )
                    is ResultadoAtualizacao.Falhou -> estado.copy(
                        atualizando = false,
                        avisoAtualizacao = "Não foi possível atualizar agora. O app segue com os dados que já tem.",
                    )
                }
            }
        }
    }

    companion object {
        const val MAXIMO_COMPARACAO = 4
    }
}
