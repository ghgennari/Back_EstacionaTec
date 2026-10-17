package br.gov.sp.fatec.itu.estacionatec_api.integration;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Random;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CameraPreviewTests {
    @Test
    void reduzTrafegoCompartilhaPreviaEPreservaQuadroDoOcr() throws Exception {
        var frame = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
        var random = new Random(42);
        for (int y = 0; y < frame.getHeight(); y++)
            for (int x = 0; x < frame.getWidth(); x++) frame.setRGB(x, y, random.nextInt(0x1000000));
        var out = new ByteArrayOutputStream();
        ImageIO.write(frame, "jpeg", out);
        byte[] original = out.toByteArray();
        byte[] copia = original.clone();
        var camera = mock(CameraClient.class);
        when(camera.capturar()).thenReturn(original);
        var preview = new CameraPreview(camera);
        byte[] reduzida = preview.capturar();
        assertThat(preview.capturar()).isEqualTo(reduzida);
        verify(camera).capturar();
        assertThat(original).isEqualTo(copia);
        assertThat(ImageIO.read(new ByteArrayInputStream(reduzida)).getWidth()).isEqualTo(800);
        assertThat(reduzida.length).isLessThan(original.length / 2);
        System.out.printf("Previa sintetica: original=%d bytes, previa=%d bytes%n", original.length, reduzida.length);
    }
}
