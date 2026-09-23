package br.gov.sp.fatec.itu.estacionatec_api.controllers;

import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.*;
import br.gov.sp.fatec.itu.estacionatec_api.services.CadastroService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;

@RestController
@RequestMapping("/api/pessoas")
public class PessoaController {
    private final CadastroService service;

    public PessoaController(CadastroService service) {
        this.service = service;
    }

    @GetMapping
    public List<PessoaResponse> listar() {
        return service.pessoas();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PessoaResponse criar(@Valid @RequestBody PessoaRequest dados) {
        return service.salvarPessoa(null, dados);
    }

    @PutMapping("/{id}")
    public PessoaResponse atualizar(@PathVariable Long id, @Valid @RequestBody PessoaRequest dados) {
        return service.salvarPessoa(id, dados);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluirPessoa(id);
    }
}
