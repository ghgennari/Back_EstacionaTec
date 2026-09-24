package br.gov.sp.fatec.itu.estacionatec_api.controllers;

import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.*;
import br.gov.sp.fatec.itu.estacionatec_api.services.*;
import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class MovimentacaoController {
    private final MovimentacaoService movimentacoes;
    private final CancelaService cancela;

    public MovimentacaoController(MovimentacaoService movimentacoes, CancelaService cancela) {
        this.movimentacoes = movimentacoes;
        this.cancela = cancela;
    }

    @PostMapping("/movimentacoes/entrada")
    public MovimentacaoResponse entrada(@Valid @RequestBody MovimentacaoRequest dados, Authentication auth) {
        return registrar(dados, "Entrada", (Long) auth.getPrincipal());
    }

    @PostMapping("/movimentacoes/saida")
    public MovimentacaoResponse saida(@Valid @RequestBody MovimentacaoRequest dados, Authentication auth) {
        return registrar(dados, "Saída", (Long) auth.getPrincipal());
    }

    @GetMapping("/historico")
    public List<HistoricoResponse> historico() {
        return movimentacoes.historico(false);
    }

    @GetMapping("/movimentacoes/ativos")
    public List<HistoricoResponse> ativos() {
        return movimentacoes.historico(true);
    }

    @PostMapping("/cancela/abrir")
    public Mensagem abrir(@Valid @RequestBody CancelaRequest dados, Authentication auth) {
        cancela.abrir(dados.eventId(), (Long) auth.getPrincipal());
        return new Mensagem("Abertura confirmada pelo ESP32.");
    }

    private MovimentacaoResponse registrar(MovimentacaoRequest dados, String tipo, Long usuarioId) {
        Long eventoId = movimentacoes.registrar(dados, tipo, usuarioId);
        if (tipo.equals("Entrada")) movimentacoes.capturarImagemEntrada(eventoId, usuarioId);
        String aviso = null;
        try {
            cancela.abrir(eventoId, usuarioId);
        } catch (RegraNegocioException exception) {
            aviso = exception.getMessage();
        }
        return movimentacoes.resposta(eventoId, aviso);
    }

    @PostMapping("/cancela/abrir-manualmente")
    public Mensagem abrirManualmente(Authentication auth) {
        return new Mensagem(cancela.abrirManualmente((Long) auth.getPrincipal()));
    }
}
