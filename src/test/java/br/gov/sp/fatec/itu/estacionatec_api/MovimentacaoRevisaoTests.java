package br.gov.sp.fatec.itu.estacionatec_api;

import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.*;
import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import br.gov.sp.fatec.itu.estacionatec_api.integration.CameraClient;
import br.gov.sp.fatec.itu.estacionatec_api.repositories.*;
import br.gov.sp.fatec.itu.estacionatec_api.services.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:movimentacao-revisao;DB_CLOSE_DELAY=-1",
        "estacionatec.storage=target/revisao-images", "estacionatec.ocr.enabled=false"
})
class MovimentacaoRevisaoTests {
    @Autowired CadastroService cadastro;
    @Autowired MovimentacaoService movimentos;
    @Autowired PessoaRepository pessoas;
    @Autowired UsuarioRepository usuarios;
    @Autowired VeiculoRepository veiculos;
    @Autowired EventoAcessoRepository eventos;
    @MockitoBean CameraClient camera;

    private long operador() {
        return usuarios.findByUsernameIgnoreCase("joao.carlos").orElseThrow().getId();
    }

    private VeiculoRequest veiculo(String placa) {
        return new VeiculoRequest(placa, "Teste", "Azul", pessoas.findAll().getFirst().getId(), "Carro", "Marca", true);
    }

    private MovimentacaoRequest visitante(String placa) {
        return new MovimentacaoRequest(placa, null, UUID.randomUUID().toString(), new VisitanteRequest("Visitante", "Uno"));
    }

    @Test
    void permiteCadastroAposSaidaDoVisitante() {
        movimentos.registrar(visitante("REV1001"), "Entrada", operador());
        movimentos.registrar(new MovimentacaoRequest("REV1001", null, UUID.randomUUID().toString(), null), "Saída", operador());
        assertThat(cadastro.salvarVeiculo(null, veiculo("REV1001")).plate()).isEqualTo("REV-1001");
    }

    @Test
    void impedeCadastroEEdicaoParaPlacaDeVisitanteAtivo() {
        movimentos.registrar(visitante("REV1002"), "Entrada", operador());
        assertThatThrownBy(() -> cadastro.salvarVeiculo(null, veiculo("REV1002")))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("visitante");
        var cadastrado = cadastro.salvarVeiculo(null, veiculo("REV1003"));
        movimentos.registrar(new MovimentacaoRequest("REV1003", null, UUID.randomUUID().toString(), null), "Entrada", operador());
        assertThatThrownBy(() -> cadastro.salvarVeiculo(cadastrado.id(), veiculo("REV1002")))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("visitante");
        assertThat(veiculos.findById(cadastrado.id()).orElseThrow().getPlaca()).isEqualTo("REV1003");
        assertThat(veiculos.findById(cadastrado.id()).orElseThrow().isEstacionado()).isTrue();
    }

    @Test
    void cadastroEEntradaVisitanteConcorrentesNaoCriamDuasIdentidadesAtivas() throws Exception {
        long operador = operador();
        var dados = veiculo("REV1004");
        var visita = visitante("REV1004");
        var inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> tentar(inicio, () -> cadastro.salvarVeiculo(null, dados)));
            var b = executor.submit(() -> tentar(inicio, () -> movimentos.registrar(visita, "Entrada", operador)));
            inicio.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }

    private boolean tentar(CountDownLatch inicio, Runnable operacao) throws Exception {
        inicio.await();
        try {
            operacao.run();
            return true;
        } catch (RegraNegocioException exception) {
            assertThat(exception.getStatus().value()).isEqualTo(409);
            return false;
        }
    }

    @Test
    void excluiAposSaidaPreservandoEventosEPermiteRecadastro() {
        var cadastrado = cadastro.salvarVeiculo(null, veiculo("DEL1001"));
        var entrada = movimentos.registrar(new MovimentacaoRequest("DEL1001", null, UUID.randomUUID().toString(), null), "Entrada", operador());
        assertThatThrownBy(() -> cadastro.excluirVeiculo(cadastrado.id()))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("Registre a saída");
        assertThat(veiculos.existsById(cadastrado.id())).isTrue();
        var saida = movimentos.registrar(new MovimentacaoRequest("DEL1001", null, UUID.randomUUID().toString(), null), "Saída", operador());
        var antes = movimentos.historico(false).stream().filter(h -> h.id().equals(entrada)).findFirst().orElseThrow();
        cadastro.excluirVeiculo(cadastrado.id());
        assertThat(veiculos.existsById(cadastrado.id())).isFalse();
        assertThat(eventos.findById(entrada).orElseThrow().getVeiculo()).isNull();
        assertThat(eventos.findById(saida).orElseThrow().getVeiculo()).isNull();
        assertThat(eventos.findById(saida).orElseThrow().getEntradaId()).isEqualTo(entrada);
        var depois = movimentos.historico(false).stream().filter(h -> h.id().equals(entrada)).findFirst().orElseThrow();
        assertThat(depois).isEqualTo(antes);
        assertThat(movimentos.historico(true)).noneMatch(h -> h.id().equals(entrada));
        var novo = cadastro.salvarVeiculo(null, veiculo("DEL1001"));
        assertThat(novo.id()).isNotEqualTo(cadastrado.id());
        movimentos.registrar(new MovimentacaoRequest("DEL1001", null, UUID.randomUUID().toString(), null), "Entrada", operador());
        assertThat(eventos.findById(entrada).orElseThrow().getVeiculo()).isNull();
    }

    @Test
    void exclusaoEEntradaConcorrentesNaoDeixamVeiculoEstacionadoExcluido() throws Exception {
        long operador = operador();
        var cadastrado = cadastro.salvarVeiculo(null, veiculo("DEL1002"));
        var dados = new MovimentacaoRequest("DEL1002", null, UUID.randomUUID().toString(), null);
        var inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> tentar(inicio, () -> cadastro.excluirVeiculo(cadastrado.id())));
            var b = executor.submit(() -> tentar(inicio, () -> movimentos.registrar(dados, "Entrada", operador)));
            inicio.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        var ativo = movimentos.historico(true).stream().filter(h -> h.plate().equals("DEL-1002")).findFirst();
        assertThat(veiculos.existsById(cadastrado.id())).isEqualTo(ativo.isPresent());
    }
}
