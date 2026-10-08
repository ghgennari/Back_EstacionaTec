package br.gov.sp.fatec.itu.estacionatec_api;

import br.gov.sp.fatec.itu.estacionatec_api.integration.OcrClient;

/** Executado tambem durante o build, no JRE e com o usuario da imagem de producao. */
public final class OcrRuntimeCheck {
    private OcrRuntimeCheck() { }

    public static void main(String[] args) throws Exception {
        String dataPath = System.getenv().getOrDefault("OCR_DATA_PATH", "");
        try (var image = OcrRuntimeCheck.class.getResourceAsStream("/ocr/placa-ABC1234.png")) {
            if (image == null) throw new IllegalStateException("Imagem de teste do OCR ausente.");
            var readings = new OcrClient(dataPath, 0, 0, 1, 1).reconhecer(image.readAllBytes());
            if (readings.stream().noneMatch(r -> r.plate().equals("ABC1234") && r.confidence() >= 70)) {
                throw new IllegalStateException("OCR nao reconheceu a placa de teste: " + readings);
            }
        }
        System.out.println("OCR Java validado: ABC1234, confianca >= 70.");
    }
}
