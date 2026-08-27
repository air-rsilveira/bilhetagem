package com.v.challenge.lock;

import com.v.challenge.exception.LockIndisponivelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LockExecutorTest {

    @Mock
    private LockService lockService;

    private LockExecutor lockExecutor;

    private static final String LOCK_KEY = "cobrancas:user-123";
    private static final Duration TTL = Duration.ofSeconds(5);

    @BeforeEach
    void setUp() {
        lockExecutor = new LockExecutor(lockService);
    }

    @Test
    void deveExecutarSupplierERetornarResultadoQuandoLockAdquirido() {
        when(lockService.tryLock(LOCK_KEY, TTL)).thenReturn(true);

        String resultado = lockExecutor.executeWithLock(LOCK_KEY, TTL, () -> "sucesso");

        assertThat(resultado).isEqualTo("sucesso");
        verify(lockService).tryLock(LOCK_KEY, TTL);
        verify(lockService).unlock(LOCK_KEY);
    }

    /**
     * TESTE OBRIGATÓRIO #7: Garante que o lock é liberado no finally
     * mesmo quando o supplier lança exceção.
     */
    @Test
    void deveGarantirUnlockNoFinallyMesmoComExcecaoDoSupplier() {
        when(lockService.tryLock(LOCK_KEY, TTL)).thenReturn(true);

        assertThatThrownBy(() ->
            lockExecutor.executeWithLock(LOCK_KEY, TTL, () -> {
                throw new RuntimeException("Erro inesperado no supplier");
            })
        ).isInstanceOf(RuntimeException.class)
         .hasMessage("Erro inesperado no supplier");

        // VERIFICA que unlock foi chamado MESMO com exceção
        verify(lockService).unlock(LOCK_KEY);
    }

    @Test
    void deveLancarLockIndisponivelQuandoLockNaoAdquirido() {
        when(lockService.tryLock(LOCK_KEY, TTL)).thenReturn(false);

        assertThatThrownBy(() ->
            lockExecutor.executeWithLock(LOCK_KEY, TTL, () -> "nao deve executar")
        ).isInstanceOf(LockIndisponivelException.class)
         .hasMessage("Geração de cobrança em andamento.");
    }

    @Test
    void naoDeveChamarUnlockQuandoLockNaoFoiAdquirido() {
        when(lockService.tryLock(LOCK_KEY, TTL)).thenReturn(false);

        assertThatThrownBy(() ->
            lockExecutor.executeWithLock(LOCK_KEY, TTL, () -> "nao deve executar")
        ).isInstanceOf(LockIndisponivelException.class);

        verify(lockService, never()).unlock(anyString());
    }
}
