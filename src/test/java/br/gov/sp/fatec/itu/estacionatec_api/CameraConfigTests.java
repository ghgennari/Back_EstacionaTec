package br.gov.sp.fatec.itu.estacionatec_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.assertThat;

class CameraConfigTests {
    @Configuration(proxyBeanMethods = false)
    static class ConfiguracaoTeste {
    }

    @Test
    void carregaCameraCompartilhadaComSenhaPrivada() {
        try (var context = application().run("--estacionatec.camera.password=senha-teste")) {
            var properties = context.getEnvironment();
            assertThat(properties.getProperty("estacionatec.camera.source")).isEqualTo("IP");
            assertThat(properties.getProperty("estacionatec.camera.rtsp-url"))
                    .isEqualTo("rtsp://admin:senha-teste@192.168.1.188:554/onvif1");
            assertThat(properties.getProperty("estacionatec.camera.rtsp-transport")).isEqualTo("udp");
            assertThat(properties.getProperty("estacionatec.camera.ffmpeg")).isEqualTo("ffmpeg");
        }
    }

    @Test
    void permiteSubstituirEnderecoEExecutavelNaImplantacao() {
        try (var context = application().run("--CAMERA_RTSP_URL=rtsp://127.0.0.1:554/teste",
                "--FFMPEG_PATH=/usr/bin/ffmpeg", "--CAMERA_RTSP_TRANSPORT=tcp")) {
            var properties = context.getEnvironment();
            assertThat(properties.getProperty("estacionatec.camera.rtsp-url"))
                    .isEqualTo("rtsp://127.0.0.1:554/teste");
            assertThat(properties.getProperty("estacionatec.camera.ffmpeg")).isEqualTo("/usr/bin/ffmpeg");
            assertThat(properties.getProperty("estacionatec.camera.rtsp-transport")).isEqualTo("tcp");
        }
    }

    private SpringApplicationBuilder application() {
        return new SpringApplicationBuilder(ConfiguracaoTeste.class).web(WebApplicationType.NONE)
                .properties("spring.config.location=file:./storage/camera.properties",
                        "spring.main.banner-mode=off", "logging.level.root=WARN");
    }
}
