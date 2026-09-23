package br.gov.sp.fatec.itu.estacionatec_api.controllers;

import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.*;
import br.gov.sp.fatec.itu.estacionatec_api.services.ConsultaService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ConsultaController {
    private final ConsultaService consultas;

    public ConsultaController(ConsultaService consultas) {
        this.consultas = consultas;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return consultas.dashboard();
    }

    @GetMapping("/monitoramento/eventos")
    public List<Map<String, Object>> recentes() {
        return consultas.recentes();
    }

    @GetMapping("/relatorios")
    public List<RelatorioResponse> relatorios() {
        return consultas.relatorios();
    }

    @PostMapping("/relatorios")
    public RelatorioResponse gerar(@Valid @RequestBody RelatorioRequest dados, Authentication auth) {
        return consultas.gerar(dados, (Long) auth.getPrincipal());
    }

    @GetMapping("/relatorios/{id}/arquivo")
    public ResponseEntity<byte[]> baixar(@PathVariable Long id) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=relatorio-" + id + ".csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(consultas.baixar(id));
    }
}
