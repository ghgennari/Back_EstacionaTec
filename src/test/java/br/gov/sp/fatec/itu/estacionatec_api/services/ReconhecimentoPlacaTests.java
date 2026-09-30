package br.gov.sp.fatec.itu.estacionatec_api.services;

import br.gov.sp.fatec.itu.estacionatec_api.integration.CameraClient;
import br.gov.sp.fatec.itu.estacionatec_api.integration.OcrClient;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReconhecimentoPlacaTests {
    private final CameraClient camera = mock(CameraClient.class);
    private final OcrClient ocr = mock(OcrClient.class);
    private final ReconhecimentoPlacaService service = new ReconhecimentoPlacaService(camera, ocr, true, 70);

    @Test
    void confirmaEmDoisQuadrosESuprimeRepeticoesAteVeiculoSair() {
        long now = System.currentTimeMillis();
        var leitura = List.of(new OcrClient.Leitura("ABC1D23", 92));
        service.processar(leitura, now);
        assertThat(service.acompanhar().plate()).isNull();
        service.processar(leitura, now + 1500);
        var primeira = service.acompanhar();
        assertThat(primeira.plate()).isEqualTo("ABC1D23");
        service.processar(leitura, now + 3000);
        assertThat(service.acompanhar().detectionId()).isEqualTo(primeira.detectionId());
        assertThat(service.acompanhar().detectedAt().toEpochMilli()).isEqualTo(now + 3000);
        service.processar(List.of(), now + 4500);
        assertThat(service.acompanhar().detectionId()).isEqualTo(primeira.detectionId());
        service.processar(List.of(), now + 13000);
        service.processar(leitura, now + 14500);
        service.processar(leitura, now + 16000);
        assertThat(service.acompanhar().detectionId()).isNotEqualTo(primeira.detectionId());
    }

    @Test
    void rejeitaBaixaConfiancaAmbiguidadeELeiturasNaoConsecutivas() {
        long now = System.currentTimeMillis();
        service.processar(List.of(new OcrClient.Leitura("ABC1234", 40)), now);
        assertThat(service.acompanhar().plate()).isNull();
        service.processar(List.of(new OcrClient.Leitura("ABC1234", 95)), now);
        service.processar(List.of(new OcrClient.Leitura("DEF1234", 95)), now + 1500);
        assertThat(service.acompanhar().plate()).isNull();
        service.processar(List.of(new OcrClient.Leitura("ABC1234", 95),
                new OcrClient.Leitura("DEF1234", 95)), now + 3000);
        assertThat(service.acompanhar().message()).contains("Mais de uma");
        assertThat(service.acompanhar().plate()).isNull();
    }

    @Test
    void naoCapturaSemTelaAtivaERecuperaAposFalha() throws Exception {
        service.analisar();
        verifyNoInteractions(camera, ocr);
        service.acompanhar();
        when(camera.capturar()).thenThrow(new IllegalStateException("senha-nao-exibir"));
        service.analisar();
        assertThat(service.acompanhar().status()).isEqualTo("INDISPONIVEL");
        assertThat(service.acompanhar().message()).doesNotContain("senha-nao-exibir");
        doReturn(new byte[]{1}).when(camera).capturar();
        when(ocr.reconhecer(any())).thenReturn(List.of(new OcrClient.Leitura("ABC1234", 95)));
        service.analisar();
        service.analisar();
        assertThat(service.acompanhar().plate()).isEqualTo("ABC1234");
    }

    @Test
    void reconheceFormatosBrasileirosSemInventarCaracteres() {
        assertThat(OcrClient.extrairPlacas("abc-1234\nABC 1D23")).containsExactly("ABC1234", "ABC1D23");
        assertThat(OcrClient.extrairPlacas("BRASIL 2026 ABC12345 XABC1234 ABCI234")).isEmpty();
    }
}
