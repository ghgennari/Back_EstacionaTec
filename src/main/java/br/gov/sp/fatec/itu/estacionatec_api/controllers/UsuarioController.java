package br.gov.sp.fatec.itu.estacionatec_api.controllers;

import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.*;
import br.gov.sp.fatec.itu.estacionatec_api.services.CadastroService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {
    private final CadastroService service;

    public UsuarioController(CadastroService service) {
        this.service = service;
    }

    @GetMapping
    public List<UsuarioResponse> listar() {
        return service.usuarios();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse criar(@Valid @RequestBody UsuarioRequest dados) {
        return service.salvarUsuario(null, dados);
    }

    @PutMapping("/{id}")
    public UsuarioResponse atualizar(@PathVariable Long id, @Valid @RequestBody UsuarioRequest dados) {
        return service.salvarUsuario(id, dados);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id, org.springframework.security.core.Authentication auth) {
        service.excluirUsuario(id, (Long) auth.getPrincipal());
    }
}
