package com.v.challenge.exception;

import com.v.challenge.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void deveRetornar404ParaCobrancaNaoEncontrada() {
        CobrancaNaoEncontradaException ex = new CobrancaNaoEncontradaException("id 999");
        ResponseEntity<ErrorResponse> response = handler.handleCobrancaNaoEncontrada(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().codigo()).isEqualTo("COBRANCA_NAO_ENCONTRADA");
        assertThat(response.getBody().mensagem()).isEqualTo("Cobrança não encontrada");
    }

    @Test
    void deveRetornar400ParaValidacaoComDetalhes() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        FieldError fieldError1 = new FieldError("dto", "valor", "must be positive");
        FieldError fieldError2 = new FieldError("dto", "tipo", "must not be null");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().codigo()).isEqualTo("VALIDACAO_FALHOU");
        assertThat(response.getBody().mensagem()).contains("valor: must be positive");
        assertThat(response.getBody().mensagem()).contains("tipo: must not be null");
    }
}
