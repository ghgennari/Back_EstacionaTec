package br.gov.sp.fatec.itu.estacionatec_api;

import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.*;
import br.gov.sp.fatec.itu.estacionatec_api.entities.Pessoa;
import br.gov.sp.fatec.itu.estacionatec_api.integration.*;
import br.gov.sp.fatec.itu.estacionatec_api.repositories.*;
import br.gov.sp.fatec.itu.estacionatec_api.services.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = { "spring.datasource.url=jdbc:h2:mem:entrada-ocr;DB_CLOSE_DELAY=-1",
        "estacionatec.storage=target/ocr-images", "estacionatec.ocr.enabled=false" })
class EntradaOcrIntegracaoTests {
    @Autowired EntradaOcrService automatico;
    @Autowired CadastroService cadastro;
    @Autowired PessoaRepository pessoas;
    @Autowired UsuarioRepository usuarios;
    @Autowired VeiculoRepository veiculos;
    @Autowired EventoAcessoRepository eventos;
    @MockitoBean CameraClient camera;
    @MockitoBean Esp32Client esp32;

    private long operador() { return usuarios.findByUsernameIgnoreCase("joao.carlos").orElseThrow().getId(); }
    private ReconhecimentoPlacaService.Estado detectar(String placa) {
        return new ReconhecimentoPlacaService.Estado(true, "DETECTADA", "", UUID.randomUUID().toString(),
                placa, 95f, Instant.now());
    }
    private long cadastrar(String placa, boolean autorizado, boolean ativo) {
        var pessoa = new Pessoa();
        pessoa.setNome("Teste OCR");
        pessoa.setAtivo(ativo);
        pessoa.setCategoria("Aluno");
        pessoas.save(pessoa);
        return cadastro.salvarVeiculo(null, new VeiculoRequest(placa, "Carro", "Azul", pessoa.getId(),
                "Carro", "Marca", autorizado)).id();
    }

    @Test
    void persisteEntradaFotoEAuditoriaDoOperadorSemDuplicarAoDetectarNovamente() throws Exception {
        long veiculo = cadastrar("OCR1001", true, true);
        try (var foto = getClass().getResourceAsStream("/ocr/placa-ABC1234.png")) {
            when(camera.capturar()).thenReturn(foto.readAllBytes());
        }
        var deteccao = detectar("OCR1001");
        assertThat(automatico.registrar(deteccao, operador()).status()).isEqualTo("ENTRADA_REGISTRADA");
        var evento = eventos.findByRequestId(deteccao.detectionId()).orElseThrow();
        assertThat(evento.getUsuarioId()).isEqualTo(operador());
        assertThat(evento.getImagem()).isNotNull();
        assertThat(veiculos.findById(veiculo).orElseThrow().isEstacionado()).isTrue();
        assertThat(automatico.registrar(detectar("OCR1001"), operador()).status()).isEqualTo("ENTRADA_NEGADA");
        verify(esp32, times(1)).abrir(evento.getId());
    }

    @Test
    void entradaDaWebcamSalvaQuadroLocalMesmoSemCameraIp() throws Exception {
        cadastrar("OCR1004", true, true);
        byte[] quadro;
        try (var foto = getClass().getResourceAsStream("/ocr/placa-ABC1234.png")) {
            quadro = foto.readAllBytes();
        }
        var deteccao = detectar("OCR1004");
        assertThat(automatico.registrar(deteccao, operador(), quadro).status()).isEqualTo("ENTRADA_REGISTRADA");
        var evento = eventos.findByRequestId(deteccao.detectionId()).orElseThrow();
        assertThat(evento.getImagem().getOrigem()).isEqualTo("WEBCAM");
        assertThat(evento.getImagem().isUtilizada()).isTrue();
        verifyNoInteractions(camera);
    }

    @Test
    void desconhecidoNaoAutorizadoEProprietarioInativoNaoEntram() {
        cadastrar("OCR1002", false, true);
        cadastrar("OCR1003", true, false);
        for (String placa : new String[]{"OCR1002", "OCR1003", "OCR9999"}) {
            var deteccao = detectar(placa);
            assertThat(automatico.registrar(deteccao, operador()).status()).isEqualTo("ENTRADA_NEGADA");
            assertThat(eventos.findByRequestId(deteccao.detectionId())).isEmpty();
        }
        verifyNoInteractions(esp32);
    }
}
