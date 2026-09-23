package br.gov.sp.fatec.itu.estacionatec_api.controllers;

import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.*;
import br.gov.sp.fatec.itu.estacionatec_api.security.SessaoService;
import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final SessaoService sessoes;

    public AuthController(SessaoService sessoes) {
        this.sessoes = sessoes;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest dados) {
        return sessoes.login(dados);
    }

    @PostMapping("/logout")
    public Mensagem logout(@RequestHeader("Authorization") String authorization) {
        sessoes.logout(authorization.substring(7));
        return new Mensagem("Sessão encerrada.");
    }

    @PostMapping("/recuperar-senha")
    public Mensagem recuperarSenha() {
        throw new RegraNegocioException(HttpStatus.SERVICE_UNAVAILABLE,
                "O envio de e-mails ainda não está configurado. Solicite a redefinição ao administrador.");
    }
}
