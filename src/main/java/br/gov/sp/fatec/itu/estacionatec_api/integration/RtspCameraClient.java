package br.gov.sp.fatec.itu.estacionatec_api.integration;

import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

@Component
public class RtspCameraClient {
    private final String url;
    private final String ffmpeg;
    private final String transporte;
    private Process processo;
    private byte[] quadro;
    private long instanteQuadro;
    private long ultimaConsulta;
    private long ultimaTentativa;
    private boolean encerrado;

    public RtspCameraClient(@Value("${estacionatec.camera.rtsp-url:}") String url,
            @Value("${estacionatec.camera.ffmpeg:ffmpeg}") String ffmpeg,
            @Value("${estacionatec.camera.rtsp-transport:tcp}") String transporte) {
        this.url = url;
        this.ffmpeg = ffmpeg;
        this.transporte = transporte;
    }

    public boolean configurada() {
        return !url.isBlank();
    }

    public synchronized byte[] capturar() {
        if (encerrado || !configurada()) {
            throw indisponivel("Transmissão RTSP não configurada ou encerrada.");
        }
        ultimaConsulta = System.nanoTime();
        if (processo == null) iniciar();
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (processo != null && (quadro == null || expirou(instanteQuadro, 3))) {
            long restante = limite - System.nanoTime();
            if (restante <= 0) break;
            try {
                TimeUnit.NANOSECONDS.timedWait(this, restante);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw indisponivel("Visualização da câmera interrompida.");
            }
        }
        if (quadro == null || expirou(instanteQuadro, 3)) {
            throw indisponivel("Sem imagem da câmera IP. Verifique se ela está ligada, na mesma rede e com RTSP habilitado.");
        }
        return quadro.clone();
    }

    private void iniciar() {
        if (ultimaTentativa != 0 && !expirou(ultimaTentativa, 3)) {
            throw indisponivel("Reconectando à câmera IP. Aguarde alguns segundos.");
        }
        ultimaTentativa = System.nanoTime();
        try {
            URI endereco = URI.create(url);
            if (!"rtsp".equalsIgnoreCase(endereco.getScheme()) || endereco.getHost() == null
                    || !(transporte.equals("tcp") || transporte.equals("udp"))) {
                throw new IllegalArgumentException();
            }
            // Nunca encaminhar stderr: o FFmpeg pode incluir credenciais da URL nos erros.
            Process novo = new ProcessBuilder(ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin",
                    "-rtsp_transport", transporte, "-min_port", "10000", "-max_port", "10019",
                    "-timeout", "5000000", "-i", url,
                    "-an", "-vf", "fps=5,scale=1280:-2", "-c:v", "mjpeg", "-q:v", "5",
                    "-f", "image2pipe", "pipe:1")
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            processo = novo;
            quadro = null;
            Thread.ofPlatform().daemon().name("camera-rtsp").start(() -> lerQuadros(novo));
            Thread.ofVirtual().name("camera-rtsp-timeout").start(() -> vigiar(novo));
        } catch (IOException exception) {
            throw indisponivel("FFmpeg não encontrado. Verifique estacionatec.camera.ffmpeg na configuração local.");
        } catch (IllegalArgumentException exception) {
            throw indisponivel("Endereço RTSP inválido na configuração local.");
        }
    }

    private void vigiar(Process atual) {
        long inicio = System.nanoTime();
        try {
            while (atual.isAlive()) {
                Thread.sleep(1000);
                synchronized (this) {
                    if (processo != atual) return;
                    if (expirou(ultimaConsulta, 15) || expirou(quadro == null ? inicio : instanteQuadro, 15)) {
                        atual.destroyForcibly();
                        return;
                    }
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            atual.destroyForcibly();
        }
    }

    private void lerQuadros(Process atual) {
        try (var input = new BufferedInputStream(atual.getInputStream())) {
            ByteArrayOutputStream jpeg = null;
            int anterior = -1;
            int valor;
            while ((valor = input.read()) != -1) {
                if (jpeg == null) {
                    if (anterior == 0xff && valor == 0xd8) {
                        jpeg = new ByteArrayOutputStream();
                        jpeg.write(0xff);
                        jpeg.write(0xd8);
                    }
                } else {
                    jpeg.write(valor);
                    if (jpeg.size() > 5 * 1024 * 1024) break;
                    if (anterior == 0xff && valor == 0xd9) {
                        synchronized (this) {
                            if (encerrado || processo != atual || expirou(ultimaConsulta, 15)) break;
                            quadro = jpeg.toByteArray();
                            instanteQuadro = System.nanoTime();
                            notifyAll();
                        }
                        jpeg = null;
                    }
                }
                anterior = valor;
            }
        } catch (IOException ignored) {
            // A próxima consulta informa a desconexão sem revelar o endereço privado.
        } finally {
            atual.destroyForcibly();
            synchronized (this) {
                if (processo == atual) {
                    processo = null;
                    quadro = null;
                    notifyAll();
                }
            }
        }
    }

    private boolean expirou(long instante, int segundos) {
        return System.nanoTime() - instante > TimeUnit.SECONDS.toNanos(segundos);
    }

    private RegraNegocioException indisponivel(String mensagem) {
        return new RegraNegocioException(HttpStatus.SERVICE_UNAVAILABLE, mensagem);
    }

    @PreDestroy
    public synchronized void fechar() {
        encerrado = true;
        if (processo != null) processo.destroyForcibly();
        processo = null;
        quadro = null;
        notifyAll();
    }
}
