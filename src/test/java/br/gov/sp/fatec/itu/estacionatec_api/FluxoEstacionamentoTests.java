package br.gov.sp.fatec.itu.estacionatec_api;

import br.gov.sp.fatec.itu.estacionatec_api.repositories.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:fluxo-test;DB_CLOSE_DELAY=-1",
        "estacionatec.storage=target/test-images",
        "estacionatec.camera.snapshot-url=",
        "estacionatec.camera.source=WEBCAM",
        "estacionatec.esp32.open-url=",
        "logging.level.root=WARN"
})
class FluxoEstacionamentoTests {
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private br.gov.sp.fatec.itu.estacionatec_api.integration.CameraClient camera;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private br.gov.sp.fatec.itu.estacionatec_api.integration.Esp32Client esp32;
    private static final AtomicInteger SEQUENCIA = new AtomicInteger(1000);
    @LocalServerPort
    private int port;
    @Autowired
    private JsonMapper mapper;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private EventoAcessoRepository eventos;
    @Autowired
    private LogSistemaRepository logs;
    @Autowired
    private ImagemCapturadaRepository imagens;
    @Autowired
    private PessoaRepository pessoas;
    @Autowired
    private VeiculoRepository veiculos;
    private final HttpClient client = HttpClient.newHttpClient();
    private String token;

    @BeforeEach
    void autenticar() throws Exception {
        token = null;
        var response = request("POST", "/auth/login", Map.of("email", "joao@edu.br", "password", "EstacionaTec@123"));
        assertThat(response.statusCode()).isEqualTo(200);
        token = json(response).path("token").asText();
    }

    @Test
    void autenticaComSenhaProtegidaEBloqueiaInativo() throws Exception {
        assertThat(usuarios.findByUsernameIgnoreCase("joao.carlos").orElseThrow().getSenha()).startsWith("$2");
        assertThat(request("POST", "/auth/login", Map.of("email", "joao@edu.br", "password", "errada")).statusCode()).isEqualTo(401);
        assertThat(request("POST", "/auth/login", Map.of("email", "juliana@edu.br", "password", "EstacionaTec@123")).statusCode()).isEqualTo(401);
        token = null;
        assertThat(request("GET", "/veiculos", null).statusCode()).isEqualTo(401);
        assertThat(request("GET", "/camera/preview", null).statusCode()).isEqualTo(401);
    }

    @Test
    void porteiroNaoPodeAdministrarCadastros() throws Exception {
        token = json(request("POST", "/auth/login", Map.of("email", "marcos@edu.br", "password", "EstacionaTec@123"))).path("token").asText();
        assertThat(request("GET", "/veiculos", null).statusCode()).isEqualTo(200);
        assertThat(request("GET", "/usuarios", null).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/pessoas", null).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/relatorios", null).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/relatorios/1/arquivo", null).statusCode()).isEqualTo(403);
        assertThat(request("POST", "/relatorios", Map.of()).statusCode()).isEqualTo(403);
        assertThat(request("POST", "/veiculos", Map.of()).statusCode()).isEqualTo(403);
        assertThat(request("PUT", "/veiculos/1", Map.of()).statusCode()).isEqualTo(403);
        assertThat(request("DELETE", "/veiculos/1", null).statusCode()).isEqualTo(403);
        for (String path : List.of("/dashboard", "/historico", "/movimentacoes/ativos", "/imagens", "/camera/status")) {
            assertThat(request("GET", path, null).statusCode()).as(path).isEqualTo(200);
        }
        assertThat(request("POST", "/pessoas", Map.of("name", "Sem permissão", "document", "123", "type", "Aluno")).statusCode()).isEqualTo(403);
    }

    @Test
    void registraEntradaSaidaEHistoricoMesmoSemPortao() throws Exception {
        String placa = novoVeiculo();
        var entrada = request("POST", "/movimentacoes/entrada", movimento(placa, upload(), UUID.randomUUID().toString()));
        assertThat(entrada.statusCode()).isEqualTo(200);
        assertThat(json(entrada).path("gateStatus").asText()).isEqualTo("ERRO");
        assertThat(json(entrada).path("warning").asText()).contains("ESP32 não conectado");
        assertThat(request("POST", "/cancela/abrir", Map.of("eventId", json(entrada).path("id").asLong())).statusCode()).isEqualTo(503);
        assertThat(json(request("GET", "/movimentacoes/ativos", null)).toString()).contains(placa.substring(0, 3) + "-" + placa.substring(3));
        assertThat(request("POST", "/movimentacoes/entrada", movimento(placa, upload(), UUID.randomUUID().toString())).statusCode()).isEqualTo(409);
        var saida = request("POST", "/movimentacoes/saida", movimento(placa, upload(), UUID.randomUUID().toString()));
        assertThat(saida.statusCode()).isEqualTo(200);
        assertThat(json(saida).path("exitedAt").isNull()).isFalse();
        assertThat(json(request("GET", "/movimentacoes/ativos", null)).toString()).doesNotContain(placa.substring(0, 3) + "-" + placa.substring(3));
        assertThat(request("POST", "/movimentacoes/saida", movimento(placa, upload(), UUID.randomUUID().toString())).statusCode()).isEqualTo(409);
        assertThat(logs.findAll()).anyMatch(log -> log.getAcao().equals("FALHA_CANCELA"));
    }

    @Test
    void reenvioEConcorrenciaNaoDuplicamEntrada() throws Exception {
        String placa = novoVeiculo();
        var body = movimento(placa, upload(), UUID.randomUUID().toString());
        var first = request("POST", "/movimentacoes/entrada", body);
        var second = request("POST", "/movimentacoes/entrada", body);
        assertThat(json(first).path("id").asLong()).isEqualTo(json(second).path("id").asLong());
        String outraPlaca = novoVeiculo();
        var request1 = httpRequest("POST", "/movimentacoes/entrada", movimento(outraPlaca, upload(), UUID.randomUUID().toString()));
        var request2 = httpRequest("POST", "/movimentacoes/entrada", movimento(outraPlaca, upload(), UUID.randomUUID().toString()));
        var futuro1 = client.sendAsync(request1, HttpResponse.BodyHandlers.ofString());
        var futuro2 = client.sendAsync(request2, HttpResponse.BodyHandlers.ofString());
        assertThat(List.of(futuro1.get().statusCode(), futuro2.get().statusCode())).containsExactlyInAnyOrder(200, 409);
    }

    @Test
    void validaImagemQuandoInformadaEImpedeVeiculoDesconhecido() throws Exception {
        String placa = novoVeiculo();
        long count = eventos.count();
        assertThat(request("POST", "/movimentacoes/entrada", movimento("ZZZ9999", upload(), UUID.randomUUID().toString())).statusCode()).isEqualTo(409);
        assertThat(eventos.count()).isEqualTo(count);
        long imagem = upload();
        assertThat(request("POST", "/movimentacoes/entrada", movimento(placa, imagem, UUID.randomUUID().toString())).statusCode()).isEqualTo(200);
        assertThat(request("POST", "/movimentacoes/saida", movimento(placa, imagem, UUID.randomUUID().toString())).statusCode()).isEqualTo(409);
    }

    @Test
    void registraEntradaESaidaManuaisSemImagemEPermiteReenvio() throws Exception {
        String placa = novoVeiculo();
        var dadosEntrada = Map.of("plate", placa, "requestId", UUID.randomUUID().toString());
        var entrada = request("POST", "/movimentacoes/entrada", dadosEntrada);
        assertThat(entrada.statusCode()).isEqualTo(200);
        assertThat(json(entrada).path("warning").asText()).contains("não foi possível capturar a imagem");
        long entradaId = json(entrada).path("id").asLong();
        assertThat(eventos.findById(entradaId).orElseThrow().getImagem()).isNull();
        var reenvioEntrada = request("POST", "/movimentacoes/entrada", dadosEntrada);
        assertThat(reenvioEntrada.statusCode()).isEqualTo(200);
        assertThat(json(reenvioEntrada).path("id").asLong()).isEqualTo(entradaId);
        assertThat(request("POST", "/movimentacoes/entrada",
                Map.of("plate", placa, "requestId", UUID.randomUUID().toString())).statusCode()).isEqualTo(409);

        var dadosSaida = Map.of("plate", placa, "requestId", UUID.randomUUID().toString());
        var saida = request("POST", "/movimentacoes/saida", dadosSaida);
        assertThat(saida.statusCode()).isEqualTo(200);
        long saidaId = json(saida).path("id").asLong();
        assertThat(eventos.findById(saidaId).orElseThrow().getImagem()).isNull();
        assertThat(eventos.findById(saidaId).orElseThrow().getEntradaId()).isEqualTo(entradaId);
        var reenvioSaida = request("POST", "/movimentacoes/saida", dadosSaida);
        assertThat(reenvioSaida.statusCode()).isEqualTo(200);
        assertThat(json(reenvioSaida).path("id").asLong()).isEqualTo(saidaId);
    }

    @Test
    void editaVeiculoEstacionadoPreservandoEntradaEPermitindoSaidaPelaPlacaAtual() throws Exception {
        String placa = novoVeiculo();
        var entrada = json(request("POST", "/movimentacoes/entrada",
                Map.of("plate", placa, "requestId", UUID.randomUUID().toString())));
        long veiculoId = entrada.path("vehicleId").asLong();
        long entradaId = entrada.path("id").asLong();
        String placaAtual = "EDT" + SEQUENCIA.incrementAndGet();
        var atualizacao = request("PUT", "/veiculos/" + veiculoId,
                Map.of("plate", placaAtual, "ownerId", 2, "model", "Modelo corrigido",
                        "color", "Preto", "type", "Carro", "brand", "Marca corrigida", "authorized", true));
        assertThat(atualizacao.statusCode()).isEqualTo(200);
        assertThat(json(atualizacao).path("model").asText()).isEqualTo("Modelo corrigido");
        JsonNode ativo = null;
        for (JsonNode item : json(request("GET", "/movimentacoes/ativos", null))) {
            if (item.path("id").asLong() == entradaId) {
                ativo = item;
            }
        }
        assertThat(ativo).isNotNull();
        assertThat(ativo.path("plate").asText()).isEqualTo(placaAtual.substring(0, 3) + "-" + placaAtual.substring(3));
        assertThat(ativo.path("owner").asText()).isEqualTo("Carlos Santos");
        assertThat(ativo.path("model").asText()).isEqualTo("Modelo corrigido");
        assertThat(eventos.findById(entradaId).orElseThrow().getPlaca()).isEqualTo(placa);
        assertThat(eventos.findById(entradaId).orElseThrow().getModelo()).isEqualTo("Teste");
        var saida = request("POST", "/movimentacoes/saida",
                Map.of("plate", placaAtual, "requestId", UUID.randomUUID().toString()));
        assertThat(saida.statusCode()).isEqualTo(200);
        assertThat(eventos.findById(json(saida).path("id").asLong()).orElseThrow().getEntradaId()).isEqualTo(entradaId);
    }

    @Test
    void rejeitaCapturaWebcamSemArquivoEImagemFalsa() throws Exception {
        long total = imagens.count();
        assertThat(request("GET", "/camera/preview", null).statusCode()).isEqualTo(409);
        assertThat(imagens.count()).isEqualTo(total);
        assertThat(request("POST", "/camera/capturar", null).statusCode()).isEqualTo(409);
        assertThat(uploadBytes("nao e imagem".getBytes(StandardCharsets.UTF_8)).statusCode()).isEqualTo(400);
        long imageId = upload();
        assertThat(request("GET", "/imagens/" + imageId + "/arquivo", null).statusCode()).isEqualTo(200);
    }

    @Test
    void salvaFotoDaWebcamComOrigemEOperadorERejeitaArquivoInvalido() throws Exception {
        assertThat(json(request("GET", "/camera/status", null)).path("source").asText()).isEqualTo("WEBCAM");
        long antes = imagens.count();
        assertThat(uploadBytes("/camera/webcam/capturar", "invalido".getBytes(StandardCharsets.UTF_8))
                .statusCode()).isEqualTo(400);
        assertThat(imagens.count()).isEqualTo(antes);
        token = json(request("POST", "/auth/login", Map.of("email", "marcos@edu.br",
                "password", "EstacionaTec@123"))).path("token").asText();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var response = uploadBytes("/camera/webcam/capturar", bytes.toByteArray());
        assertThat(response.statusCode()).isEqualTo(201);
        long id = json(response).path("id").asLong();
        var registro = imagens.findById(id).orElseThrow();
        assertThat(registro.getOrigem()).isEqualTo("WEBCAM");
        assertThat(registro.getUsuarioId()).isEqualTo(usuarios.findByUsernameIgnoreCase("marcos.oliveira")
                .orElseThrow().getId());
        assertThat(registro.isDisponivel()).isTrue();
        assertThat(request("GET", "/imagens/" + id + "/arquivo", null).statusCode()).isEqualTo(200);
        token = null;
        assertThat(uploadBytes("/camera/webcam/capturar", bytes.toByteArray()).statusCode()).isEqualTo(401);
    }

    @Test
    void geraRelatorioComDadosReais() throws Exception {
        String placa = novoVeiculo();
        var entrada = request("POST", "/movimentacoes/entrada",
                Map.of("plate", placa, "requestId", UUID.randomUUID().toString()));
        assertThat(entrada.statusCode()).isEqualTo(200);
        String hoje = java.time.LocalDate.now().toString();
        var response = request("POST", "/relatorios", Map.of("start", hoje, "end", hoje, "type", "Entradas e Saídas"));
        assertThat(response.statusCode()).isEqualTo(200);
        var csv = request("GET", "/relatorios/" + json(response).path("id").asLong() + "/arquivo", null);
        assertThat(csv.statusCode()).isEqualTo(200);
        assertThat(csv.body()).contains(placa, "Maria Silva");
        assertThat(request("POST", "/relatorios", Map.of("start", "2026-09-01", "end", "2026-08-01", "type", "Entradas e Saídas")).statusCode()).isEqualTo(409);
    }

    @Test
    void protegeVinculosERejeitaDuplicidadeDeCadastro() throws Exception {
        assertThat(request("DELETE", "/pessoas/1", null).statusCode()).isEqualTo(409);
        String placa = novoVeiculo();
        var entrada = request("POST", "/movimentacoes/entrada",
                Map.of("plate", placa, "requestId", UUID.randomUUID().toString()));
        assertThat(entrada.statusCode()).isEqualTo(200);
        long veiculoId = json(entrada).path("vehicleId").asLong();
        assertThat(request("DELETE", "/veiculos/" + veiculoId, null).statusCode()).isEqualTo(409);
        assertThat(request("POST", "/pessoas", Map.of("name", "Duplicado", "document", "123.456.789-00", "type", "Aluno")).statusCode()).isEqualTo(409);
        assertThat(request("POST", "/veiculos", Map.of("plate", "ABC--1234", "ownerId", 1, "model", "Teste", "type", "Carro")).statusCode()).isEqualTo(409);
        assertThat(request("POST", "/veiculos", Map.of("plate", "MER1C23", "ownerId", 1, "model", "Mercosul", "type", "Carro")).statusCode()).isEqualTo(201);
    }

    private String novoVeiculo() throws Exception {
        String placa = "TST" + SEQUENCIA.incrementAndGet();
        var response = request("POST", "/veiculos", Map.of("plate", placa, "ownerId", 1,
                "model", "Teste", "color", "Azul", "type", "Carro"));
        assertThat(response.statusCode()).isEqualTo(201);
        return placa;
    }

    @Test
    void porteiroAbreManualmenteSemMovimentacaoEFotografaMesmoSePortaoFalhar() throws Exception {
        token = json(request("POST", "/auth/login", Map.of("email", "marcos@edu.br",
                "password", "EstacionaTec@123"))).path("token").asText();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", bytes);
        org.mockito.Mockito.doReturn(bytes.toByteArray()).when(camera).capturar();
        org.mockito.Mockito.doNothing().when(esp32).abrirManualmente();
        long totalEventos = eventos.count();
        long totalImagens = imagens.count();
        var resposta = request("POST", "/cancela/abrir-manualmente", null);
        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(json(resposta).path("message").asText()).contains("Abertura manual confirmada", "Imagem do momento registrada");
        assertThat(eventos.count()).isEqualTo(totalEventos);
        assertThat(imagens.count()).isEqualTo(totalImagens + 1);
        assertThat(imagens.findAll()).anyMatch(i -> "CANCELA_MANUAL".equals(i.getOrigem())
                && "Abertura manual".equals(i.getTipoEvento()) && i.getUsuarioId() != null);

        org.mockito.Mockito.doThrow(br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException
                .conflito("Câmera desligada")).when(camera).capturar();
        resposta = request("POST", "/cancela/abrir-manualmente", null);
        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(json(resposta).path("message").asText()).contains("Abertura manual confirmada", "Não foi possível capturar");
        org.mockito.Mockito.verify(esp32, org.mockito.Mockito.times(2)).abrirManualmente();

        org.mockito.Mockito.doReturn(bytes.toByteArray()).when(camera).capturar();
        org.mockito.Mockito.doThrow(br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException
                .conflito("ESP32 não conectado")).when(esp32).abrirManualmente();
        resposta = request("POST", "/cancela/abrir-manualmente", null);
        assertThat(resposta.statusCode()).isEqualTo(503);
        assertThat(json(resposta).path("message").asText()).contains("ESP32 não conectado", "Imagem do momento registrada");
        assertThat(eventos.count()).isEqualTo(totalEventos);
        assertThat(imagens.count()).isEqualTo(totalImagens + 2);
        token = null;
        assertThat(request("POST", "/cancela/abrir-manualmente", null).statusCode()).isEqualTo(401);
        org.mockito.Mockito.verify(esp32, org.mockito.Mockito.times(3)).abrirManualmente();
    }

    @Test
    void capturaImagemNaEntradaManualSemDuplicarNoReenvioOuNaRecusa() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", bytes);
        org.mockito.Mockito.doReturn(bytes.toByteArray()).when(camera).capturar();
        long antes = imagens.count();
        String placa = novoVeiculo();
        var dados = Map.of("plate", placa, "requestId", UUID.randomUUID().toString());
        var entrada = request("POST", "/movimentacoes/entrada", dados);
        assertThat(entrada.statusCode()).isEqualTo(200);
        long id = json(entrada).path("id").asLong();
        var foto = eventos.findById(id).orElseThrow().getImagem();
        assertThat(foto).isNotNull();
        assertThat(foto.isUtilizada()).isTrue();
        assertThat(foto.getPlacaDetectada()).isEqualTo(placa);
        assertThat(foto.getTipoEvento()).isEqualTo("Entrada");
        assertThat(foto.getOrigem()).isEqualTo("CAMERA");
        assertThat(request("GET", "/imagens/" + foto.getId() + "/arquivo", null).statusCode()).isEqualTo(200);
        var repetida = request("POST", "/movimentacoes/entrada", dados);
        assertThat(repetida.statusCode()).isEqualTo(200);
        assertThat(json(repetida).path("id").asLong()).isEqualTo(id);
        assertThat(request("POST", "/movimentacoes/entrada", Map.of("plate", placa,
                "requestId", UUID.randomUUID().toString())).statusCode()).isEqualTo(409);
        assertThat(imagens.count()).isEqualTo(antes + 1);
        org.mockito.Mockito.verify(camera, org.mockito.Mockito.times(1)).capturar();

        String visitante = "FOT" + SEQUENCIA.incrementAndGet();
        var visita = request("POST", "/movimentacoes/entrada", Map.of("plate", visitante,
                "requestId", UUID.randomUUID().toString(),
                "visitor", Map.of("responsibleName", "Visitante fotografado", "model", "Uno")));
        assertThat(visita.statusCode()).isEqualTo(200);
        var evento = eventos.findById(json(visita).path("id").asLong()).orElseThrow();
        assertThat(evento.getVeiculo()).isNull();
        assertThat(evento.getImagem().getProprietario()).isEqualTo("Visitante fotografado");
        assertThat(imagens.count()).isEqualTo(antes + 2);
    }

    @Test
    void excluiVeiculoSemHistoricoEDepoisPessoaSemVinculos() throws Exception {
        int numero = SEQUENCIA.incrementAndGet();
        var pessoa = request("POST", "/pessoas", Map.of("name", "Pessoa para exclusão",
                "document", "EXC" + numero, "type", "Visitante"));
        assertThat(pessoa.statusCode()).isEqualTo(201);
        long pessoaId = json(pessoa).path("id").asLong();
        var veiculo = request("POST", "/veiculos", Map.of("plate", "EXC" + numero,
                "ownerId", pessoaId, "model", "Uno", "type", "Carro"));
        assertThat(veiculo.statusCode()).isEqualTo(201);
        long veiculoId = json(veiculo).path("id").asLong();
        assertThat(request("DELETE", "/pessoas/" + pessoaId, null).statusCode()).isEqualTo(409);
        assertThat(request("DELETE", "/veiculos/" + veiculoId, null).statusCode()).isEqualTo(204);
        assertThat(request("DELETE", "/pessoas/" + pessoaId, null).statusCode()).isEqualTo(204);
        assertThat(veiculos.existsById(veiculoId)).isFalse();
        assertThat(pessoas.existsById(pessoaId)).isFalse();
    }

    @Test
    void solicitaDadosDoVisitanteAntesDeCadastrar() throws Exception {
        String placa = "VIS" + SEQUENCIA.incrementAndGet();
        long totalPessoas = pessoas.count();
        long totalVeiculos = veiculos.count();
        long totalEventos = eventos.count();
        var dados = Map.of("plate", placa, "requestId", UUID.randomUUID().toString());
        var response = request("POST", "/movimentacoes/entrada", dados);
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(json(response).path("code").asText()).isEqualTo("VEICULO_NAO_CADASTRADO");
        assertThat(request("POST", "/movimentacoes/entrada", Map.of("plate", placa,
                "requestId", UUID.randomUUID().toString(),
                "visitor", Map.of("responsibleName", " ", "model", "Uno"))).statusCode()).isEqualTo(400);
        assertThat(request("POST", "/movimentacoes/entrada", Map.of("plate", placa,
                "requestId", UUID.randomUUID().toString(),
                "visitor", Map.of("responsibleName", "Ana", "model", " "))).statusCode()).isEqualTo(400);
        assertThat(request("POST", "/movimentacoes/saida", dados).statusCode()).isEqualTo(409);
        assertThat(pessoas.count()).isEqualTo(totalPessoas);
        assertThat(veiculos.count()).isEqualTo(totalVeiculos);
        assertThat(eventos.count()).isEqualTo(totalEventos);
    }

    @Test
    void porteiroRegistraVisitaSemCriarCadastrosPermiteReenvioESaida() throws Exception {
        token = json(request("POST", "/auth/login", Map.of("email", "marcos@edu.br",
                "password", "EstacionaTec@123"))).path("token").asText();
        String placa = "VIS" + SEQUENCIA.incrementAndGet();
        long totalPessoas = pessoas.count();
        long totalVeiculos = veiculos.count();
        long totalEventos = eventos.count();
        var dados = Map.of("plate", placa.toLowerCase(), "requestId", UUID.randomUUID().toString(),
                "visitor", Map.of("responsibleName", " Ana Visitante ", "model", " Fiat Uno "));
        var entrada = request("POST", "/movimentacoes/entrada", dados);
        assertThat(entrada.statusCode()).isEqualTo(200);
        assertThat(json(entrada).path("owner").asText()).isEqualTo("Ana Visitante");
        assertThat(json(entrada).path("model").asText()).isEqualTo("Fiat Uno");
        assertThat(json(entrada).path("category").asText()).isEqualTo("Visitante");
        assertThat(json(entrada).path("vehicleId").isNull()).isTrue();
        assertThat(json(entrada).path("plate").asText()).isEqualTo("VIS-" + placa.substring(3));
        assertThat(json(entrada).path("warning").asText()).contains("ESP32 não conectado");
        var reenvio = request("POST", "/movimentacoes/entrada", dados);
        assertThat(reenvio.statusCode()).isEqualTo(200);
        assertThat(json(reenvio).path("id").asLong()).isEqualTo(json(entrada).path("id").asLong());
        assertThat(pessoas.count()).isEqualTo(totalPessoas);
        assertThat(veiculos.count()).isEqualTo(totalVeiculos);
        assertThat(eventos.count()).isEqualTo(totalEventos + 1);
        assertThat(json(request("GET", "/pessoas", null)).toString()).doesNotContain("Ana Visitante");
        assertThat(json(request("GET", "/veiculos", null)).toString()).doesNotContain("VIS-" + placa.substring(3));
        assertThat(json(request("GET", "/movimentacoes/ativos", null)).toString()).contains("VIS-" + placa.substring(3));
        long entradaId = json(entrada).path("id").asLong();
        assertThat(eventos.findById(entradaId).orElseThrow().getVeiculo()).isNull();
        assertThat(request("POST", "/cancela/abrir", Map.of("eventId", entradaId)).statusCode()).isEqualTo(503);
        var dadosSaida = Map.of("plate", placa, "requestId", UUID.randomUUID().toString());
        var saida = request("POST", "/movimentacoes/saida", dadosSaida);
        assertThat(saida.statusCode()).isEqualTo(200);
        assertThat(json(saida).path("owner").asText()).isEqualTo("Ana Visitante");
        assertThat(json(saida).path("model").asText()).isEqualTo("Fiat Uno");
        assertThat(json(saida).path("vehicleId").isNull()).isTrue();
        var saidaRepetida = request("POST", "/movimentacoes/saida", dadosSaida);
        assertThat(saidaRepetida.statusCode()).isEqualTo(200);
        assertThat(json(saidaRepetida).path("id").asLong()).isEqualTo(json(saida).path("id").asLong());
        assertThat(eventos.findById(entradaId).orElseThrow().getPlacaVisitanteAtivo()).isNull();
        assertThat(json(request("GET", "/movimentacoes/ativos", null)).toString()).doesNotContain("VIS-" + placa.substring(3));
        assertThat(json(request("GET", "/historico", null)).toString()).contains("VIS-" + placa.substring(3), "Ana Visitante");
        assertThat(request("POST", "/cancela/abrir", Map.of("eventId", entradaId)).statusCode()).isEqualTo(409);

        var novaEntrada = request("POST", "/movimentacoes/entrada", Map.of("plate", placa,
                "requestId", UUID.randomUUID().toString()));
        assertThat(json(novaEntrada).path("code").asText()).isEqualTo("VEICULO_NAO_CADASTRADO");
        assertThat(request("POST", "/movimentacoes/entrada", Map.of("plate", placa,
                "requestId", UUID.randomUUID().toString(),
                "visitor", Map.of("responsibleName", "Outro visitante", "model", "Celta"))).statusCode()).isEqualTo(200);
        assertThat(pessoas.count()).isEqualTo(totalPessoas);
        assertThat(veiculos.count()).isEqualTo(totalVeiculos);
    }

    @Test
    void impedeEntradasESaidasConcorrentesDeVisitanteSemDuplicarHistorico() throws Exception {
        String placa = "VIS" + SEQUENCIA.incrementAndGet();
        long totalPessoas = pessoas.count();
        long totalVeiculos = veiculos.count();
        long totalEventos = eventos.count();
        var entrada1 = httpRequest("POST", "/movimentacoes/entrada", Map.of("plate", placa,
                "requestId", UUID.randomUUID().toString(),
                "visitor", Map.of("responsibleName", "Visitante", "model", "Uno")));
        var entrada2 = httpRequest("POST", "/movimentacoes/entrada", Map.of("plate", placa,
                "requestId", UUID.randomUUID().toString(),
                "visitor", Map.of("responsibleName", "Visitante", "model", "Uno")));
        var futuro1 = client.sendAsync(entrada1, HttpResponse.BodyHandlers.ofString());
        var futuro2 = client.sendAsync(entrada2, HttpResponse.BodyHandlers.ofString());
        assertThat(List.of(futuro1.get().statusCode(), futuro2.get().statusCode())).containsExactlyInAnyOrder(200, 409);
        var saida1 = httpRequest("POST", "/movimentacoes/saida",
                Map.of("plate", placa, "requestId", UUID.randomUUID().toString()));
        var saida2 = httpRequest("POST", "/movimentacoes/saida",
                Map.of("plate", placa, "requestId", UUID.randomUUID().toString()));
        futuro1 = client.sendAsync(saida1, HttpResponse.BodyHandlers.ofString());
        futuro2 = client.sendAsync(saida2, HttpResponse.BodyHandlers.ofString());
        assertThat(List.of(futuro1.get().statusCode(), futuro2.get().statusCode())).containsExactlyInAnyOrder(200, 409);
        assertThat(pessoas.count()).isEqualTo(totalPessoas);
        assertThat(veiculos.count()).isEqualTo(totalVeiculos);
        assertThat(eventos.count()).isEqualTo(totalEventos + 2);
    }

    @Test
    void desfazCadastroSeEntradaFalharENaoContornaVeiculoBloqueado() throws Exception {
        String placa = "VIS" + SEQUENCIA.incrementAndGet();
        long totalPessoas = pessoas.count();
        long totalVeiculos = veiculos.count();
        assertThat(request("POST", "/movimentacoes/entrada", Map.of("plate", placa,
                "requestId", UUID.randomUUID().toString(), "imageId", Long.MAX_VALUE,
                "visitor", Map.of("responsibleName", "Ana", "model", "Uno"))).statusCode()).isEqualTo(409);
        assertThat(pessoas.count()).isEqualTo(totalPessoas);
        assertThat(veiculos.count()).isEqualTo(totalVeiculos);

        String bloqueada = novoVeiculo();
        var veiculo = veiculos.findByPlaca(bloqueada).orElseThrow();
        veiculo.setAutorizado(false);
        veiculos.saveAndFlush(veiculo);
        var response = request("POST", "/movimentacoes/entrada", Map.of("plate", bloqueada,
                "requestId", UUID.randomUUID().toString(),
                "visitor", Map.of("responsibleName", "Outro nome", "model", "Outro modelo")));
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(json(response).path("message").asText()).contains("sem autorização ativa");
        assertThat(veiculos.findByPlaca(bloqueada).orElseThrow().getModelo()).isEqualTo("Teste");
        assertThat(pessoas.count()).isEqualTo(totalPessoas);
    }

    private Map<String, Object> movimento(String placa, long imagemId, String requestId) {
        return Map.of("plate", placa, "imageId", imagemId, "requestId", requestId);
    }

    private long upload() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        var response = uploadBytes(output.toByteArray());
        assertThat(response.statusCode()).isEqualTo(201);
        return json(response).path("id").asLong();
    }

    private HttpResponse<String> uploadBytes(byte[] bytes) throws Exception {
        return uploadBytes("/imagens", bytes);
    }

    private HttpResponse<String> uploadBytes(String path, byte[] bytes) throws Exception {
        String boundary = "test-boundary";
        var output = new ByteArrayOutputStream();
        output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"teste.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(bytes);
        output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path))
                .header("Authorization", "Bearer " + token).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(output.toByteArray())).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest httpRequest(String method, String path, Object body) {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
    }

    private HttpResponse<String> request(String method, String path, Object body) throws Exception {
        return client.send(httpRequest(method, path, body), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) {
        return mapper.readTree(response.body());
    }
}
