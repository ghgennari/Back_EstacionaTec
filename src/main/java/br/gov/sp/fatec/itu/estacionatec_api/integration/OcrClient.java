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
        BufferedImage original = lerImagem(imagem);
        if (!(x >= 0 && y >= 0 && width > 0 && height > 0 && x + width <= 1 && y + height <= 1)) {
            throw new IllegalArgumentException("Região de leitura inválida.");
        }
        int left = (int) (x * original.getWidth());
        int top = (int) (y * original.getHeight());
        BufferedImage region = original.getSubimage(left, top,
                Math.max(1, (int) (width * original.getWidth())),
                Math.max(1, (int) (height * original.getHeight())));
        // Preserva mais detalhes do quadro; a previa do navegador tem resolucao independente.
        double scale = Math.min(2, Math.min(2560.0 / region.getWidth(), 1440.0 / region.getHeight()));
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
        engine.setVariable("load_system_dawg", "0");
        engine.setVariable("load_freq_dawg", "0");
        engine.setPageSegMode(11);
        List<Leitura> result = new ArrayList<>();
        var lines = engine.getWords(prepared, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE);
        for (var line : lines) {
            for (String plate : extrairPlacas(line.getText())) {
                result.add(new Leitura(plate, line.getConfidence()));
            }
        }
        // Rele as linhas candidatas ampliadas como uma linha unica. Limite de trabalho
        // evita executar dezenas de OCRs em uma cena cheia de texto.
        int tentativas = 0;
        for (var line : lines) {
            if (tentativas >= 4) break;
            if (line.getConfidence() >= 70 && !extrairPlacas(line.getText()).isEmpty()) continue;
            String text = line.getText().replaceAll("[^A-Za-z0-9]", "");
            var box = line.getBoundingBox();
            if (text.length() < 6 || text.length() > 12 || box.width < box.height * 2
                    || box.width > box.height * 12) continue;
            tentativas++;
            var bounds = new java.awt.Rectangle(Math.max(0, box.x - 10), Math.max(0, box.y - 10),
                    box.width + 20, box.height + 20).intersection(
                            new java.awt.Rectangle(0, 0, prepared.getWidth(), prepared.getHeight()));
            double factor = Math.min(3, 1200.0 / bounds.width);
            BufferedImage linha = new BufferedImage((int) (bounds.width * factor),
                    Math.max(1, (int) (bounds.height * factor)), BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = linha.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(prepared.getSubimage(bounds.x, bounds.y, bounds.width, bounds.height),
                        0, 0, linha.getWidth(), linha.getHeight(), null);
            } finally { g.dispose(); }
            engine.setPageSegMode(7);
            for (var word : engine.getWords(linha, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE)) {
                for (String plate : extrairPlacas(word.getText())) {
                    result.add(new Leitura(plate, word.getConfidence()));
                }
            }
        }
        return result;
    }

    static BufferedImage lerImagem(byte[] bytes) throws java.io.IOException {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Imagem inválida para OCR.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                int largura = reader.getWidth(0);
                int altura = reader.getHeight(0);
                if (largura <= 0 || altura <= 0 || (long) largura * altura > 16_000_000) {
                    throw new IllegalArgumentException("Imagem inválida para OCR.");
                }
                BufferedImage imagem = reader.read(0);
                if (imagem == null) throw new IllegalArgumentException("Imagem inválida para OCR.");
                return imagem;
            } finally {
                reader.dispose();
            }
        }
    }

    public static List<String> extrairPlacas(String text) {
        if (text == null) return List.of();
        return PLACA.matcher(text.toUpperCase(Locale.ROOT)).results()
                .map(match -> match.group().replaceAll("[-\\s]", "")).distinct().toList();
    }
}
