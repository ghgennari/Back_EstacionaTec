package br.gov.sp.fatec.itu.estacionatec_api.controllers;

import br.gov.sp.fatec.itu.estacionatec_api.services.*;
import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.ImagemResponse;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.UUID;

/** Rotas exclusivas de administrador; nao alteram a fonte IP dos outros computadores. */
@RestController
@RequestMapping("/api/camera/local")
public class CameraLocalController {
    private final ImagemService imagens;
    private final WebcamOcrService ocr;

    public CameraLocalController(ImagemService imagens, WebcamOcrService ocr) {
        this.imagens = imagens;
        this.ocr = ocr;
    }

    @PostMapping(value = "/capturar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImagemResponse capturar(@RequestParam("file") MultipartFile file, Authentication auth) throws IOException {
        return imagens.salvar(file.getBytes(), "WEBCAM", (Long) auth.getPrincipal());
    }

    @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReconhecimentoPlacaService.Estado> ler(@RequestParam("file") MultipartFile file,
            @RequestParam("session") UUID sessao, Authentication auth) throws IOException {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ocr.ler(file.getBytes(), (Long) auth.getPrincipal(), sessao));
    }
}
