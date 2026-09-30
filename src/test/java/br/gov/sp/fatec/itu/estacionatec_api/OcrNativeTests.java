package br.gov.sp.fatec.itu.estacionatec_api;

import br.gov.sp.fatec.itu.estacionatec_api.integration.OcrClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledOnOs(OS.WINDOWS)
class OcrNativeTests {
    @Test
    void executaMotorRealEmImagemDePlacaSintetica() throws Exception {
        var image = new BufferedImage(720, 180, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 720, 180);
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("Arial", Font.BOLD, 88));
        graphics.drawString("ABC1234", 110, 115);
        graphics.dispose();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        var result = new OcrClient("", 0, 0, 1, 1).reconhecer(bytes.toByteArray());
        assertThat(result).anyMatch(r -> r.plate().equals("ABC1234") && r.confidence() >= 70);
    }
}
