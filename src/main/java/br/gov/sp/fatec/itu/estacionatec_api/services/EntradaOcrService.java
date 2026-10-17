package br.gov.sp.fatec.itu.estacionatec_api.services;

import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.MovimentacaoRequest;
import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Consome somente deteccoes confirmadas no servidor, nunca placas enviadas pelo navegador. */
@Service
public class EntradaOcrService {
    private final MovimentacaoService movimentos;
    private final CancelaService cancela;
    private final Map<String, ReconhecimentoPlacaService.Estado> resultados = new LinkedHashMap<>();

    public EntradaOcrService(MovimentacaoService movimentos, CancelaService cancela) {
        this.movimentos = movimentos;
        this.cancela = cancela;
    }

    // Serializa os consumidores da mesma camera (inclusive abas/operadores diferentes).
    // MovimentacaoService tambem usa locks no banco e requestId unico.
    public synchronized ReconhecimentoPlacaService.Estado registrar(
            ReconhecimentoPlacaService.Estado estado, Long usuarioId) {
        return registrar(estado, usuarioId, null);
    }

    public synchronized ReconhecimentoPlacaService.Estado registrar(
            ReconhecimentoPlacaService.Estado estado, Long usuarioId, byte[] quadroWebcam) {
        if (!estado.enabled() || !"DETECTADA".equals(estado.status()) || estado.detectionId() == null
                || estado.plate() == null || estado.detectedAt() == null
                || estado.detectedAt().isBefore(Instant.now().minusSeconds(5))) return estado;
        var anterior = resultados.get(estado.detectionId());
        if (anterior != null) return anterior;
        String status;
        String mensagem;
        try {
            Long id = movimentos.registrar(new MovimentacaoRequest(estado.plate(), null,
                    estado.detectionId(), null), "Entrada", usuarioId);
            if (quadroWebcam == null) movimentos.capturarImagemEntrada(id, usuarioId);
            else movimentos.capturarImagemEntradaWebcam(id, usuarioId, quadroWebcam);
            String aviso = null;
            try {
                cancela.abrir(id, usuarioId);
            } catch (RegraNegocioException exception) {
                aviso = exception.getMessage();
            }
            var resposta = movimentos.resposta(id, aviso);
            status = "ENTRADA_REGISTRADA";
            mensagem = "Entrada registrada automaticamente: " + resposta.plate() + ". "
                    + (resposta.warning() == null ? "Abertura confirmada pelo ESP32." : resposta.warning());
        } catch (RegraNegocioException exception) {
            status = "ENTRADA_NEGADA";
            mensagem = "Placa " + estado.plate() + ": " + exception.getMessage();
        }
        var resultado = new ReconhecimentoPlacaService.Estado(true, status, mensagem, estado.detectionId(),
                estado.plate(), estado.confidence(), estado.detectedAt());
        resultados.put(estado.detectionId(), resultado);
        if (resultados.size() > 128) resultados.remove(resultados.keySet().iterator().next());
        return resultado;
    }
}
