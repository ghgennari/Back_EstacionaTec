package br.gov.sp.fatec.itu.estacionatec_api.integration;

import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.HttpURLConnection;

@Component
public class CameraClient {
    private final String snapshotUrl;
    private final FonteCamera fonte;
    private final RtspCameraClient rtsp;

    public CameraClient(@Value("${estacionatec.camera.snapshot-url:}") String snapshotUrl,
            @Value("${estacionatec.camera.source:IP}") FonteCamera fonte, RtspCameraClient rtsp) {
        this.snapshotUrl = snapshotUrl;
        this.fonte = fonte;
        this.rtsp = rtsp;
    }

    public boolean configurada() {
        return usaWebcam() || rtsp.configurada() || !snapshotUrl.isBlank();
    }

    public boolean usaWebcam() {
        return fonte == FonteCamera.WEBCAM;
    }

    public FonteCamera fonte() {
        return fonte;
    }

    public byte[] capturar() {
        if (usaWebcam()) {
            throw RegraNegocioException.conflito(
                    "Ative a webcam no navegador e envie a foto pela captura de webcam.");
        }
        if (rtsp.configurada()) return rtsp.capturar();
        if (!configurada()) {
            throw new RegraNegocioException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Câmera não conectada. Configure uma fonte de imagem para visualizar ou capturar.");
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(snapshotUrl).toURL().openConnection();
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(2000);
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != 200) {
                throw new java.io.IOException("A câmera não retornou uma imagem.");
            }
            try (var input = connection.getInputStream()) {
                return input.readNBytes(5 * 1024 * 1024 + 1);
            }
        } catch (Exception exception) {
            throw new RegraNegocioException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Falha ao capturar imagem da câmera. Verifique endereço e conexão.");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
