package br.com.carrosverdes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.carrosverdes.domain.PerfilGuiado
import br.com.carrosverdes.domain.Prioridade
import br.com.carrosverdes.domain.Uso

private class Opcao(val titulo: String, val descricao: String, val acao: () -> Unit)

/**
 * O atalho para quem não sabe por onde começar. Ele não leva a um resultado fechado:
 * preenche os filtros e a ordem da lista, que continua toda ajustável.
 */
@Composable
fun ConversaScreen(
    aoAplicar: (PerfilGuiado) -> Unit,
    aoVoltar: () -> Unit,
) {
    var passo by rememberSaveable { mutableIntStateOf(0) }
    var perfil by remember { mutableStateOf(PerfilGuiado()) }

    val pergunta = when (passo) {
        0 -> "Quanto você pode gastar no carro?"
        1 -> "Como você vai usar o carro?"
        else -> "O que mais importa para você?"
    }
    val ajuda = when (passo) {
        0 -> "O preço à vista, o que aparece na tabela. Vira um filtro na lista."
        1 -> "Pense na maior parte do ano, não na viagem das férias."
        else -> "Isso define a ordem da lista. Os outros critérios continuam lá."
    }

    val opcoes = when (passo) {
        0 -> listOf(
            Opcao("Até R\$ 150 mil", "A faixa dos compactos", { perfil = perfil.copy(orcamentoMax = 150_000); passo = 1 }),
            Opcao("Até R\$ 200 mil", "Compactos e alguns médios", { perfil = perfil.copy(orcamentoMax = 200_000); passo = 1 }),
            Opcao("Até R\$ 300 mil", "Quase todos os modelos", { perfil = perfil.copy(orcamentoMax = 300_000); passo = 1 }),
            Opcao("Sem limite de preço", "Ver o melhor de cada faixa", { perfil = perfil.copy(orcamentoMax = null); passo = 1 }),
        )
        1 -> Uso.entries.map { uso ->
            Opcao(uso.rotulo, uso.pergunta, { perfil = perfil.copy(uso = uso); passo = 2 })
        }
        else -> Prioridade.entries.map { prioridade ->
            Opcao(prioridade.rotulo, prioridade.ajuda, { aoAplicar(perfil.copy(prioridade = prioridade)) })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tamanhos.espacoGrande, vertical = Tamanhos.espaco),
        verticalArrangement = Arrangement.spacedBy(Tamanhos.espaco),
    ) {
        Text(
            text = "Pergunta ${passo + 1} de 3",
            style = MaterialTheme.typography.labelMedium,
            color = Cores.TintaSuave,
        )
        Text(pergunta, style = MaterialTheme.typography.headlineLarge, color = Cores.Tinta)
        Text(ajuda, style = MaterialTheme.typography.bodyMedium, color = Cores.TintaSuave)

        BotaoOuvir { "$pergunta $ajuda" }

        Column(verticalArrangement = Arrangement.spacedBy(Tamanhos.espaco)) {
            opcoes.forEach { opcao ->
                Card(
                    onClick = opcao.acao,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Tamanhos.alvoGrande),
                    shape = RoundedCornerShape(Tamanhos.raio),
                    colors = CardDefaults.cardColors(containerColor = Cores.Papel2),
                    border = BorderStroke(2.dp, Cores.Borda),
                ) {
                    Column(Modifier.padding(Tamanhos.espaco)) {
                        Text(opcao.titulo, style = MaterialTheme.typography.titleLarge, color = Cores.VerdeEscuro)
                        Text(opcao.descricao, style = MaterialTheme.typography.bodyMedium, color = Cores.TintaSuave)
                    }
                }
            }
        }

        BotaoGrande(
            texto = if (passo == 0) "Voltar à lista" else "Voltar",
            aoClicar = { if (passo == 0) aoVoltar() else passo -= 1 },
            secundario = true,
            modifier = Modifier.padding(top = Tamanhos.espaco),
        )
    }
}
