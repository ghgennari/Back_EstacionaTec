package br.gov.sp.fatec.itu.estacionatec_api;

import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.*;
import br.gov.sp.fatec.itu.estacionatec_api.entities.Perfil;
import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import br.gov.sp.fatec.itu.estacionatec_api.repositories.*;
import br.gov.sp.fatec.itu.estacionatec_api.services.CadastroService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cadastro-revisao;DB_CLOSE_DELAY=-1",
        "estacionatec.seed.enabled=false", "estacionatec.ocr.enabled=false",
        "logging.level.root=WARN"
})
class CadastroRevisaoTests {
    @Autowired CadastroService cadastro;
    @Autowired UsuarioRepository usuarios;
    @Autowired PessoaRepository pessoas;
    @Autowired PerfilRepository perfis;

    @BeforeEach
    void preparar() {
        usuarios.deleteAll();
        pessoas.deleteAll();
        for (String nome : List.of("Administrador", "Porteiro", "Usuário")) {
            if (perfis.findByNome(nome).isEmpty()) {
                Perfil perfil = new Perfil();
                perfil.setNome(nome);
                perfis.saveAndFlush(perfil);
            }
        }
    }

    private UsuarioRequest dados(String username, String email, String role, String status, String senha) {
        return new UsuarioRequest("Operador", username, email, role, status, senha);
    }

    private UsuarioResponse criar(String nome, String email, String role) {
        return cadastro.salvarUsuario(null, dados(nome, email, role, "Ativo", "SenhaTeste123!"));
    }

    @Test
    void preservaSenhaNaEdicaoEPermiteAdministrarComOutraContaAtiva() {
        var admin = criar("admin", "admin@test.com", "Administrador");
        criar("outro", "outro@test.com", "Administrador");
        String hash = usuarios.findById(admin.id()).orElseThrow().getSenha();
        cadastro.salvarUsuario(admin.id(), dados("admin", "admin@test.com", "Administrador", "Ativo", null));
        assertThat(usuarios.findById(admin.id()).orElseThrow().getSenha()).isEqualTo(hash);
        cadastro.salvarUsuario(admin.id(), dados("admin", "admin@test.com", "Porteiro", "Inativo", ""));
        assertThat(usuarios.findById(admin.id()).orElseThrow().isAtivo()).isFalse();
    }

    @Test
    void rejeitaEmailDuplicadoNoCadastroDeUsuario() {
        criar("um", "um@test.com", "Porteiro");
        assertThatThrownBy(() -> criar("dois", "UM@test.com", "Porteiro"))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("E-mail");
    }

    @Test
    void rejeitaSenhaEmBrancoAntesDePersistir() {
        assertThatThrownBy(() -> cadastro.salvarUsuario(null,
                dados("vazio", "vazio@test.com", "Porteiro", "Ativo", "      ")))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("senha");
        assertThat(usuarios.count()).isZero();
        assertThat(pessoas.count()).isZero();
    }

    @Test
    void edicaoDePessoaNaoDuplicaEmailDeLoginMasPermiteContatoCompartilhadoSemConta() {
        criar("um", "um@test.com", "Porteiro");
        var segundo = criar("dois", "dois@test.com", "Porteiro");
        Long pessoaId = usuarios.findById(segundo.id()).orElseThrow().getPessoa().getId();
        assertThatThrownBy(() -> cadastro.salvarPessoa(pessoaId,
                new PessoaRequest("Dois", "DOC2", "UM@test.com", null, "Funcionário", true)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("E-mail");
        assertThat(pessoas.findById(pessoaId).orElseThrow().getEmail()).isEqualTo("dois@test.com");
        cadastro.salvarPessoa(null, new PessoaRequest("Contato", "DOC3", "um@test.com", null, "Aluno", true));
    }

    @Test
    void preservaUltimoAdministradorEmTodosOsCaminhos() {
        var admin = criar("admin", "admin@test.com", "Administrador");
        var operador = criar("operador", "operador@test.com", "Porteiro");
        assertThatThrownBy(() -> cadastro.salvarUsuario(admin.id(),
                dados("admin", "admin@test.com", "Administrador", "Inativo", null)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("administrador");
        assertThatThrownBy(() -> cadastro.salvarUsuario(admin.id(),
                dados("admin", "admin@test.com", "Porteiro", "Ativo", null)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("administrador");
        assertThatThrownBy(() -> cadastro.excluirUsuario(admin.id(), operador.id()))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("administrador");
        Long pessoaId = usuarios.findById(admin.id()).orElseThrow().getPessoa().getId();
        assertThatThrownBy(() -> cadastro.salvarPessoa(pessoaId,
                new PessoaRequest("Admin", "ADM1", "admin@test.com", null, "Funcionário", false)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("administrador");
        assertThat(usuarios.findById(admin.id()).orElseThrow().isAtivo()).isTrue();
        assertThat(pessoas.findById(pessoaId).orElseThrow().isAtivo()).isTrue();
    }

    @Test
    void serializaEmailsConcorrentesSemAlterarSchema() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            Callable<Boolean> um = () -> tentarCriar(inicio, "um");
            Callable<Boolean> dois = () -> tentarCriar(inicio, "dois");
            var a = executor.submit(um);
            var b = executor.submit(dois);
            inicio.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(usuarios.count()).isEqualTo(1);
        }
    }

    private boolean tentarCriar(CountDownLatch inicio, String nome) throws Exception {
        inicio.await();
        try {
            criar(nome, "concorrente@test.com", "Porteiro");
            return true;
        } catch (RegraNegocioException exception) {
            assertThat(exception.getMessage()).contains("E-mail");
            return false;
        }
    }

    @Test
    void administradoresNaoConseguemSeInativarSimultaneamente() throws Exception {
        var um = criar("um", "um@test.com", "Administrador");
        var dois = criar("dois", "dois@test.com", "Administrador");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            var a = executor.submit(() -> inativar(inicio, um));
            var b = executor.submit(() -> inativar(inicio, dois));
            inicio.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }

    private boolean inativar(CountDownLatch inicio, UsuarioResponse usuario) throws Exception {
        inicio.await();
        try {
            cadastro.salvarUsuario(usuario.id(), dados(usuario.username(), usuario.email(),
                    "Administrador", "Inativo", null));
            return true;
        } catch (RegraNegocioException exception) {
            assertThat(exception.getMessage()).contains("administrador");
            return false;
        }
    }
}
