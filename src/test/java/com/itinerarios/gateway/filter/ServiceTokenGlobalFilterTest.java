package com.itinerarios.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceTokenGlobalFilterTest {

    private final ReactiveOAuth2AuthorizedClientManager manager = mock(ReactiveOAuth2AuthorizedClientManager.class);
    private final ServiceTokenGlobalFilter filter = new ServiceTokenGlobalFilter(manager);
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = ex -> { forwarded.set(ex); return Mono.empty(); };

    private static OAuth2AuthorizedClient clientWithToken(String token) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("gateway")
                .clientId("gateway").authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("http://keycloak/token").build();
        return new OAuth2AuthorizedClient(registration, "gateway",
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, token, Instant.now(), Instant.now().plusSeconds(300)));
    }

    @Test
    void reemplazaElAuthorizationEntranteConElTokenDeServicio() {
        when(manager.authorize(any())).thenReturn(Mono.just(clientWithToken("token-de-servicio")));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/itineraries")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-colado-por-el-cliente"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get().getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION))
                .containsExactly("Bearer token-de-servicio");
    }

    @Test
    void keycloakNoDisponible_respondeServiceUnavailableSinEnrutar() {
        when(manager.authorize(any())).thenReturn(Mono.error(
                new OAuth2AuthorizationException(new OAuth2Error("server_error", "Keycloak caido", null))));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/itineraries"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
