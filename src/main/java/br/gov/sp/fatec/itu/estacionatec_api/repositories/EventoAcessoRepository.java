package br.gov.sp.fatec.itu.estacionatec_api.repositories;

import br.gov.sp.fatec.itu.estacionatec_api.entities.EventoAcesso;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoAcessoRepository extends JpaRepository<EventoAcesso, Long> {
    java.util.Optional<EventoAcesso> findFirstByVeiculoIdOrderByDataHoraDesc(Long veiculoId);
    java.util.Optional<EventoAcesso> findFirstByPlacaOrderByDataHoraDescIdDesc(String placa);
    java.util.Optional<EventoAcesso> findByRequestId(String requestId);
    java.util.Optional<EventoAcesso> findFirstByVeiculoIdAndTipoEventoOrderByDataHoraDesc(Long veiculoId, String tipoEvento);
    java.util.Optional<EventoAcesso> findByEntradaId(Long entradaId);
    boolean existsByVeiculoId(Long veiculoId);
    java.util.List<EventoAcesso> findAllByOrderByDataHoraDesc();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from EventoAcesso e where e.placaVisitanteAtivo = :placa")
    java.util.Optional<EventoAcesso> bloquearVisitanteAtivo(
            @org.springframework.data.repository.query.Param("placa") String placa);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from EventoAcesso e where e.id = :id")
    java.util.Optional<EventoAcesso> bloquear(@org.springframework.data.repository.query.Param("id") Long id);
}
