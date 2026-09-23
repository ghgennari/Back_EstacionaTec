package br.gov.sp.fatec.itu.estacionatec_api.repositories;

import br.gov.sp.fatec.itu.estacionatec_api.entities.Pessoa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PessoaRepository extends JpaRepository<Pessoa, Long> {
    boolean existsByDocumento(String documento);
    boolean existsByEmailIgnoreCase(String email);
    java.util.Optional<Pessoa> findByEmailIgnoreCase(String email);
}
