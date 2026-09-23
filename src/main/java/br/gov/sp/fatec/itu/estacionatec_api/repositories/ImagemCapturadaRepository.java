package br.gov.sp.fatec.itu.estacionatec_api.repositories;

import br.gov.sp.fatec.itu.estacionatec_api.entities.ImagemCapturada;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagemCapturadaRepository extends JpaRepository<ImagemCapturada, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select i from ImagemCapturada i where i.id = :id")
    java.util.Optional<ImagemCapturada> bloquear(@org.springframework.data.repository.query.Param("id") Long id);
}
