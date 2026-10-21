package com.itinerarios.gateway.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Identidad del usuario logueado para el frontend (BFF, ADR-018). No es logica de negocio: solo expone
 * los claims del ID token de Keycloak que el SPA necesita para mostrar el nombre.
 */
@RestController
public class CurrentUserController {

    public record CurrentUser(String name, String username, String email) {
    }

    @GetMapping("/auth/me")
    public CurrentUser me(@AuthenticationPrincipal OidcUser user) {
        String name = user.getFullName() != null ? user.getFullName() : user.getPreferredUsername();
        return new CurrentUser(name, user.getPreferredUsername(), user.getEmail());
    }
}
