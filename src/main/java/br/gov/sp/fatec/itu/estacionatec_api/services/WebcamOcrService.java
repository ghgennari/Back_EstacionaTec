package br.gov.sp.fatec.itu.estacionatec_api.services;

import br.gov.sp.fatec.itu.estacionatec_api.integration.*;
import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class WebcamOcrService {
    private record Chave(Long usuario, UUID sessao) { }
    private record Leitor(ReconhecimentoPlacaService reconhecimento, long atualizado) { }
    private final Map<Chave, Leitor> leitores = new LinkedHashMap<>();
    private final OcrClient ocr;
    private final EntradaOcrService entradas;
    private final boolean enabled;
    private final float minimum;
    private final java.util.function.LongSupplier clock;

    @org.springframework.beans.factory.annotation.Autowired
    public WebcamOcrService(EntradaOcrService entradas,
            @Value("${estacionatec.ocr.data-path:}") String dataPath,
            @Value("${estacionatec.ocr.enabled:true}") boolean enabled,
            @Value("${estacionatec.ocr.minimum-confidence:70}") float minimum) {
        this(entradas, new OcrClient(dataPath, 0, 0, 1, 1), enabled, minimum, System::currentTimeMillis);
    }

    WebcamOcrService(EntradaOcrService entradas, OcrClient ocr, boolean enabled, float minimum,
            java.util.function.LongSupplier clock) {
        this.entradas = entradas;
        this.ocr = ocr;
        this.enabled = enabled;
        this.minimum = minimum;
        this.clock = clock;
    }

    public synchronized ReconhecimentoPlacaService.Estado ler(byte[] quadro, Long usuario, UUID sessao) {
        if (!enabled) return new ReconhecimentoPlacaService.Estado(false, "DESATIVADO",
                "OCR desativado no servidor.", null, null, null, null);
        if (quadro.length == 0 || quadro.length > 5 * 1024 * 1024)
            throw new RegraNegocioException(HttpStatus.BAD_REQUEST, "Envie uma imagem de ate 5 MB.");
        long now = clock.getAsLong();
        leitores.entrySet().removeIf(e -> now - e.getValue().atualizado() > 60_000);
        Chave chave = new Chave(usuario, sessao);
        var leitor = leitores.get(chave);
        if (leitor != null && now - leitor.atualizado() < 500)
            throw new RegraNegocioException(HttpStatus.TOO_MANY_REQUESTS, "Aguarde o proximo quadro da webcam.");
        var reconhecimento = leitor == null
                ? new ReconhecimentoPlacaService(null, ocr, true, minimum) : leitor.reconhecimento();
        try {
            var leituras = ocr.reconhecer(quadro);
            var estado = reconhecimento.processarLocal(leituras, clock.getAsLong());
            leitores.put(chave, new Leitor(reconhecimento, clock.getAsLong()));
            if (leitores.size() > 128) leitores.remove(leitores.keySet().iterator().next());
            return entradas.registrar(estado, usuario, quadro);
        } catch (RegraNegocioException exception) {
            throw exception;
        } catch (Exception | LinkageError exception) {
            leitores.remove(chave);
            throw new RegraNegocioException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Nao foi possivel ler o quadro da webcam. Verifique a imagem e o OCR.");
        }
    }
}
