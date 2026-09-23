package br.gov.sp.fatec.itu.estacionatec_api.exceptions;

import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.Mensagem;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<Mensagem> regra(RegraNegocioException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new Mensagem(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Mensagem> validacao(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(java.util.stream.Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new Mensagem(message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Mensagem> integridade() {
        return ResponseEntity.status(409).body(new Mensagem("Cadastro duplicado ou vinculado a outro registro."));
    }

    @ExceptionHandler({IllegalArgumentException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Mensagem> formato() {
        return ResponseEntity.badRequest().body(new Mensagem("Dados da requisição inválidos."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Mensagem> tamanho() {
        return ResponseEntity.status(413).body(new Mensagem("A imagem deve ter até 5 MB."));
    }
}
