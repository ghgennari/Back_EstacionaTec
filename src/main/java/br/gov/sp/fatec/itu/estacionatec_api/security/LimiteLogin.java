package br.gov.sp.fatec.itu.estacionatec_api.security;

import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

@Component
public class LimiteLogin {
    private record Janela(int tentativas, long inicio) { }
    private static final long DURACAO = Duration.ofMinutes(1).toNanos();
    private static final int MAX_CHAVES = 10_000;
    private final Map<String, Janela> contas = new HashMap<>();
    private final Map<String, Janela> origens = new HashMap<>();
    private final LongSupplier relogio;

    public LimiteLogin() {
        this(System::nanoTime);
    }

    LimiteLogin(LongSupplier relogio) {
        this.relogio = relogio;
    }

    public synchronized void iniciar(String identificador, String origem) {
        long agora = relogio.getAsLong();
        contas.values().removeIf(j -> agora - j.inicio() >= DURACAO);
        origens.values().removeIf(j -> agora - j.inicio() >= DURACAO);
        String conta = chave(identificador);
        validar(contas, conta, 5);
        validar(origens, origem, 120);
        incrementar(contas, conta, agora);
        incrementar(origens, origem, agora);
    }

    public synchronized void sucesso(String identificador) {
        contas.remove(chave(identificador));
    }

    private String chave(String identificador) {
        // Identificadores maiores não cabem nos cadastros; não os retemos integralmente em memória.
        return identificador.length() > 256 ? "identificador-excessivo" : identificador.trim().toLowerCase(Locale.ROOT);
    }

    private void validar(Map<String, Janela> mapa, String chave, int limite) {
        Janela janela = mapa.get(chave);
        if ((janela != null && janela.tentativas() >= limite)
                || (janela == null && mapa.size() >= MAX_CHAVES)) {
            throw new RegraNegocioException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas de login. Aguarde um minuto antes de tentar novamente.");
        }
    }

    private void incrementar(Map<String, Janela> mapa, String chave, long agora) {
        mapa.compute(chave, (k, janela) -> janela == null ? new Janela(1, agora)
                : new Janela(janela.tentativas() + 1, janela.inicio()));
    }
}
