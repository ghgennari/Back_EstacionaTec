package br.gov.sp.fatec.itu.estacionatec_api.services;

import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.*;
import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class EntradaOcrTests {
    private final MovimentacaoService movimentos = mock(MovimentacaoService.class);
    private final CancelaService cancela = mock(CancelaService.class);
    private final EntradaOcrService service = new EntradaOcrService(movimentos, cancela);

    private ReconhecimentoPlacaService.Estado estado(String status, Instant instante) {
        return new ReconhecimentoPlacaService.Estado(true, status, "", UUID.randomUUID().toString(),
                "ABC1234", 94f, instante);
    }

    @Test
    void registraSemConfirmacaoUmaVezMesmoComOperadoresConcorrentes() throws Exception {
        var estado = estado("DETECTADA", Instant.now());
        when(movimentos.registrar(any(), eq("Entrada"), anyLong())).thenReturn(123L);
        when(movimentos.resposta(123L, null)).thenReturn(new MovimentacaoResponse(123L, 1L, "ABC-1234",
                "Pessoa", "Aluno", "Carro", null, null, "ABERTA", null));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> service.registrar(estado, 1L));
            var b = pool.submit(() -> service.registrar(estado, 2L));
            assertThat(a.get(5, TimeUnit.SECONDS).status()).isEqualTo("ENTRADA_REGISTRADA");
            assertThat(b.get(5, TimeUnit.SECONDS)).isEqualTo(a.get());
        }
        verify(movimentos).registrar(eq(new MovimentacaoRequest("ABC1234", null, estado.detectionId(), null)),
                eq("Entrada"), anyLong());
        verify(movimentos).capturarImagemEntrada(eq(123L), anyLong());
        verify(cancela).abrir(eq(123L), anyLong());
    }

    @Test
    void fotoLocalSubstituiCapturaIpNaEntradaAutomatica() {
        byte[] quadro = {1, 2, 3};
        when(movimentos.registrar(any(), anyString(), anyLong())).thenReturn(123L);
        when(movimentos.resposta(123L, null)).thenReturn(new MovimentacaoResponse(123L, 1L, "ABC-1234",
                "Pessoa", "Aluno", "Carro", null, null, "ABERTA", null));
        service.registrar(estado("DETECTADA", Instant.now()), 1L, quadro);
        verify(movimentos).capturarImagemEntradaWebcam(123L, 1L, quadro);
        verify(movimentos, never()).capturarImagemEntrada(anyLong(), anyLong());
    }

    @Test
    void naoRegistraLeituraIncompletaOuAntiga() {
        service.registrar(estado("CONFIRMANDO", Instant.now()), 1L);
        service.registrar(estado("DETECTADA", Instant.now().minusSeconds(10)), 1L);
        verifyNoInteractions(movimentos, cancela);
    }

    @Test
    void rejeicaoNaoAbreCancelaNemRepeteTentativaEmCadaPolling() {
        when(movimentos.registrar(any(), anyString(), anyLong()))
                .thenThrow(RegraNegocioException.conflito("Veiculo sem autorizacao ativa."));
        var estado = estado("DETECTADA", Instant.now());
        assertThat(service.registrar(estado, 1L).status()).isEqualTo("ENTRADA_NEGADA");
        service.registrar(estado, 2L);
        verify(movimentos).registrar(any(), anyString(), anyLong());
        verifyNoInteractions(cancela);
    }

    @Test
    void falhaDaCancelaMantemEntradaConfirmadaESemAcionamentoRepetido() {
        when(movimentos.registrar(any(), anyString(), anyLong())).thenReturn(123L);
        doThrow(RegraNegocioException.conflito("ESP32 indisponivel")).when(cancela).abrir(123L, 1L);
        when(movimentos.resposta(123L, "ESP32 indisponivel")).thenReturn(new MovimentacaoResponse(
                123L, 1L, "ABC-1234", "Pessoa", "Aluno", "Carro", null, null, "ERRO", "ESP32 indisponivel"));
        var estado = estado("DETECTADA", Instant.now());
        assertThat(service.registrar(estado, 1L).message()).contains("Entrada registrada", "ESP32 indisponivel");
        service.registrar(estado, 1L);
        verify(cancela).abrir(123L, 1L);
    }
}
