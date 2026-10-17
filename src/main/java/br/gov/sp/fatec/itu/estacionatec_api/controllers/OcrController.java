package br.gov.sp.fatec.itu.estacionatec_api.controllers;

import br.gov.sp.fatec.itu.estacionatec_api.services.ReconhecimentoPlacaService;
import br.gov.sp.fatec.itu.estacionatec_api.services.EntradaOcrService;
import org.springframework.security.core.Authentication;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/camera/ocr")
public class OcrController {
    private final ReconhecimentoPlacaService reconhecimento;
    private final EntradaOcrService entradas;

    public OcrController(ReconhecimentoPlacaService reconhecimento, EntradaOcrService entradas) {
        this.reconhecimento = reconhecimento;
        this.entradas = entradas;
    }

    @PostMapping("/acompanhar")
    public ResponseEntity<ReconhecimentoPlacaService.Estado> acompanhar(Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(entradas.registrar(reconhecimento.acompanhar(), (Long) auth.getPrincipal()));
    }
}
