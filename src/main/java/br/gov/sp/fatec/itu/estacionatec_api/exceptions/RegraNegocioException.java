package br.gov.sp.fatec.itu.estacionatec_api.exceptions;

import org.springframework.http.HttpStatus;

public class RegraNegocioException extends RuntimeException {
    private final HttpStatus status;
    private final String codigo;

    public RegraNegocioException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public RegraNegocioException(HttpStatus status, String message, String codigo) {
        super(message);
        this.status = status;
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
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
