package br.gov.sp.fatec.itu.estacionatec_api.services;

import br.gov.sp.fatec.itu.estacionatec_api.entities.LogSistema;
import br.gov.sp.fatec.itu.estacionatec_api.repositories.LogSistemaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AuditoriaService {
    private final LogSistemaRepository logs;

    public AuditoriaService(LogSistemaRepository logs) {
        this.logs = logs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Long usuarioId, String acao, String descricao) {
        LogSistema log = new LogSistema();
        log.setUsuarioId(usuarioId);
        log.setAcao(acao);
        log.setDescricao(descricao);
        logs.save(log);
    }
}
