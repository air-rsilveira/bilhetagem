package com.v.challenge.controller;

import com.v.challenge.dto.*;
import com.v.challenge.service.CobrancaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cobrancas")
@RequiredArgsConstructor
@Validated
public class CobrancaController {

    private final CobrancaService cobrancaService;

    @PostMapping
    public ResponseEntity<CobrancaBasicoResponseDTO> criarCobranca(
            @Valid @RequestBody CobrancaRequestDTO request) {
        CobrancaBasicoResponseDTO response = cobrancaService.criarCobranca(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CobrancaCompletoResponseDTO> consultarCobranca(
            @PathVariable Long id) {
        CobrancaCompletoResponseDTO response = cobrancaService.consultarCobranca(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/webhook/pix")
    public ResponseEntity<Void> processarWebhookPix(
            @RequestBody PixWebhookDTO webhook) {
        cobrancaService.processarWebhookPix(webhook);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{transactionId}/validate")
    public ResponseEntity<Void> validarCheckout(
            @PathVariable String transactionId,
            @Valid @RequestBody CheckoutValidateRequestDTO request) {
        cobrancaService.validarCheckout(transactionId, request);
        return ResponseEntity.ok().build();
    }
}
