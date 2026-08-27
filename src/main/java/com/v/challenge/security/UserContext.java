package com.v.challenge.security;

public record UserContext(
    String idUsuario,
    String givenName,
    String familyName,
    String cpf
) {
    public String getNomeCompleto() {
        return givenName + " " + familyName;
    }
}
