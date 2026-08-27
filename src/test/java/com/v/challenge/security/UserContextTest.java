package com.v.challenge.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextTest {

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void getNomeCompleto_deveConcatenarNomes() {
        UserContext context = new UserContext("user-1", "João", "Silva", "12345678900");

        assertThat(context.getNomeCompleto()).isEqualTo("João Silva");
    }

    @Test
    void userContextHolder_deveArmazenarERecuperarContexto() {
        UserContext context = new UserContext("user-1", "João", "Silva", "12345678900");

        UserContextHolder.setContext(context);

        UserContext retrieved = UserContextHolder.getContext();
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.idUsuario()).isEqualTo("user-1");
        assertThat(retrieved.givenName()).isEqualTo("João");
        assertThat(retrieved.familyName()).isEqualTo("Silva");
        assertThat(retrieved.cpf()).isEqualTo("12345678900");
    }

    @Test
    void userContextHolder_clearDeveRemoverContexto() {
        UserContext context = new UserContext("user-1", "João", "Silva", "12345678900");
        UserContextHolder.setContext(context);

        UserContextHolder.clear();

        assertThat(UserContextHolder.getContext()).isNull();
    }

    @Test
    void userContextHolder_deveIsolarEntreThreads() throws InterruptedException {
        UserContext context = new UserContext("user-main", "Maria", "Santos", "98765432100");
        UserContextHolder.setContext(context);

        AtomicReference<UserContext> otherThreadContext = new AtomicReference<>();

        Thread thread = new Thread(() -> otherThreadContext.set(UserContextHolder.getContext()));
        thread.start();
        thread.join();

        assertThat(otherThreadContext.get()).isNull();
        assertThat(UserContextHolder.getContext()).isEqualTo(context);
    }
}
