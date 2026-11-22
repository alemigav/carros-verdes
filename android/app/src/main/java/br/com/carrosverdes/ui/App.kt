package br.com.carrosverdes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.carrosverdes.data.AcaoDaLicao
import br.com.carrosverdes.data.Car
import br.com.carrosverdes.domain.Filtros
import br.com.carrosverdes.domain.Motorizacao
import br.com.carrosverdes.domain.OpcoesDeFiltro
import br.com.carrosverdes.domain.Ordem
import br.com.carrosverdes.domain.Ordenacao
import br.com.carrosverdes.domain.RecargaCasa
import br.com.carrosverdes.domain.agruparEmModelos

sealed interface Tela {
    data object Inicio : Tela
    data object Lista : Tela
    data object Filtros : Tela
    data object Ordenar : Tela
    data object Comparar : Tela
    data object Guia : Tela
    data object Aprender : Tela
    data class Licao(val id: String) : Tela
    data object Sobre : Tela
    data class Ficha(val id: String) : Tela
}

@Composable
fun App(viewModel: AppViewModel = viewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val exploracao by viewModel.exploracao.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val voz = remember { Voz(contexto) }
    DisposableEffect(Unit) { onDispose { voz.encerrar() } }

    val pilha = remember { mutableStateListOf<Tela>(Tela.Inicio) }

    fun abrir(tela: Tela) {
        pilha.remove(tela)
        pilha.add(tela)
    }

    fun voltar() {
        if (pilha.size > 1) pilha.removeAt(pilha.lastIndex)
    }

    fun irPara(aba: Aba) {
        val destino = when (aba) {
            Aba.INICIO -> Tela.Inicio
            Aba.CARROS -> Tela.Lista
            Aba.ENTENDER -> Tela.Aprender
            Aba.SOBRE -> Tela.Sobre
        }
        pilha.clear()
        pilha.add(destino)
    }

    /** Lição e guia terminam na lista, com o filtro já aplicado. */
    fun abrirListaCom(filtros: Filtros, ordem: Ordem) {
        viewModel.definirFiltros(filtros)
        viewModel.definirOrdem(ordem)
        pilha.clear()
        pilha.add(Tela.Lista)
    }

    BackHandler(enabled = pilha.size > 1) { voltar() }

    CompositionLocalProvider(LocalVoz provides voz) {
        TemaCarrosVerdes {
            val tela = pilha.last()
            val comBarraInferior = tela is Tela.Inicio || tela is Tela.Lista ||
                tela is Tela.Aprender || tela is Tela.Sobre

            val abaAtual = when (tela) {
                is Tela.Inicio -> Aba.INICIO
                is Tela.Lista -> Aba.CARROS
                is Tela.Aprender -> Aba.ENTENDER
                is Tela.Sobre -> Aba.SOBRE
                else -> Aba.CARROS
            }

            val tituloTopo = when (tela) {
                is Tela.Filtros -> "Filtros"
                is Tela.Ordenar -> "Ordenar por"
                is Tela.Comparar -> "Comparar carros"
                is Tela.Guia -> "Não sei por onde começar"
                is Tela.Licao -> viewModel.aprender.porId(tela.id)?.titulo ?: "Entender"
                is Tela.Ficha -> estado.arquivo?.cars?.firstOrNull { it.id == tela.id }?.nome ?: "Ficha técnica"
                else -> ""
            }

            Scaffold(
                containerColor = Cores.Papel,
                topBar = {
                    if (!comBarraInferior) BarraTopo(tituloTopo) { voltar() }
                },
                bottomBar = {
                    if (comBarraInferior) BarraInferior(abaAtual) { irPara(it) }
                },
            ) { espaco ->
                Box(Modifier.fillMaxSize().padding(espaco)) {
                    val arquivo = estado.arquivo
                    when {
                        estado.carregando -> Carregando()
                        arquivo == null -> FalhaAoAbrir(estado.erro)
                        else -> {
                            val filtrados = remember(arquivo.cars, exploracao.filtros) {
                                exploracao.filtros.aplicar(arquivo.cars)
                            }
                            val modelos = remember(filtrados, exploracao.ordem) {
                                Ordenacao.aplicar(agruparEmModelos(filtrados), exploracao.ordem)
                            }
                            val todosOsModelos = remember(arquivo.cars) { agruparEmModelos(arquivo.cars) }
                            val modelosPorId = remember(todosOsModelos) { todosOsModelos.associateBy { it.id } }
                            val opcoes = remember(arquivo.cars) { OpcoesDeFiltro(arquivo.cars) }

                            when (tela) {
                                Tela.Inicio -> InicioScreen(
                                    totalCarros = arquivo.cars.size,
                                    totalLicoes = viewModel.aprender.licoes.size,
                                    aoEscolherGuia = { abrir(Tela.Guia) },
                                    aoAprender = { irPara(Aba.ENTENDER) },
                                    aoVerLista = { irPara(Aba.CARROS) },
                                )

                                Tela.Lista -> ListaScreen(
                                    modelos = modelos,
                                    totalNaBase = todosOsModelos.size,
                                    estado = exploracao,
                                    fotos = viewModel.fotos,
                                    aoBuscar = { texto ->
                                        viewModel.definirFiltros(exploracao.filtros.copy(busca = texto))
                                    },
                                    aoEscolherPreset = { preset -> viewModel.definirFiltros(preset) },
                                    aoAbrirFiltros = { abrir(Tela.Filtros) },
                                    aoAbrirOrdem = { abrir(Tela.Ordenar) },
                                    aoRemoverEtiqueta = { remover -> viewModel.removerEtiqueta(remover) },
                                    aoLimparFiltros = viewModel::limparFiltros,
                                    aoAlternarSelecao = viewModel::alternarSelecao,
                                    aoAbrirFicha = { abrir(Tela.Ficha(it)) },
                                    aoComparar = { abrir(Tela.Comparar) },
                                    aoDescartarSelecao = viewModel::limparSelecao,
                                    aoAbrirGuia = { abrir(Tela.Guia) },
                                )

                                Tela.Filtros -> FiltrosScreen(
                                    filtrosIniciais = exploracao.filtros,
                                    cars = arquivo.cars,
                                    opcoes = opcoes,
                                    aoAplicar = { novos ->
                                        viewModel.definirFiltros(novos)
                                        voltar()
                                    },
                                )

                                Tela.Ordenar -> OrdenarScreen(
                                    ordemAtual = exploracao.ordem,
                                    aoEscolher = { ordem ->
                                        viewModel.definirOrdem(ordem)
                                        voltar()
                                    },
                                )

                                Tela.Comparar -> CompararScreen(
                                    cars = exploracao.selecionados.mapNotNull { modelosPorId[it]?.ideal },
                                    aoAbrirFicha = { abrir(Tela.Ficha(it)) },
                                    aoLimparSelecao = {
                                        viewModel.limparSelecao()
                                        voltar()
                                    },
                                )

                                Tela.Guia -> ConversaScreen(
                                    aoAplicar = { perfil ->
                                        viewModel.aplicarPerfil(perfil)
                                        pilha.clear()
                                        pilha.add(Tela.Lista)
                                    },
                                    aoVoltar = { voltar() },
                                )

                                Tela.Aprender -> AprenderScreen(
                                    licoes = viewModel.aprender.licoes,
                                    aoAbrir = { abrir(Tela.Licao(it)) },
                                )

                                is Tela.Licao -> {
                                    val licao = viewModel.aprender.porId(tela.id)
                                    if (licao == null) {
                                        voltar()
                                    } else {
                                        LicaoScreen(
                                            licao = licao,
                                            aoAplicarAcao = { acao ->
                                                val (filtros, ordem) = filtroDaAcao(acao)
                                                abrirListaCom(filtros, ordem)
                                            },
                                        )
                                    }
                                }

                                Tela.Sobre -> SobreScreen(
                                    estado = estado,
                                    aoAtualizar = viewModel::atualizar,
                                )

                                is Tela.Ficha -> {
                                    val car = arquivo.cars.firstOrNull { it.id == tela.id }
                                    if (car == null) {
                                        voltar()
                                    } else {
                                        FichaScreen(
                                            car = car,
                                            todasAsVersoes = arquivo.cars,
                                            fotos = viewModel.fotos,
                                            aoAbrirFicha = { abrir(Tela.Ficha(it)) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Cada lição termina em um filtro de verdade, não em um texto solto. */
private fun filtroDaAcao(acao: AcaoDaLicao): Pair<Filtros, Ordem> = when (acao.filtro) {
    "hibrido" -> Filtros(motorizacao = Motorizacao.HIBRIDO) to Ordem.CUSTO
    "eletrico" -> Filtros(motorizacao = Motorizacao.ELETRICO) to Ordem.CUSTO
    "autonomia" -> Filtros() to Ordem.AUTONOMIA
    "tomada" -> Filtros(recargaCasa = RecargaCasa.TOMADA) to Ordem.CUSTO
    "custo" -> Filtros() to Ordem.CUSTO
    else -> Filtros() to Ordem.CUSTO
}

@Composable
private fun Carregando() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Tamanhos.espaco),
        ) {
            CircularProgressIndicator(color = Cores.Verde)
            Text("Abrindo a base de carros…", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun FalhaAoAbrir(motivo: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Tamanhos.espacoGrande),
        verticalArrangement = Arrangement.spacedBy(Tamanhos.espaco),
    ) {
        Text("Não foi possível abrir os dados", style = MaterialTheme.typography.headlineMedium, color = Cores.Tinta)
        Text(
            motivo ?: "O arquivo de dados do app está com problema.",
            style = MaterialTheme.typography.bodyLarge,
            color = Cores.TintaSuave,
        )
    }
}

/** Usado pela ficha para saber quem são as outras versões do mesmo modelo. */
fun irmaosDe(car: Car, todas: List<Car>): List<Car> =
    todas.filter { it.marca == car.marca && it.modelo == car.modelo && it.id != car.id }
        .sortedBy { it.preco ?: Int.MAX_VALUE }
