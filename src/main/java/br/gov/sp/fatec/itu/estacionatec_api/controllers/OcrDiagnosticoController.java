package br.gov.sp.fatec.itu.estacionatec_api.controllers;

import br.gov.sp.fatec.itu.estacionatec_api.integration.CameraClient;
import br.gov.sp.fatec.itu.estacionatec_api.integration.OcrClient;
import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/camera/ocr")
public class OcrDiagnosticoController {
    public record Resultado(String message, List<OcrClient.Leitura> readings, long elapsedMs) { }
    private final CameraClient camera;
    private final OcrClient ocr;
    private final float minimum;

    public OcrDiagnosticoController(CameraClient camera, OcrClient ocr,
            @Value("${estacionatec.ocr.minimum-confidence:70}") float minimum) {
        this.camera = camera;
        this.ocr = ocr;
        this.minimum = minimum;
    }

    @PostMapping("/diagnosticar")
    public ResponseEntity<Resultado> camera() { return testar(camera.capturar()); }

    @PostMapping(value = "/testar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Resultado> arquivo(@RequestParam("file") MultipartFile arquivo) throws java.io.IOException {
        if (arquivo.getSize() > 5 * 1024 * 1024) {
            throw new RegraNegocioException(HttpStatus.BAD_REQUEST, "Use uma imagem de ate 5 MB.");
        }
        return testar(arquivo.getBytes());
    }

    private ResponseEntity<Resultado> testar(byte[] imagem) {
        long inicio = System.nanoTime();
        try {
            var leituras = ocr.reconhecer(imagem);
            String message = leituras.isEmpty()
                    ? "OCR executado, mas nenhuma placa reconhecida. Ajuste foco, reflexos e enquadramento."
                    : "Leituras: " + leituras.stream().distinct()
                            .map(l -> l.plate() + " (" + Math.round(l.confidence()) + "%)")
                            .collect(java.util.stream.Collectors.joining(", "))
                            + ". Limiar: " + Math.round(minimum) + "%. A entrada exige confirmacao em dois quadros.";
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                    new Resultado(message, leituras, (System.nanoTime() - inicio) / 1_000_000));
        } catch (Exception | LinkageError exception) {
            throw new RegraNegocioException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Falha ao executar OCR. Consulte o diagnostico das bibliotecas e a imagem enviada.");
        }
    }
}
