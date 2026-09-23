package br.gov.sp.fatec.itu.estacionatec_api.repositories;

import br.gov.sp.fatec.itu.estacionatec_api.entities.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    java.util.Optional<Usuario> findByUsernameIgnoreCase(String username);
    java.util.Optional<Usuario> findByPessoaEmailIgnoreCase(String email);
    boolean existsByPessoaId(Long pessoaId);
}
