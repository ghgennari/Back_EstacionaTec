package br.gov.sp.fatec.itu.estacionatec_api.integration;

import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.IIOImage;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/** Previa pequena, compartilhada entre clientes; OCR e fotos usam o quadro completo. */
@Component
public class CameraPreview {
    private final CameraClient camera;
    private byte[] cache;
    private long atualizado;

    public CameraPreview(CameraClient camera) { this.camera = camera; }

    public synchronized byte[] capturar() {
        long now = System.nanoTime();
        if (cache != null && now - atualizado < 900_000_000L) return cache.clone();
        try {
            cache = reduzir(camera.capturar());
            atualizado = System.nanoTime();
            return cache.clone();
        } catch (java.io.IOException exception) {
            throw new RegraNegocioException(HttpStatus.SERVICE_UNAVAILABLE, "Falha ao preparar a previa da camera.");
        }
    }

    static byte[] reduzir(byte[] bytes) throws java.io.IOException {
        BufferedImage original = OcrClient.lerImagem(bytes);
        double scale = Math.min(1, 800.0 / original.getWidth());
        BufferedImage pequena = new BufferedImage(Math.max(1, (int) (original.getWidth() * scale)),
                Math.max(1, (int) (original.getHeight() * scale)), BufferedImage.TYPE_INT_RGB);
        var g = pequena.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(original, 0, 0, pequena.getWidth(), pequena.getHeight(), null);
        } finally { g.dispose(); }
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (var bytesOut = new ByteArrayOutputStream(); var output = ImageIO.createImageOutputStream(bytesOut)) {
            writer.setOutput(output);
            var options = writer.getDefaultWriteParam();
            options.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            options.setCompressionQuality(0.65f);
            writer.write(null, new IIOImage(pequena, null, null), options);
            output.flush();
            return bytesOut.toByteArray();
        } finally { writer.dispose(); }
    }
}
