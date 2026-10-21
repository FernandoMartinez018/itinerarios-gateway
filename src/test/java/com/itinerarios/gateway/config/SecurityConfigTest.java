package com.itinerarios.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

@SpringBootTest(properties = "spring.security.oauth2.client.registration.gateway.client-secret=test")
class SecurityConfigTest {

    @Autowired
    ApplicationContext context;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient().build();
    }

    @Test
    void apiSinSesion_responde401SinRedirigir() {
        client.get().uri("/api/itineraries").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void actuatorHealth_esPublico() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void inicioDeLogin_redirigeAKeycloakPorElMismoOrigenConPkce() {
        URI location = client.get().uri("http://demo.test/oauth2/authorization/keycloak").exchange()
                .expectStatus().isFound()
                .returnResult(Void.class).getResponseHeaders().getLocation();

        assertThat(location).isNotNull();
        assertThat(location.getPath()).isEqualTo("/realms/itinerarios/protocol/openid-connect/auth");
        assertThat(location.getHost()).isNull(); // relativa: Keycloak va detras del nginx del frontend
        assertThat(location.getQuery())
                .contains("client_id=gateway", "code_challenge_method=S256", "code_challenge=",
                        "redirect_uri=http://demo.test/login/oauth2/code/keycloak");
    }

    @Test
    void authMe_devuelveElNombreDelUsuarioDeKeycloak() {
        client.mutateWith(mockOidcLogin().idToken(t -> t
                        .claim("name", "Ana Gomez").claim("preferred_username", "agomez").claim("email", "f@example.test")))
                .get().uri("/auth/me").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Ana Gomez")
                .jsonPath("$.username").isEqualTo("agomez");
    }

    @Test
    void escrituraSinTokenCsrf_esRechazada() {
        client.mutateWith(mockOidcLogin())
                .post().uri("/api/itineraries").bodyValue("{}").exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void logout_cierraTambienLaSesionDeKeycloakYVuelveAlLogin() {
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token-123").claim("sub", "u1").build();

        URI location = client.mutateWith(mockOidcLogin().idToken(t -> t.tokenValue("id-token-123").claims(c -> c.putAll(idToken.getClaims()))))
                .mutateWith(csrf())
                .post().uri("http://demo.test/logout").exchange()
                .expectStatus().isFound()
                .returnResult(Void.class).getResponseHeaders().getLocation();

        assertThat(location).isNotNull();
        assertThat(location.getPath()).isEqualTo("/realms/itinerarios/protocol/openid-connect/logout");
        assertThat(location.getQuery())
                .contains("client_id=gateway", "id_token_hint=id-token-123",
                        "post_logout_redirect_uri=http://demo.test/login");
    }
}
