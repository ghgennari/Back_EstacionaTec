package br.gov.sp.fatec.itu.estacionatec_api;

import br.gov.sp.fatec.itu.estacionatec_api.integration.*;
import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class HardwareClientTests {
    @Test
    void falhaRtspNaoExpoeCredenciaisEFechamentoImpedeReabertura() {
        var rtsp = new RtspCameraClient("rtsp://admin:senha-teste@127.0.0.1:554/onvif1",
                "executavel-ffmpeg-inexistente", "udp");
        assertThat(rtsp.configurada()).isTrue();
        assertThatThrownBy(rtsp::capturar).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("FFmpeg não encontrado").hasMessageNotContaining("senha-teste");
        rtsp.fechar();
        assertThatThrownBy(rtsp::capturar).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("encerrada");
    }

    @Test
    void enviaComandoIdentificadoEExigeConfirmacaoCorreta() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/abrir", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = "{\"commandId\":\"evento-42\",\"opened\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var client = new Esp32Client("http://127.0.0.1:" + server.getAddress().getPort() + "/abrir", JsonMapper.builder().build());
            client.abrir(42L);
            assertThat(body.get()).contains("evento-42", "OPEN");
            assertThatThrownBy(() -> client.abrir(43L)).isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("não confirmou");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cameraConsultaEndpointConfiguradoESinalizaFalhaHttp() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] expected = {1, 2, 3};
        server.createContext("/snapshot", exchange -> {
            exchange.sendResponseHeaders(200, expected.length);
            exchange.getResponseBody().write(expected);
            exchange.close();
        });
        server.createContext("/falha", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThat(new CameraClient(base + "/snapshot", FonteCamera.IP, new RtspCameraClient("", "ffmpeg", "tcp")).capturar()).isEqualTo(expected);
            assertThatThrownBy(() -> new CameraClient(base + "/falha", FonteCamera.IP, new RtspCameraClient("", "ffmpeg", "tcp")).capturar())
                    .isInstanceOf(RegraNegocioException.class).hasMessageContaining("Falha ao capturar");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void diferenciaWebcamDaCapturaIpSemConectarEquipamentos() {
        var webcam = new CameraClient("http://127.0.0.1:1/nao-usar", FonteCamera.WEBCAM, new RtspCameraClient("", "ffmpeg", "tcp"));
        assertThat(webcam.configurada()).isTrue();
        assertThatThrownBy(webcam::capturar).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("navegador");
        var ip = new CameraClient("", FonteCamera.IP, new RtspCameraClient("", "ffmpeg", "tcp"));
        assertThat(ip.configurada()).isFalse();
        assertThatThrownBy(ip::capturar).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Câmera não conectada");
    }
}
