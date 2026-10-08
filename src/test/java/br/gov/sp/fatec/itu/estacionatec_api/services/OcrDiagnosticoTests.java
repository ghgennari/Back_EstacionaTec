package br.gov.sp.fatec.itu.estacionatec_api.services;

import br.gov.sp.fatec.itu.estacionatec_api.integration.CameraClient;
import br.gov.sp.fatec.itu.estacionatec_api.integration.OcrClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class OcrDiagnosticoTests {
    private final CameraClient camera = mock(CameraClient.class);
    private final OcrClient ocr = mock(OcrClient.class);
    private final ReconhecimentoPlacaService service = new ReconhecimentoPlacaService(camera, ocr, true, 70);

    @Test
    void identificaCapturaSemExporCredenciais(CapturedOutput output) {
        when(camera.capturar()).thenThrow(new IllegalStateException("rtsp://admin:segredo@camera"));
        service.acompanhar();
        service.analisar();
        assertThat(output.getAll()).contains("etapa=CAPTURA", "tipo=java.lang.IllegalStateException")
                .doesNotContain("segredo", "rtsp://");
        assertThat(service.acompanhar().status()).isEqualTo("INDISPONIVEL");
        verifyNoInteractions(ocr);
    }

    @Test
    void identificaBibliotecaNativaLimitaRepeticoesERecupera(CapturedOutput output) throws Exception {
        when(camera.capturar()).thenReturn(new byte[]{1});
        when(ocr.reconhecer(any())).thenThrow(new UnsatisfiedLinkError("caminho-privado"));
        service.acompanhar();
        service.analisar();
        service.analisar();
        assertThat(output.getAll()).contains("etapa=OCR", "tipo=java.lang.UnsatisfiedLinkError")
                .doesNotContain("caminho-privado");
        assertThat(output.getAll().split("etapa=OCR", -1)).hasSize(2);
        doReturn(List.of(new OcrClient.Leitura("ABC1234", 95))).when(ocr).reconhecer(any());
        service.analisar();
        service.analisar();
        assertThat(service.acompanhar().plate()).isEqualTo("ABC1234");
        doThrow(new UnsatisfiedLinkError("caminho-privado")).when(ocr).reconhecer(any());
        service.analisar();
        assertThat(output.getAll().split("etapa=OCR", -1)).hasSize(3);
    }
}
