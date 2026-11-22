package br.com.carrosverdes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import br.com.carrosverdes.BuildConfig
import br.com.carrosverdes.data.Origem
import br.com.carrosverdes.domain.Premissas
import br.com.carrosverdes.domain.Traducao

@Composable
fun SobreScreen(
    estado: EstadoDados,
    aoAtualizar: () -> Unit,
) {
    val tratador = LocalUriHandler.current
    val meta = estado.arquivo?.meta

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tamanhos.espacoGrande, vertical = Tamanhos.espaco),
        verticalArrangement = Arrangement.spacedBy(Tamanhos.espaco),
    ) {
        Text("Sobre os dados", style = MaterialTheme.typography.headlineLarge, color = Cores.Tinta)

        Cartao {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("De onde vem a base", style = MaterialTheme.typography.titleLarge, color = Cores.VerdeEscuro)
                Text(
                    "São ${estado.arquivo?.cars?.size ?: 0} versões de carros elétricos e híbridos " +
                        "plug-in flex à venda no Brasil. Cada versão tem preço de tabela, autonomia, " +
                        "bateria, recarga, equipamentos e garantia, com a fonte de cada número.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.Tinta,
                )
                Text(
                    "Preços de referência: ${meta?.precoReferencia.orEmpty()}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.TintaSuave,
                )
                Text(
                    "Campo sem fonte verificável fica em branco, com uma frase explicando o que falta. " +
                        "Nunca preenchemos com zero nem com estimativa apresentada como dado de fábrica.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.TintaSuave,
                )
            }
        }

        Cartao {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Como calculamos o gasto mensal", style = MaterialTheme.typography.titleLarge, color = Cores.VerdeEscuro)
                Text(
                    "Rodando ${Traducao.inteiro(Premissas.KM_ANO.toDouble())} km por ano, " +
                        "${Traducao.inteiro((Premissas.KM_ANO / 12).toDouble())} km por mês.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Recarga: ${(Premissas.FRACAO_CASA * 100).toInt()}% em casa a " +
                        "${Traducao.reaisComCentavos(Premissas.PRECO_KWH_CASA)}/kWh e o resto em posto público a " +
                        "${Traducao.reaisComCentavos(Premissas.PRECO_KWH_PUBLICO)}/kWh.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Manutenção e pneus: ${Traducao.reais(Premissas.MANUTENCAO_ANO)} por ano.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Comparação com gasolina: ${Traducao.reaisComCentavos(Premissas.PRECO_GASOLINA)} por litro e " +
                        "${Premissas.KM_POR_LITRO_GASOLINA.toInt()} km por litro.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Híbrido plug-in: ${(Premissas.FRACAO_ELETRICA_PHEV * 100).toInt()}% dos quilômetros no modo elétrico.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Fora da conta: seguro, parcelas do financiamento, depreciação, pedágio e a instalação " +
                        "de carregador na garagem.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.Alerta,
                )
            }
        }

        Cartao {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Atualizar os dados", style = MaterialTheme.typography.titleLarge, color = Cores.VerdeEscuro)
                Text(
                    text = if (estado.origem == Origem.BAIXADO) {
                        "Este app está usando uma base baixada, de ${meta?.geradoEm.orEmpty()}."
                    } else {
                        "Este app está usando a base que veio junto com ele, de ${meta?.geradoEm.orEmpty()}."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (estado.atualizando) {
                        "Buscando dados novos…"
                    } else {
                        estado.avisoAtualizacao ?: "Toque para procurar uma base mais nova. Precisa de internet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.TintaSuave,
                )
                BotaoGrande(
                    texto = if (estado.atualizando) "Buscando…" else "Procurar dados novos",
                    aoClicar = { if (!estado.atualizando) aoAtualizar() },
                    secundario = true,
                )
            }
        }

        Cartao {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fontes", style = MaterialTheme.typography.titleLarge, color = Cores.VerdeEscuro)
                meta?.fontes?.forEach { fonte ->
                    Column {
                        Text(fonte.nome, style = MaterialTheme.typography.titleMedium, color = Cores.Tinta)
                        Text(
                            text = "${fonte.licenca} · ${fonte.url}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Cores.Azul,
                            modifier = Modifier.clickable {
                                runCatching { tratador.openUri(fonte.url) }
                            },
                        )
                    }
                }
                Text(
                    "Fotos dos modelos: Wikimedia Commons, com autor e licença ao lado de cada imagem. " +
                        "São ilustrativas do modelo, não necessariamente da versão vendida no Brasil.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.TintaSuave,
                )
            }
        }

        Cartao {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Licença", style = MaterialTheme.typography.titleLarge, color = Cores.VerdeEscuro)
                Text(
                    "Conteúdo e compilação reservados. © 2026 Alexandre Michel Gavronski. " +
                        "Os dados de terceiros mantêm as licenças dos seus autores.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Versão do app ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.TintaSuave,
                )
            }
        }

    }
}
