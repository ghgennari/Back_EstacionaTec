package br.gov.sp.fatec.itu.estacionatec_api.repositories;

import br.gov.sp.fatec.itu.estacionatec_api.entities.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from Usuario u where u.id = :id")
    java.util.Optional<Usuario> bloquearPorId(@org.springframework.data.repository.query.Param("id") Long id);

    java.util.Optional<Usuario> findByUsernameIgnoreCase(String username);
    java.util.Optional<Usuario> findByPessoaEmailIgnoreCase(String email);
    boolean existsByPessoaId(Long pessoaId);
    boolean existsByPessoaEmailIgnoreCaseAndPessoaIdNot(String email, Long pessoaId);
    boolean existsByPessoaEmailIgnoreCase(String email);
    boolean existsByAtivoTrueAndPessoaAtivoTrueAndPerfilNomeAndIdNot(String perfil, Long id);
    boolean existsByAtivoTrueAndPessoaAtivoTrueAndPerfilNomeAndPessoaIdNot(String perfil, Long pessoaId);
    boolean existsByPessoaIdAndAtivoTrueAndPerfilNome(Long pessoaId, String perfil);
}
