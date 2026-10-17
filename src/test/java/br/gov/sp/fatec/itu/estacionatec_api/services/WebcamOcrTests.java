package br.gov.sp.fatec.itu.estacionatec_api.services;

import br.gov.sp.fatec.itu.estacionatec_api.integration.OcrClient;
import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class WebcamOcrTests {
    private final EntradaOcrService entradas = mock(EntradaOcrService.class);
    private final OcrClient ocr = mock(OcrClient.class);
    private final AtomicLong now = new AtomicLong(System.currentTimeMillis());
    private final WebcamOcrService service = new WebcamOcrService(entradas, ocr, true, 70, now::get);

    @Test
    void confirmaDoisQuadrosDaMesmaSessaoEEnviaFotoDaWebcam() throws Exception {
        byte[] frame = {1, 2};
        when(ocr.reconhecer(frame)).thenReturn(List.of(new OcrClient.Leitura("ABC1234", 95)));
        when(entradas.registrar(any(), eq(1L), same(frame))).thenAnswer(i -> i.getArgument(0));
        UUID sessao = UUID.randomUUID();
        assertThat(service.ler(frame, 1L, sessao).status()).isEqualTo("CONFIRMANDO");
        now.addAndGet(1500);
        var confirmada = service.ler(frame, 1L, sessao);
        assertThat(confirmada.status()).isEqualTo("DETECTADA");
        now.addAndGet(1500);
        assertThat(service.ler(frame, 1L, sessao).detectionId()).isEqualTo(confirmada.detectionId());
        verify(entradas, times(3)).registrar(any(), eq(1L), same(frame));
    }

    @Test
    void naoMisturaUsuariosOuSessoesENaoAceitaEnviosRapidos() throws Exception {
        when(ocr.reconhecer(any())).thenReturn(List.of(new OcrClient.Leitura("ABC1234", 95)));
        when(entradas.registrar(any(), anyLong(), any())).thenAnswer(i -> i.getArgument(0));
        UUID sessao = UUID.randomUUID();
        service.ler(new byte[]{1}, 1L, sessao);
        assertThatThrownBy(() -> service.ler(new byte[]{1}, 1L, sessao)).isInstanceOf(RegraNegocioException.class);
        now.addAndGet(1500);
        assertThat(service.ler(new byte[]{1}, 2L, sessao).status()).isEqualTo("CONFIRMANDO");
        assertThat(service.ler(new byte[]{1}, 1L, UUID.randomUUID()).status()).isEqualTo("CONFIRMANDO");
    }

    @Test
    void falhaDeLeituraDescartaConfirmacaoAnteriorSemExporDetalhes() throws Exception {
        UUID sessao = UUID.randomUUID();
        when(entradas.registrar(any(), anyLong(), any())).thenAnswer(i -> i.getArgument(0));
        when(ocr.reconhecer(any())).thenReturn(List.of(new OcrClient.Leitura("ABC1234", 95)));
        service.ler(new byte[]{1}, 1L, sessao);
        now.addAndGet(1500);
        when(ocr.reconhecer(any())).thenThrow(new IllegalStateException("privado"));
        assertThatThrownBy(() -> service.ler(new byte[]{1}, 1L, sessao))
                .isInstanceOf(RegraNegocioException.class).hasMessageNotContaining("privado");
        doReturn(List.of(new OcrClient.Leitura("ABC1234", 95))).when(ocr).reconhecer(any());
        assertThat(service.ler(new byte[]{1}, 1L, sessao).status()).isEqualTo("CONFIRMANDO");
    }
}
