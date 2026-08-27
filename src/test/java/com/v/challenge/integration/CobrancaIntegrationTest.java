package com.v.challenge.integration;

import com.v.challenge.domain.Cobranca;
import com.v.challenge.domain.CobrancaMetodoEnum;
import com.v.challenge.domain.CobrancaStatusEnum;
import com.v.challenge.domain.CobrancaTipoEnum;
import com.v.challenge.dto.CobrancaBasicoResponseDTO;
import com.v.challenge.dto.CobrancaCompletoResponseDTO;
import com.v.challenge.dto.CobrancaRequestDTO;
import com.v.challenge.repository.CobrancaRepository;
import com.v.challenge.security.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Testes de Integração (end-to-end) - Cobranças")
class CobrancaIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CobrancaRepository repository;

    private static final String ID_USUARIO = "user-123";

    @BeforeEach
    void limparBase() {
        repository.deleteAll();
    }

    private String tokenValido() {
        return JwtTokenUtil.generateToken(
            ID_USUARIO, "Joao", "Silva", "12345678900", 3600);
    }

    private HttpHeaders headersComToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @DisplayName("Deve criar e consultar cobrança PIX (fluxo completo)")
    void deveCriarEConsultarCobrancaPix() {
        // POST - criação
        CobrancaRequestDTO request = new CobrancaRequestDTO(
            new BigDecimal("100.00"),
            CobrancaTipoEnum.RECARGA,
            CobrancaMetodoEnum.PIX);

        HttpEntity<CobrancaRequestDTO> postEntity =
            new HttpEntity<>(request, headersComToken(tokenValido()));

        ResponseEntity<CobrancaBasicoResponseDTO> postResponse = restTemplate.postForEntity(
            "/api/v1/cobrancas", postEntity, CobrancaBasicoResponseDTO.class);

        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postResponse.getBody()).isNotNull();
        assertThat(postResponse.getBody().id()).isNotNull();
        assertThat(postResponse.getBody().txid()).isNotBlank();

        Long id = postResponse.getBody().id();

        // GET - consulta
        HttpEntity<Void> getEntity = new HttpEntity<>(headersComToken(tokenValido()));

        ResponseEntity<CobrancaCompletoResponseDTO> getResponse = restTemplate.exchange(
            "/api/v1/cobrancas/" + id, HttpMethod.GET, getEntity,
            CobrancaCompletoResponseDTO.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().idUsuario()).isEqualTo(ID_USUARIO);
        // Cobrança PIX em SOLICITADA é consultável: a consulta externa reconcilia
        // o status para AGUARDANDO_PAGAMENTO criando uma nova versão.
        assertThat(getResponse.getBody().status())
            .isEqualTo(CobrancaStatusEnum.AGUARDANDO_PAGAMENTO);
        assertThat(getResponse.getBody().metodo()).isEqualTo(CobrancaMetodoEnum.PIX);
    }

    @Test
    @DisplayName("Deve retornar 401 quando não há token")
    void deveRetornar401SemToken() {
        CobrancaRequestDTO request = new CobrancaRequestDTO(
            new BigDecimal("50.00"),
            CobrancaTipoEnum.RECARGA,
            CobrancaMetodoEnum.PIX);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CobrancaRequestDTO> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/cobrancas", entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Deve retornar 401 com token inválido/malformado")
    void deveRetornar401ComTokenInvalido() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("token.malformado.invalido");
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/cobrancas/1", HttpMethod.GET, entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Deve processar webhook PIX e finalizar cobrança (nova versão)")
    void deveProcessarWebhookPixEFinalizarCobranca() {
        // Persiste uma cobrança PIX diretamente via repositório
        Cobranca cobranca = new Cobranca();
        cobranca.setIdUsuario(ID_USUARIO);
        cobranca.setNomeSolicitante("Joao Silva");
        cobranca.setTipo(CobrancaTipoEnum.RECARGA);
        cobranca.setMetodo(CobrancaMetodoEnum.PIX);
        cobranca.setStatus(CobrancaStatusEnum.SOLICITADA);
        cobranca.setValorSolicitacao(new BigDecimal("100.00"));
        cobranca.setTxid("PIX-INT-TEST");
        cobranca.setDataCriacao(LocalDateTime.now());
        Cobranca salva = repository.save(cobranca);
        Long idOriginal = salva.getId();

        // Monta corpo do webhook (PixWebhookDTO com um item)
        Map<String, Object> item = Map.of(
            "txid", "PIX-INT-TEST",
            "horario", "2024-01-01T10:00:00Z",
            "valor", 100.00);
        Map<String, Object> body = Map.of("pix", List.of(item));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Void> response = restTemplate.postForEntity(
            "/api/v1/cobrancas/webhook/pix", entity, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verifica que uma nova versão finalizada foi criada
        Cobranca versaoAtual = repository.findVersaoMaisRecente(idOriginal).orElseThrow();
        assertThat(versaoAtual.getStatus()).isEqualTo(CobrancaStatusEnum.FINALIZADA);
        assertThat(versaoAtual.getValorPago()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(versaoAtual.getIdCobrancaOrigem()).isEqualTo(idOriginal);
        assertThat(versaoAtual.getId()).isNotEqualTo(idOriginal);
    }

    @Test
    @DisplayName("Deve retornar 400 para request inválido (valor negativo)")
    void deveRetornar400ParaRequestInvalido() {
        CobrancaRequestDTO request = new CobrancaRequestDTO(
            new BigDecimal("-10.00"),
            CobrancaTipoEnum.RECARGA,
            CobrancaMetodoEnum.PIX);

        HttpEntity<CobrancaRequestDTO> entity =
            new HttpEntity<>(request, headersComToken(tokenValido()));

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/cobrancas", entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Webhook deve ser acessível sem autenticação")
    void webhookDeveSerAcessivelSemAutenticacao() {
        Map<String, Object> body = Map.of("pix", List.of());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Void> response = restTemplate.postForEntity(
            "/api/v1/cobrancas/webhook/pix", entity, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
