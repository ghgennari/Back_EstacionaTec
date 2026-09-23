package br.gov.sp.fatec.itu.estacionatec_api.repositories;

import br.gov.sp.fatec.itu.estacionatec_api.entities.Veiculo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VeiculoRepository extends JpaRepository<Veiculo, Long> {
    java.util.Optional<Veiculo> findByPlaca(String placa);
    boolean existsByPessoaId(Long pessoaId);
    java.util.List<Veiculo> findByEstacionadoTrue();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select v from Veiculo v where v.id = :id")
    java.util.Optional<Veiculo> bloquearPorId(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select v from Veiculo v where v.placa = :placa")
    java.util.Optional<Veiculo> bloquearPorPlaca(@org.springframework.data.repository.query.Param("placa") String placa);
}
