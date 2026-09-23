package br.gov.sp.fatec.itu.estacionatec_api.exceptions;

import org.springframework.http.HttpStatus;

public class RegraNegocioException extends RuntimeException {
    private final HttpStatus status;

    public RegraNegocioException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static RegraNegocioException conflito(String message) {
        return new RegraNegocioException(HttpStatus.CONFLICT, message);
    }

    public static RegraNegocioException naoEncontrado(String message) {
        return new RegraNegocioException(HttpStatus.NOT_FOUND, message);
    }
}
