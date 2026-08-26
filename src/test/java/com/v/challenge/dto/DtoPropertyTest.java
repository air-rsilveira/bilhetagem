package com.v.challenge.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.v.challenge.domain.CobrancaMetodoEnum;
import com.v.challenge.domain.CobrancaStatusEnum;
import com.v.challenge.domain.CobrancaTipoEnum;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests para DTOs e Exception Handler.
 */
class DtoPropertyTest {

    private static final Validator validator;
    private static final ObjectMapper objectMapper;

    static {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    // ===== Property 1: Validação rejeita valores não-positivos =====
    // **Validates: Requirements 1.1**

    @Property
    void validacaoRejeitaValorNulo() {
        CobrancaRequestDTO dto = new CobrancaRequestDTO(null, null, null);
        Set<ConstraintViolation<CobrancaRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("valor"));
    }

    @Property
    void validacaoRejeitaValorZero() {
        CobrancaRequestDTO dto = new CobrancaRequestDTO(BigDecimal.ZERO, null, null);
        Set<ConstraintViolation<CobrancaRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("valor"));
    }

    @Property
    void validacaoRejeitaValorNegativo(@ForAll @Negative BigDecimal valor) {
        CobrancaRequestDTO dto = new CobrancaRequestDTO(valor, null, null);
        Set<ConstraintViolation<CobrancaRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("valor"));
    }

    // ===== Property 2: Validação rejeita strings em branco =====
    // **Validates: Requirements 1.4**

    @Property
    void validacaoRejeitaCheckoutComCamposBrancos(@ForAll("whitespaceStrings") String ws) {
        CheckoutValidateRequestDTO dto = new CheckoutValidateRequestDTO(ws, ws, ws);
        Set<ConstraintViolation<CheckoutValidateRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSizeGreaterThanOrEqualTo(3);
    }

    @Provide
    Arbitrary<String> whitespaceStrings() {
        return Arbitraries.of("", " ", "  ", "\t", "\n", " \t\n ");
    }

    // ===== Property 3: Round-trip de serialização de PixWebhookDTO =====
    // **Validates: Requirements 1.5**

    @Property
    void roundTripPixWebhookDTO(@ForAll("pixWebhookDtos") PixWebhookDTO original) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(original);
        PixWebhookDTO deserialized = objectMapper.readValue(json, PixWebhookDTO.class);
        assertThat(deserialized).isEqualTo(original);
    }

    @Provide
    Arbitrary<PixWebhookDTO> pixWebhookDtos() {
        Arbitrary<PixWebhookItemDTO> itemArb = Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(35),
            Arbitraries.of(
                LocalDateTime.of(2024, 1, 15, 10, 30, 0),
                LocalDateTime.of(2024, 6, 20, 14, 45, 0),
                LocalDateTime.of(2023, 12, 1, 8, 0, 0)
            ),
            Arbitraries.bigDecimals().between(BigDecimal.ONE, new BigDecimal("99999.99"))
                .ofScale(2)
        ).as(PixWebhookItemDTO::new);

        return itemArb.list().ofMinSize(0).ofMaxSize(5)
            .map(PixWebhookDTO::new);
    }

    // ===== Property 4: Serialização de Response DTOs preserva todos os campos =====
    // **Validates: Requirements 2.3, 2.4**

    @Property
    void serializacaoCobrancaCompletoPreservaCampos(@ForAll("cobrancaCompletoDtos") CobrancaCompletoResponseDTO dto) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(dto);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("id")).isTrue();
        assertThat(node.has("txid")).isTrue();
        assertThat(node.has("idUsuario")).isTrue();
        assertThat(node.has("tipo")).isTrue();
        assertThat(node.has("metodo")).isTrue();
        assertThat(node.has("status")).isTrue();
        assertThat(node.has("valorSolicitado")).isTrue();
        assertThat(node.has("valorPago")).isTrue();
        assertThat(node.has("dataCriacao")).isTrue();
        assertThat(node.has("dataExpiracao")).isTrue();
        assertThat(node.has("dataFinalizada")).isTrue();
        assertThat(node.size()).isEqualTo(11);
    }

    @Provide
    Arbitrary<CobrancaCompletoResponseDTO> cobrancaCompletoDtos() {
        return Combinators.combine(
            Arbitraries.longs().between(1L, 100000L),
            Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(35),
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20),
            Arbitraries.of(CobrancaTipoEnum.values()),
            Arbitraries.of(CobrancaMetodoEnum.values()),
            Arbitraries.of(CobrancaStatusEnum.values()),
            Arbitraries.bigDecimals().between(BigDecimal.ONE, new BigDecimal("99999.99")).ofScale(2),
            Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal("99999.99")).ofScale(2)
        ).as((id, txid, idUsuario, tipo, metodo, status, valorSol, valorPago) ->
            new CobrancaCompletoResponseDTO(
                id, txid, idUsuario, tipo, metodo, status, valorSol, valorPago,
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                LocalDateTime.of(2024, 1, 2, 10, 0, 0),
                LocalDateTime.of(2024, 1, 3, 10, 0, 0)
            )
        );
    }

    // ===== Property 5: LockIndisponivelException preserva mensagem na resposta =====
    // **Validates: Requirements 3.1**

    @Property
    void lockIndisponivelPreservaMensagem(@ForAll @StringLength(min = 1, max = 100) String mensagem) {
        var handler = new com.v.challenge.exception.GlobalExceptionHandler();
        var ex = new com.v.challenge.exception.LockIndisponivelException(mensagem);
        var response = handler.handleLockIndisponivel(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().codigo()).isEqualTo("LOCK_INDISPONIVEL");
        assertThat(response.getBody().mensagem()).isEqualTo(mensagem);
    }

    // ===== Property 6: Exceções genéricas sempre retornam resposta fixa =====
    // **Validates: Requirements 3.3**

    @Property
    void excecaoGenericaRetornaRespostaFixa(@ForAll @StringLength(min = 0, max = 200) String mensagem) {
        var handler = new com.v.challenge.exception.GlobalExceptionHandler();
        var ex = new Exception(mensagem);
        var response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().codigo()).isEqualTo("ERRO_INTERNO");
        assertThat(response.getBody().mensagem()).isEqualTo("Erro ao criar cobrança.");
    }

    // ===== Property 7: ErrorResponse serializa exatamente dois campos =====
    // **Validates: Requirements 3.5**

    @Property
    void errorResponseSerializaDoisCampos(
            @ForAll @StringLength(min = 1, max = 50) String codigo,
            @ForAll @StringLength(min = 1, max = 100) String mensagem) throws JsonProcessingException {
        ErrorResponse errorResponse = new ErrorResponse(codigo, mensagem);
        String json = objectMapper.writeValueAsString(errorResponse);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.size()).isEqualTo(2);
        assertThat(node.has("codigo")).isTrue();
        assertThat(node.has("mensagem")).isTrue();
    }

    // ===== Property 8: Round-trip de serialização de CobrancaEventDTO =====
    // **Validates: Requirements 4.1, 4.2**

    @Property
    void roundTripCobrancaEventDTO(@ForAll("cobrancaEventDtos") CobrancaEventDTO original) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(original);
        CobrancaEventDTO deserialized = objectMapper.readValue(json, CobrancaEventDTO.class);
        assertThat(deserialized).isEqualTo(original);
    }

    @Provide
    Arbitrary<CobrancaEventDTO> cobrancaEventDtos() {
        return Combinators.combine(
            Arbitraries.longs().between(1L, 100000L),
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20),
            Arbitraries.of(CobrancaStatusEnum.values()),
            Arbitraries.of(CobrancaStatusEnum.values()),
            Arbitraries.of(
                LocalDateTime.of(2024, 1, 15, 10, 30, 0),
                LocalDateTime.of(2024, 6, 20, 14, 45, 0),
                LocalDateTime.of(2023, 12, 1, 8, 0, 0)
            ),
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(30)
        ).as(CobrancaEventDTO::new);
    }
}
