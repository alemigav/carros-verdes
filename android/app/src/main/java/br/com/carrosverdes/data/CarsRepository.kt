package br.com.carrosverdes.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class Origem { EMBUTIDO, BAIXADO }

class Carregado(val arquivo: CarsFile, val origem: Origem)

sealed interface ResultadoAtualizacao {
    data class Atualizado(val arquivo: CarsFile) : ResultadoAtualizacao
    data class JaAtual(val geradoEm: String) : ResultadoAtualizacao
    data class Falhou(val motivo: String) : ResultadoAtualizacao
}

fun interface ClienteHttp {
    fun texto(url: String): String
}

class ClienteHttpPadrao : ClienteHttp {
    override fun texto(url: String): String {
        val conexao = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CarrosVerdes-Android")
        }
        try {
            val codigo = conexao.responseCode
            if (codigo !in 200..299) error("O servidor respondeu $codigo.")
            return conexao.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conexao.disconnect()
        }
    }
}

/**
 * A base vive em dois lugares, nesta ordem de preferência: cache baixado, arquivo embutido no APK.
 * O app nunca fica sem dados: se a rede falhar, segue com o que já tem.
 */
class CarsRepository(
    private val context: Context,
    private val cliente: ClienteHttp = ClienteHttpPadrao(),
) {
    private val arquivoCache = File(context.filesDir, "cars.json")

    /**
     * Prefere o cache baixado, mas só se ele for mais novo que a base que veio no APK.
     * Sem essa comparação, um app atualizado continuaria preso a um cache antigo.
     */
    fun carregar(): Carregado {
        val doCache = if (arquivoCache.exists()) {
            runCatching { CarsFile.parse(arquivoCache.readText()) }.getOrNull()
                ?: run { arquivoCache.delete(); null }
        } else {
            null
        }
        val embutido = runCatching { CarsFile.parse(lerEmbutido()) }.getOrNull()

        return when {
            doCache != null && embutido != null && doCache.meta.geradoEm >= embutido.meta.geradoEm ->
                Carregado(doCache, Origem.BAIXADO)
            doCache != null && embutido == null -> Carregado(doCache, Origem.BAIXADO)
            embutido != null -> {
                if (doCache != null) arquivoCache.delete()
                Carregado(embutido, Origem.EMBUTIDO)
            }
            else -> error("Não foi possível abrir a base de dados do aplicativo.")
        }
    }

    private fun lerEmbutido(): String =
        context.assets.open("cars.json").bufferedReader(Charsets.UTF_8).use { it.readText() }

    suspend fun atualizar(atual: CarsFile): ResultadoAtualizacao = withContext(Dispatchers.IO) {
        val texto = runCatching { cliente.texto(URL_BASE) }
            .getOrElse { return@withContext ResultadoAtualizacao.Falhou(it.message ?: "Não foi possível baixar os dados.") }

        val baixado = runCatching { CarsFile.parse(texto) }
            .getOrElse { return@withContext ResultadoAtualizacao.Falhou(it.message ?: "Os dados baixados estão ilegíveis.") }

        if (baixado.meta.geradoEm == atual.meta.geradoEm && baixado.cars.size == atual.cars.size) {
            return@withContext ResultadoAtualizacao.JaAtual(atual.meta.geradoEm)
        }

        runCatching { gravar(texto) }
            .getOrElse { return@withContext ResultadoAtualizacao.Falhou(it.message ?: "Não foi possível guardar os dados.") }

        ResultadoAtualizacao.Atualizado(baixado)
    }

    /** Escreve em arquivo temporário e troca de uma vez: nunca deixa JSON pela metade. */
    private fun gravar(texto: String) {
        val temporario = File(arquivoCache.parentFile, "cars.json.tmp")
        temporario.writeText(texto)
        if (!temporario.renameTo(arquivoCache)) {
            arquivoCache.delete()
            temporario.copyTo(arquivoCache, overwrite = true)
            temporario.delete()
        }
    }

    companion object {
        const val URL_BASE = "https://alemigav.github.io/carros-verdes/data/cars.json"
    }
}
