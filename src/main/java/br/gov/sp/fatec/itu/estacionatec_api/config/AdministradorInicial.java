package br.gov.sp.fatec.itu.estacionatec_api.config;

import br.gov.sp.fatec.itu.estacionatec_api.entities.*;
import br.gov.sp.fatec.itu.estacionatec_api.repositories.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Component
@Profile("prod")
public class AdministradorInicial implements CommandLineRunner {
    private final UsuarioRepository usuarios;
    private final PessoaRepository pessoas;
    private final PerfilRepository perfis;
    private final PasswordEncoder encoder;
    private final String nome;
    private final String username;
    private final String email;
    private final String senha;

    public AdministradorInicial(UsuarioRepository usuarios, PessoaRepository pessoas,
            PerfilRepository perfis, PasswordEncoder encoder,
            @Value("${estacionatec.admin.name:Administrador}") String nome,
            @Value("${estacionatec.admin.username:admin}") String username,
            @Value("${estacionatec.admin.email:}") String email,
            @Value("${estacionatec.admin.password:}") String senha) {
        this.usuarios = usuarios;
        this.pessoas = pessoas;
        this.perfis = perfis;
        this.encoder = encoder;
        this.nome = nome.trim();
        this.username = username.trim().toLowerCase(Locale.ROOT);
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.senha = senha;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (usuarios.count() > 0) return;
        if (nome.isBlank() || nome.length() > 150 || username.isBlank() || username.length() > 50
                || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length() > 150
                || senha.length() < 12 || senha.getBytes(StandardCharsets.UTF_8).length > 72
                || senha.equals("EstacionaTec@123")) {
            throw new IllegalStateException("Configure ADMIN_NAME, ADMIN_USERNAME, ADMIN_EMAIL e uma ADMIN_PASSWORD própria de 12 caracteres ou mais (até 72 bytes) para a primeira inicialização.");
        }
        for (String tipo : List.of("Administrador", "Porteiro", "Usuário")) {
            if (perfis.findByNome(tipo).isEmpty()) {
                Perfil perfil = new Perfil();
                perfil.setNome(tipo);
                perfis.save(perfil);
            }
        }
        Pessoa pessoa = new Pessoa();
        pessoa.setNome(nome);
        pessoa.setEmail(email);
        pessoa.setCategoria("Funcionário");
        Usuario usuario = new Usuario();
        usuario.setPessoa(pessoas.save(pessoa));
        usuario.setUsername(username);
        usuario.setSenha(encoder.encode(senha));
        usuario.setPerfil(perfis.findByNome("Administrador").orElseThrow());
        usuarios.save(usuario);
    }
}
