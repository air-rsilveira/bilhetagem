package com.v.challenge.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cobranca")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cobranca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String idUsuario;

    @Column(nullable = false)
    private String nomeSolicitante;

    @Enumerated(EnumType.STRING)
    private CobrancaTipoEnum tipo;

    @Enumerated(EnumType.STRING)
    private CobrancaMetodoEnum metodo;

    @Enumerated(EnumType.STRING)
    private CobrancaStatusEnum status;

    private BigDecimal valorSolicitacao;
    private BigDecimal valorPago;

    private String txid;

    @Column(columnDefinition = "TEXT")
    private String copiaECola;

    private String transactionId;
    private String acsUrl;

    @Column(columnDefinition = "TEXT")
    private String threeDsPayload;

    private LocalDateTime dataCriacao;
    private LocalDateTime dataExpiracao;
    private LocalDateTime dataFinalizada;

    private Long idCobrancaOrigem;
}
