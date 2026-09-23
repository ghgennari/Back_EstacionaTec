package br.gov.sp.fatec.itu.estacionatec_api.security;

import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.*;
import br.gov.sp.fatec.itu.estacionatec_api.entities.Usuario;
import br.gov.sp.fatec.itu.estacionatec_api.repositories.UsuarioRepository;
import br.gov.sp.fatec.itu.estacionatec_api.services.CadastroService;
import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessaoService {
    private record Sessao(Long usuarioId, Instant expiraEm) {
    }

    private final Map<String, Sessao> sessoes = new ConcurrentHashMap<>();
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final CadastroService cadastro;

    public SessaoService(UsuarioRepository usuarios, PasswordEncoder encoder, CadastroService cadastro) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.cadastro = cadastro;
    }

    public LoginResponse login(LoginRequest dados) {
        Usuario usuario = usuarios.findByPessoaEmailIgnoreCase(dados.email().trim())
                .or(() -> usuarios.findByUsernameIgnoreCase(dados.email().trim()))
                .orElseThrow(this::credenciaisInvalidas);
        if (!usuario.isAtivo() || !usuario.getPessoa().isAtivo()
                || !encoder.matches(dados.password(), usuario.getSenha())) {
            throw credenciaisInvalidas();
        }
        sessoes.entrySet().removeIf(entry -> entry.getValue().expiraEm().isBefore(Instant.now()));
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        sessoes.put(token, new Sessao(usuario.getId(), Instant.now().plusSeconds(8 * 3600)));
        return new LoginResponse(token, cadastro.usuarioDto(usuario));
    }

    public Usuario autenticar(String token) {
        Sessao sessao = sessoes.get(token);
        if (sessao == null || sessao.expiraEm().isBefore(Instant.now())) {
            sessoes.remove(token);
            return null;
        }
        return usuarios.findById(sessao.usuarioId())
                .filter(u -> u.isAtivo() && u.getPessoa().isAtivo()).orElse(null);
    }

    public void logout(String token) {
        sessoes.remove(token);
    }

    private RegraNegocioException credenciaisInvalidas() {
        return new RegraNegocioException(HttpStatus.UNAUTHORIZED, "Usuário ou senha inválidos.");
    }
}
