package com.v.challenge.repository;

import com.v.challenge.domain.Cobranca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CobrancaRepository extends JpaRepository<Cobranca, Long> {

    @Query("SELECT c FROM Cobranca c WHERE (c.idCobrancaOrigem = :id OR c.id = :id) ORDER BY c.dataCriacao DESC")
    List<Cobranca> findAllVersoes(@Param("id") Long id);

    default Optional<Cobranca> findVersaoMaisRecente(Long id) {
        List<Cobranca> versoes = findAllVersoes(id);
        return versoes.isEmpty() ? Optional.empty() : Optional.of(versoes.get(0));
    }

    Optional<Cobranca> findTopByTxidOrderByDataCriacaoDesc(String txid);

    Optional<Cobranca> findByTransactionId(String transactionId);
}
