package br.gov.sp.fatec.itu.estacionatec_api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-revisao;DB_CLOSE_DELAY=-1",
        "estacionatec.ocr.enabled=false"
})
class AuthRevisaoTests {
    @LocalServerPort int port;
    @Autowired JsonMapper mapper;
    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> enviar(String method, String path, String token, Object dados) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path))
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.method(method, dados == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(dados))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String login(String email, String senha) throws Exception {
        var resposta = enviar("POST", "/auth/login", null, Map.of("email", email, "password", senha));
        assertThat(resposta.statusCode()).isEqualTo(200);
        return mapper.readTree(resposta.body()).path("token").asText();
    }

    @Test
    void endpointLoginLimitaFalhasSemAlterarRespostaDeCredenciaisInvalidas() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(enviar("POST", "/auth/login", null,
                    Map.of("email", "inexistente@example.test", "password", "errada")).statusCode()).isEqualTo(401);
        }
        var bloqueado = enviar("POST", "/auth/login", null,
                Map.of("email", "INEXISTENTE@example.test", "password", "errada"));
        assertThat(bloqueado.statusCode()).isEqualTo(429);
        assertThat(mapper.readTree(bloqueado.body()).path("message").asText()).contains("um minuto");
        login("joao@edu.br", "EstacionaTec@123");
    }

    @Test
    void somenteAdministradorPodeTrocarSenhaESessoesExistentesSaoPreservadas() throws Exception {
        String admin = login("joao@edu.br", "EstacionaTec@123");
        var criacao = enviar("POST", "/usuarios", admin, Map.of("name", "Teste senha", "username", "senha.teste",
                "email", "senha@example.test", "role", "Porteiro", "status", "Ativo", "password", "SenhaOriginal123!"));
        assertThat(criacao.statusCode()).isEqualTo(201);
        long id = mapper.readTree(criacao.body()).path("id").asLong();
        String porteiro = login("senha@example.test", "SenhaOriginal123!");
        var alteracao = Map.of("name", "Teste senha", "username", "senha.teste", "email", "senha@example.test",
                "role", "Porteiro", "status", "Ativo", "password", "SenhaAlterada456!");
        assertThat(enviar("PUT", "/usuarios/" + id, null, alteracao).statusCode()).isEqualTo(401);
        assertThat(enviar("PUT", "/usuarios/" + id, porteiro, alteracao).statusCode()).isEqualTo(403);
        assertThat(enviar("PUT", "/usuarios/" + id, admin, alteracao).statusCode()).isEqualTo(200);
        assertThat(enviar("POST", "/auth/login", null,
                Map.of("email", "senha@example.test", "password", "SenhaOriginal123!")).statusCode()).isEqualTo(401);
        login("senha@example.test", "SenhaAlterada456!");
        // A01 não foi autorizado: esta revisão não altera a validade de tokens já emitidos.
        assertThat(enviar("GET", "/veiculos", porteiro, null).statusCode()).isEqualTo(200);
    }

    @Test
    void recuperacaoPublicaNaoTemMaisEndpoint() throws Exception {
        String admin = login("joao@edu.br", "EstacionaTec@123");
        assertThat(enviar("POST", "/auth/recuperar-senha", admin, Map.of("email", "joao@edu.br"))
                .statusCode()).isEqualTo(404);
    }
}
