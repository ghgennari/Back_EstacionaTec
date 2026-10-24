package br.gov.sp.fatec.itu.estacionatec_api.security;

import java.nio.charset.StandardCharsets;
import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import org.springframework.http.HttpStatus;

public final class PoliticaSenha {
    private PoliticaSenha() { }
    public static boolean forte(String senha) {
        return senha != null && senha.length() >= 12
                && senha.getBytes(StandardCharsets.UTF_8).length <= 72
                && senha.matches("(?s).*[A-Z].*") && senha.matches("(?s).*[a-z].*")
                && senha.matches("(?s).*[0-9].*") && senha.matches("(?s).*[^a-zA-Z0-9\\s].*")
                && senha.chars().noneMatch(Character::isWhitespace);
    }
    public static void validar(String senha) {
        if (!forte(senha)) throw new RegraNegocioException(HttpStatus.BAD_REQUEST,
                "Use pelo menos 12 caracteres, com maiúscula, minúscula, número e símbolo, sem espaços (até 72 bytes).");
    }
}
