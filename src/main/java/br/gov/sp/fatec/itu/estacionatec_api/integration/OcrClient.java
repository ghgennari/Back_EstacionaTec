package br.gov.sp.fatec.itu.estacionatec_api.integration;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.util.LoadLibs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class OcrClient {
    public record Leitura(String plate, float confidence) { }

    private static final Pattern PLACA = Pattern.compile("(?<![A-Z0-9])[A-Z]{3}[-\\s]*[0-9][A-Z0-9][0-9]{2}(?![A-Z0-9])");
    private final String dataPath;
    private final double x;
    private final double y;
    private final double width;
    private final double height;

    public OcrClient(@Value("${estacionatec.ocr.data-path:}") String dataPath,
            @Value("${estacionatec.ocr.region.x:0}") double x,
            @Value("${estacionatec.ocr.region.y:0}") double y,
            @Value("${estacionatec.ocr.region.width:1}") double width,
            @Value("${estacionatec.ocr.region.height:1}") double height) {
        this.dataPath = dataPath;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public synchronized List<Leitura> reconhecer(byte[] imagem) throws Exception {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(imagem));
        if (original == null || (long) original.getWidth() * original.getHeight() > 16_000_000) {
            throw new IllegalArgumentException("Imagem inválida para OCR.");
        }
        if (!(x >= 0 && y >= 0 && width > 0 && height > 0 && x + width <= 1 && y + height <= 1)) {
            throw new IllegalArgumentException("Região de leitura inválida.");
        }
        int left = (int) (x * original.getWidth());
        int top = (int) (y * original.getHeight());
        BufferedImage region = original.getSubimage(left, top,
                Math.max(1, (int) (width * original.getWidth())),
                Math.max(1, (int) (height * original.getHeight())));
        double scale = Math.min(2, Math.min(1600.0 / region.getWidth(), 1200.0 / region.getHeight()));
        BufferedImage prepared = new BufferedImage(Math.max(1, (int) (region.getWidth() * scale)),
                Math.max(1, (int) (region.getHeight() * scale)), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = prepared.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(region, 0, 0, prepared.getWidth(), prepared.getHeight(), null);
        } finally {
            graphics.dispose();
        }
        Tesseract engine = new Tesseract();
        engine.setDatapath(dataPath.isBlank() ? LoadLibs.extractTessResources("tessdata").getAbsolutePath() : dataPath);
        engine.setLanguage("eng");
        engine.setOcrEngineMode(1);
        engine.setPageSegMode(11);
        List<Leitura> result = new ArrayList<>();
        for (var line : engine.getWords(prepared, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE)) {
            for (String plate : extrairPlacas(line.getText())) {
                result.add(new Leitura(plate, line.getConfidence()));
            }
        }
        return result;
    }

    public static List<String> extrairPlacas(String text) {
        if (text == null) return List.of();
        return PLACA.matcher(text.toUpperCase(Locale.ROOT)).results()
                .map(match -> match.group().replaceAll("[-\\s]", "")).distinct().toList();
    }
}
