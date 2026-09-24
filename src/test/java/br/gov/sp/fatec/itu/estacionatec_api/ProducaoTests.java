package br.gov.sp.fatec.itu.estacionatec_api;

import br.gov.sp.fatec.itu.estacionatec_api.config.AdministradorInicial;
import br.gov.sp.fatec.itu.estacionatec_api.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.*;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("prod")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:production-profile-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.password=senha-apenas-deste-teste",
        "estacionatec.admin.email=admin@example.test",
        "estacionatec.admin.password=SenhaApenasParaTeste123!",
        "estacionatec.storage=target/production-test-images"
})
class ProducaoTests {
    @Autowired private UsuarioRepository usuarios;
    @Autowired private VeiculoRepository veiculos;
    @Autowired private EventoAcessoRepository eventos;
    @Autowired private PerfilRepository perfis;
    @Autowired private AdministradorInicial inicial;
    @Autowired private PasswordEncoder encoder;
    @LocalServerPort private int port;

    @Test
    void iniciaSemDadosDeExemploECriaAdministradorUmaUnicaVez() throws Exception {
        assertThat(usuarios.count()).isEqualTo(1);
        assertThat(veiculos.count()).isZero();
        assertThat(eventos.count()).isZero();
        assertThat(perfis.count()).isEqualTo(3);
        var admin = usuarios.findByUsernameIgnoreCase("admin").orElseThrow();
        assertThat(encoder.matches("SenhaApenasParaTeste123!", admin.getSenha())).isTrue();
        String senha = admin.getSenha();
        inicial.run();
        assertThat(usuarios.count()).isEqualTo(1);
        assertThat(usuarios.findById(admin.getId()).orElseThrow().getSenha()).isEqualTo(senha);
        var http = HttpClient.newHttpClient();
        var health = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("UP");
        var console = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/h2-console/"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(console.statusCode()).isEqualTo(404);
    }
}
