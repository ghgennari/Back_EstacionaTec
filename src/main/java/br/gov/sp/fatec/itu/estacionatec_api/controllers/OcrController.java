package br.gov.sp.fatec.itu.estacionatec_api.controllers;

import br.gov.sp.fatec.itu.estacionatec_api.services.ReconhecimentoPlacaService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/camera/ocr")
public class OcrController {
    private final ReconhecimentoPlacaService reconhecimento;

    public OcrController(ReconhecimentoPlacaService reconhecimento) {
        this.reconhecimento = reconhecimento;
    }

    @PostMapping("/acompanhar")
    public ResponseEntity<ReconhecimentoPlacaService.Estado> acompanhar() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reconhecimento.acompanhar());
    }
}
