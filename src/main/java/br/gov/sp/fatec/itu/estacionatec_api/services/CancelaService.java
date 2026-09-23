package br.gov.sp.fatec.itu.estacionatec_api.services;

import br.gov.sp.fatec.itu.estacionatec_api.entities.EventoAcesso;
import br.gov.sp.fatec.itu.estacionatec_api.repositories.EventoAcessoRepository;
import br.gov.sp.fatec.itu.estacionatec_api.integration.Esp32Client;
import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class CancelaService {
    private final EventoAcessoRepository eventos;
    private final Esp32Client esp32;
    private final AuditoriaService auditoria;

    public CancelaService(EventoAcessoRepository eventos, Esp32Client esp32, AuditoriaService auditoria) {
        this.eventos = eventos;
        this.esp32 = esp32;
        this.auditoria = auditoria;
    }

    @Transactional(noRollbackFor = RegraNegocioException.class)
    public void abrir(Long eventoId, Long usuarioId) {
        EventoAcesso evento = eventos.bloquear(eventoId)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Registre uma movimentação antes de abrir o portão."));
        if (!evento.isAcessoAutorizado() || !usuarioId.equals(evento.getUsuarioId())
                || evento.getDataHora().isBefore(LocalDateTime.now().minusMinutes(2))
                || !evento.getVeiculo().isAutorizado() || !evento.getVeiculo().getPessoa().isAtivo()) {
            throw RegraNegocioException.conflito("Abertura não autorizada para este evento ou autorização expirada.");
        }
        if (evento.getStatusCancela().equals("ABERTA")) {
            return;
        }
        Long ultimoEventoId = eventos.findFirstByVeiculoIdOrderByDataHoraDesc(evento.getVeiculo().getId())
                .orElseThrow().getId();
        if (!ultimoEventoId.equals(eventoId)) {
            throw RegraNegocioException.conflito("Já existe uma movimentação mais recente para este veículo.");
        }
        try {
            esp32.abrir(eventoId);
            evento.setStatusCancela("ABERTA");
            evento.setObservacao("Abertura confirmada pelo ESP32.");
            auditoria.registrar(usuarioId, "CANCELA_ABERTA", "Evento " + eventoId);
        } catch (RegraNegocioException exception) {
            evento.setStatusCancela("ERRO");
            evento.setObservacao(exception.getMessage());
            auditoria.registrar(usuarioId, "FALHA_CANCELA", "Evento " + eventoId + ": " + exception.getMessage());
            throw exception;
        }
    }
}
