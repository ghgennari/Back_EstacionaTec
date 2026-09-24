package br.gov.sp.fatec.itu.estacionatec_api.integration;

import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

@Component
public class Esp32Client {
    private final String openUrl;
    private final JsonMapper mapper;

    public Esp32Client(@Value("${estacionatec.esp32.open-url:}") String openUrl, JsonMapper mapper) {
        this.openUrl = openUrl;
        this.mapper = mapper;
    }

    public boolean configurado() {
        return !openUrl.isBlank();
    }

    public void abrir(Long eventoId) {
        abrirComando("evento-" + eventoId);
    }

    public void abrirManualmente() {
        abrirComando("manual-" + java.util.UUID.randomUUID());
    }

    private void abrirComando(String commandId) {
        if (!configurado()) {
            throw new RegraNegocioException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O portão não abriu: ESP32 não conectado.");
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(openUrl).toURL().openConnection();
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(2000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            byte[] body = ("{\"commandId\":\"" + commandId + "\",\"action\":\"OPEN\"}")
                    .getBytes(StandardCharsets.UTF_8);
            try (var output = connection.getOutputStream()) {
                output.write(body);
            }
            if (connection.getResponseCode() != 200) {
                throw new java.io.IOException("ESP32 não confirmou o comando.");
            }
            try (var input = connection.getInputStream()) {
                var resposta = mapper.readTree(input.readNBytes(4096));
                if (!resposta.path("opened").asBoolean()
                        || !commandId.equals(resposta.path("commandId").asText())) {
                    throw new java.io.IOException("Confirmação inválida.");
                }
            }
        } catch (Exception exception) {
            throw new RegraNegocioException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O ESP32 não confirmou a abertura. Confira o portão antes de tentar novamente.");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
