package br.gov.sp.fatec.itu.estacionatec_api.services;

import br.gov.sp.fatec.itu.estacionatec_api.integration.CameraClient;
import br.gov.sp.fatec.itu.estacionatec_api.integration.OcrClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ReconhecimentoPlacaService {
    public record Estado(boolean enabled, String status, String message, String detectionId,
            String plate, Float confidence, Instant detectedAt) { }

    private final CameraClient camera;
    private final OcrClient ocr;
    private final boolean enabled;
    private final float minimumConfidence;
    private volatile long acompanharAte;
    private volatile Estado estado = new Estado(true, "AGUARDANDO", "Aguardando placa na câmera.", null, null, null, null);
    private String candidata;
    private int confirmacoes;
    private String presente;
    private Estado ultimaConfirmada;
    private long vistaEm;
    private long ultimoQuadro;

    public ReconhecimentoPlacaService(CameraClient camera, OcrClient ocr,
            @Value("${estacionatec.ocr.enabled:true}") boolean enabled,
            @Value("${estacionatec.ocr.minimum-confidence:70}") float minimumConfidence) {
        this.camera = camera;
        this.ocr = ocr;
        this.enabled = enabled;
        this.minimumConfidence = minimumConfidence;
    }

    public Estado acompanhar() {
        if (!enabled || camera.usaWebcam()) {
            return new Estado(false, "DESATIVADO", "OCR automático disponível para a câmera IP.", null, null, null, null);
        }
        acompanharAte = System.currentTimeMillis() + 12_000;
        Estado atual = estado;
        if (atual.detectedAt() != null && atual.detectedAt().isBefore(Instant.now().minusSeconds(20))) {
            return new Estado(true, "AGUARDANDO", "Aguardando uma nova placa.", null, null, null, null);
        }
        return atual;
    }

    // Uma leitura compartilhada por todos os operadores. Não grava entradas nem abre a cancela.
    @Scheduled(fixedDelayString = "${estacionatec.ocr.interval-ms:1500}")
    public void analisar() {
        if (!enabled || System.currentTimeMillis() > acompanharAte || camera.usaWebcam()) return;
        try {
            processar(ocr.reconhecer(camera.capturar()), System.currentTimeMillis());
        } catch (Exception | LinkageError exception) {
            candidata = null;
            confirmacoes = 0;
            presente = null;
            // Exceções da biblioteca/câmera podem incluir caminhos ou informações privadas.
            estado = new Estado(true, "INDISPONIVEL",
                    "Não foi possível ler a câmera. Verifique a imagem e a instalação do OCR.", null, null, null, null);
        }
    }

    synchronized void processar(List<OcrClient.Leitura> leituras, long agora) {
        if (agora - ultimoQuadro > 5_000) {
            candidata = null;
            confirmacoes = 0;
        }
        ultimoQuadro = agora;
        var placas = leituras.stream().filter(l -> Float.isFinite(l.confidence()) && l.confidence() >= minimumConfidence)
                .filter(l -> OcrClient.extrairPlacas(l.plate()).contains(l.plate()))
                .map(OcrClient.Leitura::plate).distinct().toList();
        if (presente != null && agora - vistaEm > 8_000) presente = null;
        if (placas.size() != 1) {
            candidata = null;
            confirmacoes = 0;
            if (placas.isEmpty() && estado.detectedAt() != null
                    && agora - estado.detectedAt().toEpochMilli() <= 20_000) return;
            estado = new Estado(true, "AGUARDANDO", placas.size() > 1
                    ? "Mais de uma placa visível. Posicione um veículo por vez na área de leitura."
                    : "Aguardando uma placa nítida na câmera.", null, null, null, null);
            return;
        }
        String placa = placas.getFirst();
        if (placa.equals(presente)) {
            vistaEm = agora;
            estado = new Estado(true, "DETECTADA", "Placa identificada. Confira antes de registrar a entrada.",
                    ultimaConfirmada.detectionId(), placa, ultimaConfirmada.confidence(), Instant.ofEpochMilli(agora));
            return;
        }
        confirmacoes = placa.equals(candidata) ? confirmacoes + 1 : 1;
        candidata = placa;
        if (confirmacoes < 2) {
            estado = new Estado(true, "CONFIRMANDO", "Confirmando leitura da placa...", null, null, null, null);
            return;
        }
        float confidence = leituras.stream().filter(l -> placa.equals(l.plate()))
                .max(Comparator.comparing(OcrClient.Leitura::confidence)).orElseThrow().confidence();
        presente = placa;
        vistaEm = agora;
        estado = new Estado(true, "DETECTADA", "Placa identificada. Confira antes de registrar a entrada.",
                UUID.randomUUID().toString(), placa, confidence, Instant.ofEpochMilli(agora));
        ultimaConfirmada = estado;
    }
}
