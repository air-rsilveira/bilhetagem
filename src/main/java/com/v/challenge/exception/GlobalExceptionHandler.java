package com.v.challenge.exception;

import com.v.challenge.dto.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LockIndisponivelException.class)
    public ResponseEntity<ErrorResponse> handleLockIndisponivel(LockIndisponivelException ex) {
        return ResponseEntity.status(422)
            .body(new ErrorResponse("LOCK_INDISPONIVEL", ex.getMessage()));
    }

    @ExceptionHandler(CobrancaNaoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleCobrancaNaoEncontrada(CobrancaNaoEncontradaException ex) {
        return ResponseEntity.status(404)
            .body(new ErrorResponse("COBRANCA_NAO_ENCONTRADA", "Cobrança não encontrada"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String detalhes = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ResponseEntity.status(400)
            .body(new ErrorResponse("VALIDACAO_FALHOU", detalhes));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) throws NoResourceFoundException {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        return ResponseEntity.status(500)
            .body(new ErrorResponse("ERRO_INTERNO", "Erro ao criar cobrança."));
    }
}
