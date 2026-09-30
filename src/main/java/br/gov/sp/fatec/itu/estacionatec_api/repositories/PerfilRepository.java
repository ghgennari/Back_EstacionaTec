package br.gov.sp.fatec.itu.estacionatec_api.repositories;

import br.gov.sp.fatec.itu.estacionatec_api.entities.Perfil;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerfilRepository extends JpaRepository<Perfil, Long> {
    java.util.Optional<Perfil> findByNome(String nome);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Perfil p where p.nome = :nome")
    java.util.Optional<Perfil> bloquearPorNome(@org.springframework.data.repository.query.Param("nome") String nome);
}
