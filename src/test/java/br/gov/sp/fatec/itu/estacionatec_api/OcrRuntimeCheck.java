package br.gov.sp.fatec.itu.estacionatec_api;

import br.gov.sp.fatec.itu.estacionatec_api.integration.OcrClient;

/** Executado tambem durante o build, no JRE e com o usuario da imagem de producao. */
public final class OcrRuntimeCheck {
    private OcrRuntimeCheck() { }

    public static void main(String[] args) throws Exception {
        String dataPath = System.getenv().getOrDefault("OCR_DATA_PATH", "");
        // Permite repetir o teste com uma captura real, sem cadastrar entrada nem abrir cancela.
        if (args.length == 2) {
            var readings = new OcrClient(dataPath, 0, 0, 1, 1)
                    .reconhecer(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[0])));
            if (readings.stream().noneMatch(r -> r.plate().equals(args[1]) && r.confidence() >= 70)) {
                throw new IllegalStateException("Placa esperada nao reconhecida: " + readings);
            }
            System.out.println("Captura fornecida validada: " + readings);
            return;
        }
        try (var image = OcrRuntimeCheck.class.getResourceAsStream("/ocr/placa-ABC1234.png")) {
            if (image == null) throw new IllegalStateException("Imagem de teste do OCR ausente.");
            byte[] original = image.readAllBytes();
            var readings = new OcrClient(dataPath, 0, 0, 1, 1).reconhecer(original);
            if (readings.stream().noneMatch(r -> r.plate().equals("ABC1234") && r.confidence() >= 70)) {
                throw new IllegalStateException("OCR nao reconheceu a placa de teste: " + readings);
            }
            var placa = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(original));
            var cena = new java.awt.image.BufferedImage(1920, 1080, java.awt.image.BufferedImage.TYPE_INT_RGB);
            var g = cena.createGraphics();
            try {
                g.setColor(new java.awt.Color(100, 110, 120));
                g.fillRect(0, 0, 1920, 1080);
                g.setColor(java.awt.Color.DARK_GRAY);
                g.fillRect(450, 250, 1020, 550);
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(480, 280, 960, 490);
                int altura = placa.getHeight() * 700 / placa.getWidth();
                g.drawImage(placa, 610, 460 - altura / 2, 700, altura, null);
            } finally { g.dispose(); }
            var jpeg = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(cena, "jpeg", jpeg);
            long inicio = System.nanoTime();
            var cenaLida = new OcrClient(dataPath, 0, 0, 1, 1).reconhecer(jpeg.toByteArray());
            if (cenaLida.stream().noneMatch(r -> r.plate().equals("ABC1234") && r.confidence() >= 70)) {
                throw new IllegalStateException("OCR falhou na cena sintetica JPEG: " + cenaLida);
            }
            System.out.println("Cena sintetica 1920x1080 validada em "
                    + (System.nanoTime() - inicio) / 1_000_000 + " ms: " + cenaLida);
        }
        try (var mercosul = OcrRuntimeCheck.class.getResourceAsStream("/ocr/placa-ABC1D23.png")) {
            if (mercosul == null) throw new IllegalStateException("Fixture Mercosul ausente.");
            var readings = new OcrClient(dataPath, 0, 0, 1, 1).reconhecer(mercosul.readAllBytes());
            if (readings.stream().noneMatch(r -> r.plate().equals("ABC1D23") && r.confidence() >= 70)) {
                throw new IllegalStateException("OCR nao reconheceu Mercosul: " + readings);
            }
            System.out.println("Placa Mercosul sintetica validada: " + readings);
        }
        System.out.println("OCR Java validado: ABC1234, confianca >= 70.");
    }
}
