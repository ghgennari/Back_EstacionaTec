package br.gov.sp.fatec.itu.estacionatec_api.controllers;

import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.*;
import br.gov.sp.fatec.itu.estacionatec_api.services.CadastroService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;

@RestController
@RequestMapping("/api/veiculos")
public class VeiculoController {
    private final CadastroService service;

    public VeiculoController(CadastroService service) {
        this.service = service;
    }

    @GetMapping
    public List<VeiculoResponse> listar() {
        return service.veiculos();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VeiculoResponse criar(@Valid @RequestBody VeiculoRequest dados) {
        return service.salvarVeiculo(null, dados);
    }

    @PutMapping("/{id}")
    public VeiculoResponse atualizar(@PathVariable Long id, @Valid @RequestBody VeiculoRequest dados) {
        return service.salvarVeiculo(id, dados);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluirVeiculo(id);
    }
}
