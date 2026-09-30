package br.gov.sp.fatec.itu.estacionatec_api.security;

import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

class LimiteLoginTests {
    private final AtomicLong agora = new AtomicLong();
    private final LimiteLogin limite = new LimiteLogin(agora::get);

    @Test
    void limitaIdentificadorNormalizadoELiberaAoFimDaJanelaSemProrrogarBloqueio() {
        for (int i = 0; i < 5; i++) limite.iniciar(" ADMIN@test.com ", "origem-" + i);
        bloqueado(() -> limite.iniciar("admin@test.com", "outra"));
        agora.set(Duration.ofSeconds(59).toNanos());
        bloqueado(() -> limite.iniciar("admin@test.com", "outra"));
        agora.set(Duration.ofMinutes(1).toNanos());
        assertThatCode(() -> limite.iniciar("admin@test.com", "outra")).doesNotThrowAnyException();
    }

    @Test
    void sucessoZeraContadorDaContaMasNaoPermiteContornarLimiteDaOrigem() {
        for (int i = 0; i < 120; i++) {
            limite.iniciar("conta" + i, "origem");
            limite.sucesso("conta" + i);
        }
        bloqueado(() -> limite.iniciar("nova", "origem"));
        assertThatCode(() -> limite.iniciar("nova", "outra-origem")).doesNotThrowAnyException();
        limite.sucesso("nova");
        for (int i = 0; i < 5; i++) limite.iniciar("nova", "outra-origem");
        bloqueado(() -> limite.iniciar("nova", "outra-origem"));
    }

    @Test
    void concorrenciaNaoUltrapassaCincoTentativasDaConta() throws Exception {
        try (var executor = Executors.newFixedThreadPool(10)) {
            var inicio = new CountDownLatch(1);
            var futures = new java.util.ArrayList<Future<Boolean>>();
            for (int i = 0; i < 10; i++) futures.add(executor.submit(() -> {
                inicio.await();
                try { limite.iniciar("conta", "origem"); return true; }
                catch (RegraNegocioException exception) { return false; }
            }));
            inicio.countDown();
            int aceitas = 0;
            for (var future : futures) if (future.get(5, TimeUnit.SECONDS)) aceitas++;
            assertThat(aceitas).isEqualTo(5);
        }
    }

    private void bloqueado(Runnable tentativa) {
        assertThatThrownBy(tentativa::run).isInstanceOfSatisfying(RegraNegocioException.class,
                e -> assertThat(e.getStatus().value()).isEqualTo(429));
    }
}
