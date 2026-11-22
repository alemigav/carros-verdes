package br.com.carrosverdes.domain

import br.com.carrosverdes.data.Car

enum class Grupo(val titulo: String, val leigo: String) {
    PRECO("Preço", "Quanto custa o carro"),
    CUSTO("Custo de rodagem", "Quanto sai por mês para rodar"),
    BATERIA("Bateria e autonomia", "Quantos quilômetros anda e com que bateria"),
    RECARGA("Recarga", "Como e onde se carrega"),
    MOTOR("Motorização", "Força, tração e desempenho"),
    COMBUSTIVEL("Motor a combustão", "Só nos híbridos plug-in flex"),
    DIMENSOES("Tamanho e espaço", "Medidas e porta-malas"),
    SEGURANCA("Segurança", "Airbags e assistentes de direção"),
    CONFORTO("Conforto e tecnologia", "Tela, bancos, ar-condicionado"),
    GARANTIA("Garantia e confiança", "Por quanto tempo a fábrica cobre e de onde vêm os dados"),
    OUTROS("Outros dados da base", "Campos ainda sem tradução própria"),
}

enum class Tipo { TEXTO, INTEIRO, DECIMAL, SIM_NAO, MOEDA, MOEDA_CENTAVOS }

class Linha(
    val rotulo: String,
    val valor: String?,
    val explicacao: String?,
    val ausente: String,
)

class Bloco(val grupo: Grupo, val linhas: List<Linha>)

/**
 * O catálogo é a única definição da ficha técnica.
 *
 * Para acrescentar um campo novo ao app, basta uma linha aqui. Para um carro novo,
 * nada: o campo aparece sozinho em "Outros dados da base" enquanto não tiver tradução.
 */
object CampoCatalogo {

    private const val NAO_PUBLICADO = "O fabricante não publica este dado nesta versão."

    private class Campo(
        val chave: String?,
        val rotulo: String,
        val grupo: Grupo,
        val tipo: Tipo = Tipo.TEXTO,
        val unidade: String? = null,
        val explicacao: String? = null,
        val ausente: String = NAO_PUBLICADO,
        val mostrarAusente: Boolean = false,
        val calculado: ((Car) -> String?)? = null,
    )

    /** Chaves de controle da base: não são dado de carro, não entram na ficha. */
    private val METADADOS = setOf(
        "id", "marca", "modelo", "versao", "versao_pesquisada",
        "fontes", "fonte_principal", "observacoes", "alerta_versao",
    )

    private val CAMPOS: List<Campo> = listOf(
        // ---- preço ----
        Campo("preco_brl", "Preço de tabela", Grupo.PRECO, Tipo.MOEDA,
            explicacao = "Preço de campanha ou de tabela na data de referência dos dados.",
            ausente = "Esta versão não tem preço público na base.", mostrarAusente = true),
        Campo("preco_kwh", "Preço por kWh de bateria", Grupo.PRECO, Tipo.MOEDA_CENTAVOS,
            explicacao = "Preço dividido pela bateria: ajuda a ver se o carro está caro para o tamanho da bateria."),
        Campo("preco_por_km_autonomia", "Preço dividido pela autonomia", Grupo.PRECO, Tipo.MOEDA_CENTAVOS,
            explicacao = "Quanto do preço do carro \"compra\" cada quilômetro de alcance. É indicador de preço, não o custo de rodar."),
        Campo("preco_por_cv", "Preço por cavalo de potência", Grupo.PRECO, Tipo.MOEDA),
        Campo("observacao_preco", "Observação do preço", Grupo.PRECO),

        // ---- custo de rodagem ----
        Campo(null, "Por quilômetro rodado", Grupo.CUSTO, calculado = { car ->
            Traducao.custoPor100km(car)?.let { Traducao.reaisComCentavos(it / 100) }
        }),
        Campo(null, "Por 100 quilômetros", Grupo.CUSTO, calculado = { car ->
            Traducao.custoPor100km(car)?.let { Traducao.reaisComCentavos(it) }
        }),
        Campo(null, "Por mês", Grupo.CUSTO, calculado = { car ->
            quandoCompleto(car)?.let { Traducao.reais(it) }
        }),
        Campo(null, "Por ano", Grupo.CUSTO, calculado = { car ->
            quandoCompleto(car)?.let { Traducao.reais(it * 12) }
        }),

        // ---- bateria e autonomia ----
        Campo("bateria_kwh", "Bateria (capacidade usável)", Grupo.BATERIA, Tipo.DECIMAL, "kWh",
            explicacao = "Energia que a bateria guarda. Número maior costuma significar mais autonomia."),
        Campo("bateria_bruta_kwh", "Bateria (capacidade bruta)", Grupo.BATERIA, Tipo.DECIMAL, "kWh"),
        Campo("quimica_bateria", "Química da bateria", Grupo.BATERIA),
        Campo(null, "Autonomia", Grupo.BATERIA, calculado = { car -> car.autonomia?.let(Traducao::km) },
            explicacao = "Quantos quilômetros o carro anda com uma carga cheia, no número usado pelo Inmetro.",
            ausente = "O fabricante não publica a autonomia desta versão.", mostrarAusente = true),
        Campo("autonomia_wltp_km", "Autonomia no ciclo europeu (WLTP)", Grupo.BATERIA, Tipo.INTEIRO, "km"),
        Campo("autonomia_eletrica_km", "Autonomia só no modo elétrico", Grupo.BATERIA, Tipo.INTEIRO, "km"),
        Campo("consumo_calc_kwh_100km", "Consumo de energia", Grupo.BATERIA, Tipo.DECIMAL, "kWh/100 km",
            explicacao = "Quanto de eletricidade o carro gasta para andar 100 km."),
        Campo("eficiencia_km_kwh", "Eficiência", Grupo.BATERIA, Tipo.DECIMAL, "km/kWh"),

        // ---- recarga ----
        Campo("recarga_dc_kw", "Potência de recarga rápida", Grupo.RECARGA, Tipo.DECIMAL, "kW",
            explicacao = "Velocidade máxima que o carro aceita num carregador de estrada."),
        Campo("recarga_dc_10_80_min", "Recarga rápida de 10% a 80%", Grupo.RECARGA, Tipo.INTEIRO, "min",
            explicacao = "Tempo parado na estrada para recuperar a maior parte da carga.",
            ausente = "O fabricante não publica este tempo — o que não quer dizer que o carro não carregue rápido.",
            mostrarAusente = true),
        Campo("recarga_ac_kw", "Recarga em casa", Grupo.RECARGA, Tipo.DECIMAL, "kW",
            explicacao = "Abaixo de 7,4 kW, a tomada comum resolve. Acima disso, é preciso instalar um carregador de parede."),
        Campo("conector_dc", "Conector de recarga rápida", Grupo.RECARGA),
        Campo("v2l", "Fornece tomada (V2L)", Grupo.RECARGA, Tipo.SIM_NAO,
            explicacao = "O carro pode alimentar aparelhos elétricos, como uma extensão."),
        Campo("v2l_kw", "Potência da tomada do carro", Grupo.RECARGA, Tipo.DECIMAL, "kW"),
        Campo("bomba_de_calor", "Bomba de calor", Grupo.RECARGA, Tipo.SIM_NAO,
            explicacao = "Sistema que gasta menos bateria para aquecer a cabine no frio."),

        // ---- motorização ----
        Campo("potencia_cv", "Potência", Grupo.MOTOR, Tipo.INTEIRO, "cv"),
        Campo("potencia_combinada_cv", "Potência combinada", Grupo.MOTOR, Tipo.INTEIRO, "cv"),
        Campo("torque_nm", "Torque", Grupo.MOTOR, Tipo.INTEIRO, "Nm"),
        Campo("torque_combinado_nm", "Torque combinado", Grupo.MOTOR, Tipo.INTEIRO, "Nm"),
        Campo("tracao", "Tração", Grupo.MOTOR),
        Campo("motores_qtd", "Quantidade de motores elétricos", Grupo.MOTOR, Tipo.INTEIRO),
        Campo("zero_a_cem_s", "Aceleração de 0 a 100 km/h", Grupo.MOTOR, Tipo.DECIMAL, "s"),
        Campo("velocidade_max_kmh", "Velocidade máxima", Grupo.MOTOR, Tipo.INTEIRO, "km/h"),
        Campo("peso_kg", "Peso", Grupo.MOTOR, Tipo.INTEIRO, "kg"),
        Campo("cv_por_tonelada", "Relação peso-potência", Grupo.MOTOR, Tipo.DECIMAL, "cv/t"),

        // ---- combustão (híbridos) ----
        Campo("motor_termico", "Motor a combustão", Grupo.COMBUSTIVEL),
        Campo("combustivel", "Combustível", Grupo.COMBUSTIVEL,
            explicacao = "Flex quer dizer etanol ou gasolina, no tanque."),
        Campo("tanque_l", "Tanque de combustível", Grupo.COMBUSTIVEL, Tipo.DECIMAL, "L"),
        Campo("consumo_l_100km", "Consumo de combustível", Grupo.COMBUSTIVEL, Tipo.DECIMAL, "L/100 km",
            explicacao = "Sem este número não é possível estimar o gasto real de um híbrido plug-in.",
            ausente = "O fabricante não publica o consumo de combustível desta versão.", mostrarAusente = true),
        Campo("cambio", "Câmbio", Grupo.COMBUSTIVEL),

        // ---- dimensões ----
        Campo("segmento", "Segmento", Grupo.DIMENSOES),
        Campo("carroceria", "Carroceria", Grupo.DIMENSOES),
        Campo("lugares", "Lugares", Grupo.DIMENSOES, Tipo.INTEIRO, mostrarAusente = true),
        Campo("comprimento_mm", "Comprimento", Grupo.DIMENSOES, Tipo.INTEIRO, "mm"),
        Campo("largura_mm", "Largura", Grupo.DIMENSOES, Tipo.INTEIRO, "mm"),
        Campo("altura_mm", "Altura", Grupo.DIMENSOES, Tipo.INTEIRO, "mm"),
        Campo("entre_eixos_mm", "Entre-eixos", Grupo.DIMENSOES, Tipo.INTEIRO, "mm"),
        Campo("altura_livre_solo_mm", "Altura livre do solo", Grupo.DIMENSOES, Tipo.INTEIRO, "mm"),
        Campo("porta_malas_l", "Porta-malas", Grupo.DIMENSOES, Tipo.INTEIRO, "L",
            explicacao = "Espaço para bagagem. Acima de 500 L cabe bagagem de família.",
            ausente = "O fabricante não publica o porta-malas desta versão.", mostrarAusente = true),
        Campo("porta_malas_max_l", "Porta-malas com bancos rebatidos", Grupo.DIMENSOES, Tipo.INTEIRO, "L"),
        Campo("volume_externo_m3", "Volume externo", Grupo.DIMENSOES, Tipo.DECIMAL, "m³"),

        // ---- segurança ----
        Campo("airbags", "Airbags", Grupo.SEGURANCA, Tipo.INTEIRO, mostrarAusente = true,
            ausente = "O fabricante não publica a quantidade de airbags desta versão."),
        Campo("acc", "Piloto adaptativo (ACC)", Grupo.SEGURANCA, Tipo.SIM_NAO,
            explicacao = "Mantém a distância do carro da frente sozinho, em viagem."),
        Campo("aeb", "Freagem automática de emergência", Grupo.SEGURANCA, Tipo.SIM_NAO,
            explicacao = "Freia sozinho se identificar risco de batida."),
        Campo("lka", "Centralização de faixa", Grupo.SEGURANCA, Tipo.SIM_NAO),
        Campo("ldw", "Aviso de saída de faixa", Grupo.SEGURANCA, Tipo.SIM_NAO),
        Campo("bsm", "Alerta de ponto cego", Grupo.SEGURANCA, Tipo.SIM_NAO),
        Campo("rcta", "Alerta de tráfego cruzado traseiro", Grupo.SEGURANCA, Tipo.SIM_NAO),
        Campo("tsr", "Leitura de placas de trânsito", Grupo.SEGURANCA, Tipo.SIM_NAO),
        Campo("camera_360", "Câmera 360°", Grupo.SEGURANCA, Tipo.SIM_NAO,
            explicacao = "Visão de cima do carro: ajuda a estacionar."),
        Campo("camera_re", "Câmera de ré", Grupo.SEGURANCA, Tipo.SIM_NAO),
        Campo("sensor_estacionamento", "Sensor de estacionamento", Grupo.SEGURANCA, Tipo.SIM_NAO),
        Campo("adas_nivel", "Nível de assistência à direção", Grupo.SEGURANCA),

        // ---- conforto ----
        Campo("tela_central_pol", "Tela central", Grupo.CONFORTO, Tipo.DECIMAL, "pol"),
        Campo("cluster_digital_pol", "Quadro de instrumentos digital", Grupo.CONFORTO, Tipo.DECIMAL, "pol"),
        Campo("carplay", "Apple CarPlay", Grupo.CONFORTO, Tipo.SIM_NAO),
        Campo("android_auto", "Android Auto", Grupo.CONFORTO, Tipo.SIM_NAO),
        Campo("ar_condicionado", "Ar-condicionado", Grupo.CONFORTO),
        Campo("carregador_sem_fio", "Carregador de celular por indução", Grupo.CONFORTO, Tipo.SIM_NAO),
        Campo("som_alto_falantes", "Alto-falantes", Grupo.CONFORTO, Tipo.INTEIRO),
        Campo("som_marca", "Marca do sistema de som", Grupo.CONFORTO),
        Campo("banco_eletrico", "Banco com ajuste elétrico", Grupo.CONFORTO, Tipo.SIM_NAO),
        Campo("bancos_ventilados", "Bancos ventilados", Grupo.CONFORTO, Tipo.SIM_NAO),
        Campo("bancos_aquecidos", "Bancos aquecidos", Grupo.CONFORTO, Tipo.SIM_NAO),
        Campo("teto_panoramico", "Teto panorâmico", Grupo.CONFORTO, Tipo.SIM_NAO),
        Campo("head_up_display", "Projeção no para-brisa", Grupo.CONFORTO, Tipo.SIM_NAO),
        Campo("keyless", "Partida sem chave", Grupo.CONFORTO),
        Campo("partida_remota", "Partida remota", Grupo.CONFORTO),

        // ---- garantia ----
        Campo("garantia_veiculo_anos", "Garantia do carro", Grupo.GARANTIA, Tipo.INTEIRO, "anos"),
        Campo("garantia_veiculo_km", "Limite de quilometragem do carro", Grupo.GARANTIA, Tipo.INTEIRO, "km"),
        Campo("garantia_bateria_anos", "Garantia da bateria", Grupo.GARANTIA, Tipo.INTEIRO, "anos",
            explicacao = "Tempo em que a fábrica troca a bateria se ela perder capacidade.",
            ausente = "O fabricante não publica a garantia da bateria desta versão.", mostrarAusente = true),
        Campo("garantia_bateria_km", "Limite de quilometragem da bateria", Grupo.GARANTIA, Tipo.INTEIRO, "km"),
        Campo(null, "Campos verificados", Grupo.GARANTIA, calculado = { car ->
            car.camposPreenchidos?.let { "$it campos da base têm informação" }
        }),
        Campo(null, "Confiança dos dados", Grupo.GARANTIA, calculado = { Traducao.confiancaEmPalavras(it) }),
    )

    fun blocos(car: Car): List<Bloco> {
        val porGrupo = CAMPOS.groupBy { it.grupo }

        val blocos = Grupo.entries.mapNotNull { grupo ->
            if (grupo == Grupo.OUTROS) return@mapNotNull null
            if (grupo == Grupo.COMBUSTIVEL && !car.hibrido) return@mapNotNull null

            val linhas = porGrupo[grupo].orEmpty().mapNotNull { campo -> linha(car, campo) }
            if (linhas.isEmpty()) null else Bloco(grupo, linhas)
        }.toMutableList()

        // Garantia de manutenção: campo novo na base aparece aqui mesmo sem tradução.
        val conhecidas = CAMPOS.mapNotNull { it.chave }.toSet() + METADADOS
        val extras = car.chaves.filter { it !in conhecidas }.sorted()
        if (extras.isNotEmpty()) {
            blocos += Bloco(
                Grupo.OUTROS,
                extras.map { chave ->
                    Linha(humanizar(chave), textoBruto(car, chave), null, "Não publicado")
                },
            )
        }
        return blocos
    }

    private fun linha(car: Car, campo: Campo): Linha? {
        val valor = campo.calculado?.invoke(car) ?: bruto(car, campo)
        if (valor == null && !campo.mostrarAusente) return null
        return Linha(campo.rotulo, valor, campo.explicacao, campo.ausente)
    }

    private fun bruto(car: Car, campo: Campo): String? {
        val chave = campo.chave ?: return null
        val base = when (campo.tipo) {
            Tipo.TEXTO -> car.texto(chave)
            Tipo.INTEIRO -> car.numero(chave)?.let(Traducao::inteiro)
            Tipo.DECIMAL -> car.numero(chave)?.let(Traducao::numero1)
            Tipo.SIM_NAO -> car.simNao(chave)?.let { if (it) "Sim" else "Não" }
            Tipo.MOEDA -> car.numero(chave)?.let(Traducao::reais)
            Tipo.MOEDA_CENTAVOS -> car.numero(chave)?.let(Traducao::reaisComCentavos)
        } ?: return null
        return if (campo.unidade != null) "$base ${campo.unidade}" else base
    }

    private fun textoBruto(car: Car, chave: String): String? =
        car.texto(chave) ?: car.numero(chave)?.let(Traducao::numero1)

    private fun quandoCompleto(car: Car): Double? =
        (Traducao.custoMensal(car) as? CustoMensal.Completo)?.reais

    private fun humanizar(chave: String): String =
        chave.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
