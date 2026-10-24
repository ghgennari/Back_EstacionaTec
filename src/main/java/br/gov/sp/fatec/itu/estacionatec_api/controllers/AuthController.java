package br.gov.sp.fatec.itu.estacionatec_api.controllers;

import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.*;
import br.gov.sp.fatec.itu.estacionatec_api.security.SessaoService;
import br.gov.sp.fatec.itu.estacionatec_api.security.LimiteLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final SessaoService sessoes;
    private final LimiteLogin limite;

    public AuthController(SessaoService sessoes, LimiteLogin limite) {
        this.sessoes = sessoes;
        this.limite = limite;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest dados, HttpServletRequest request) {
        limite.iniciar(dados.email(), request.getRemoteAddr());
        LoginResponse resposta = sessoes.login(dados);
        limite.sucesso(dados.email());
        return resposta;
    }

    @PostMapping("/concluir-senha")
    public Mensagem concluir(@Valid @RequestBody ConcluirSenhaRequest dados, HttpServletRequest request) {
        limite.iniciar("troca:" + dados.resetToken(), request.getRemoteAddr());
        sessoes.concluir(dados);
        return new Mensagem("Senha criada. Entre com sua nova senha.");
    }

    @PostMapping("/senha")
    public Mensagem alterar(@Valid @RequestBody TrocaSenhaRequest dados,
            org.springframework.security.core.Authentication auth, HttpServletRequest request) {
        Long id = (Long) auth.getPrincipal();
        limite.iniciar("senha:" + id, request.getRemoteAddr());
        sessoes.alterar(id, dados);
        limite.sucesso("senha:" + id);
        return new Mensagem("Senha alterada. Entre novamente com sua nova senha.");
    }

    @PostMapping("/logout")
    public Mensagem logout(@RequestHeader("Authorization") String authorization) {
        sessoes.logout(authorization.substring(7));
        return new Mensagem("Sessão encerrada.");
    }

}
