package br.gov.sp.fatec.itu.estacionatec_api.security;

import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.*;
import br.gov.sp.fatec.itu.estacionatec_api.entities.Usuario;
import br.gov.sp.fatec.itu.estacionatec_api.repositories.UsuarioRepository;
import br.gov.sp.fatec.itu.estacionatec_api.services.CadastroService;
import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessaoService {
    // O hash vincula a sessão à credencial vigente, inclusive entre instâncias.
    private record Sessao(Long usuarioId, Instant expiraEm, String credencial) { }
    private final Map<String, Sessao> sessoes = new ConcurrentHashMap<>();
    private final Map<String, Sessao> trocas = new ConcurrentHashMap<>();
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final CadastroService cadastro;

    public SessaoService(UsuarioRepository usuarios, PasswordEncoder encoder, CadastroService cadastro) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.cadastro = cadastro;
    }

    @Transactional
    public LoginResponse login(LoginRequest dados) {
        Usuario encontrado = usuarios.findByPessoaEmailIgnoreCase(dados.email().trim())
                .or(() -> usuarios.findByUsernameIgnoreCase(dados.email().trim()))
                .orElseThrow(this::credenciaisInvalidas);
        Usuario usuario = bloquear(encontrado.getId());
        if (!ativo(usuario) || !encoder.matches(dados.password(), usuario.getSenha())
                || (usuario.getSenhaTemporariaExpiraEm() != null
                    && !usuario.getSenhaTemporariaExpiraEm().isAfter(Instant.now()))) throw credenciaisInvalidas();
        limparExpiradas();
        String token = token();
        if (usuario.isTrocaSenhaObrigatoria() || !PoliticaSenha.forte(dados.password())) {
            // Nunca entrega um token de acesso antes da troca obrigatória.
            usuario.setTrocaSenhaObrigatoria(true);
            trocas.put(token, new Sessao(usuario.getId(), Instant.now().plusSeconds(600), usuario.getSenha()));
            return new LoginResponse(null, null, true, token);
        }
        sessoes.put(token, new Sessao(usuario.getId(), Instant.now().plusSeconds(8 * 3600), usuario.getSenha()));
        return new LoginResponse(token, cadastro.usuarioDto(usuario), false, null);
    }

    @Transactional(readOnly = true)
    public Usuario autenticar(String token) {
        Sessao sessao = sessoes.get(token);
        if (sessao == null || !sessao.expiraEm().isAfter(Instant.now())) {
            sessoes.remove(token);
            return null;
        }
        return usuarios.findById(sessao.usuarioId()).filter(u -> ativo(u) && !u.isTrocaSenhaObrigatoria()
                && u.getSenha().equals(sessao.credencial())).orElse(null);
    }

    @Transactional
    public RedefinicaoResponse redefinir(Long id, Long atualId) {
        if (id.equals(atualId)) throw invalido("Para sua própria conta, use a engrenagem Alterar senha.");
        Usuario usuario = bloquear(id);
        if (!ativo(usuario)) throw invalido("Ative o usuário e a pessoa antes de redefinir a senha.");
        byte[] bytes = new byte[18];
        new java.security.SecureRandom().nextBytes(bytes);
        String temporaria = "Aa1!" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expira = Instant.now().plusSeconds(1800);
        usuario.setSenha(encoder.encode(temporaria));
        usuario.setTrocaSenhaObrigatoria(true);
        usuario.setSenhaTemporariaExpiraEm(expira);
        revogar(id);
        return new RedefinicaoResponse(temporaria, expira);
    }

    @Transactional
    public void concluir(ConcluirSenhaRequest dados) {
        limparExpiradas();
        Sessao troca = trocas.get(dados.resetToken());
        if (troca == null) throw invalido("A solicitação expirou. Volte ao login e entre novamente.");
        Usuario usuario = bloquear(troca.usuarioId());
        if (!ativo(usuario) || !troca.expiraEm().isAfter(Instant.now())
                || !usuario.isTrocaSenhaObrigatoria() || !usuario.getSenha().equals(troca.credencial())
                || (usuario.getSenhaTemporariaExpiraEm() != null
                    && !usuario.getSenhaTemporariaExpiraEm().isAfter(Instant.now())))
            throw invalido("A solicitação expirou ou foi substituída. Volte ao login.");
        salvarSenha(usuario, dados.newPassword(), dados.confirmPassword());
    }

    @Transactional
    public void alterar(Long id, TrocaSenhaRequest dados) {
        Usuario usuario = bloquear(id);
        if (!ativo(usuario) || usuario.isTrocaSenhaObrigatoria()
                || !encoder.matches(dados.currentPassword(), usuario.getSenha()))
            throw invalido("A senha atual está incorreta ou a conta exige redefinição.");
        salvarSenha(usuario, dados.newPassword(), dados.confirmPassword());
    }

    private void salvarSenha(Usuario usuario, String nova, String confirmacao) {
        PoliticaSenha.validar(nova);
        if (!nova.equals(confirmacao)) throw invalido("As senhas não conferem.");
        if (encoder.matches(nova, usuario.getSenha())) throw invalido("Escolha uma senha diferente da atual.");
        usuario.setSenha(encoder.encode(nova));
        usuario.setTrocaSenhaObrigatoria(false);
        usuario.setSenhaTemporariaExpiraEm(null);
        usuarios.saveAndFlush(usuario);
        revogar(usuario.getId());
    }

    private Usuario bloquear(Long id) {
        Usuario usuario = usuarios.bloquearPorId(id).orElseThrow(this::credenciaisInvalidas);
        entityManager.refresh(usuario, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return usuario;
    }
    private boolean ativo(Usuario usuario) { return usuario.isAtivo() && usuario.getPessoa().isAtivo(); }
    private String token() { return UUID.randomUUID().toString() + UUID.randomUUID(); }
    private void limparExpiradas() {
        sessoes.values().removeIf(s -> !s.expiraEm().isAfter(Instant.now()));
        trocas.values().removeIf(s -> !s.expiraEm().isAfter(Instant.now()));
    }
    private void revogar(Long id) {
        sessoes.values().removeIf(s -> s.usuarioId().equals(id));
        trocas.values().removeIf(s -> s.usuarioId().equals(id));
    }
    public void logout(String token) { sessoes.remove(token); }
    private RegraNegocioException invalido(String mensagem) {
        return new RegraNegocioException(HttpStatus.BAD_REQUEST, mensagem);
    }
    private RegraNegocioException credenciaisInvalidas() {
        return new RegraNegocioException(HttpStatus.UNAUTHORIZED, "Usuário ou senha inválidos, ou senha temporária expirada.");
    }
}
