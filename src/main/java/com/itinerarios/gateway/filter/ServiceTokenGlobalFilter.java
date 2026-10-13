package com.itinerarios.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Seguridad S2S (paso 31, seccion 108): el Gateway obtiene su propio token (OAuth2 Client
 * Credentials contra Keycloak, scopes airport/itinerary/notification) y lo pone como Bearer en cada
 * peticion enrutada. Reemplaza cualquier Authorization entrante: un cliente externo no puede colar
 * su propio token hacia los servicios internos. El token se cachea hasta que expira.
 *
 * No hay logica de negocio (seccion 33): solo autentica al Gateway como servicio.
 */
@Component
public class ServiceTokenGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenGlobalFilter.class);
    static final String CLIENT_REGISTRATION = "gateway";

    private final ReactiveOAuth2AuthorizedClientManager authorizedClientManager;

    public ServiceTokenGlobalFilter(ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {
        this.authorizedClientManager = authorizedClientManager;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest.withClientRegistrationId(CLIENT_REGISTRATION)
                .principal(CLIENT_REGISTRATION)
                .build();
        return authorizedClientManager.authorize(request)
                .map(client -> client.getAccessToken().getTokenValue())
                .map(token -> exchange.mutate()
                        .request(r -> r.headers(h -> h.setBearerAuth(token)))
                        .build())
                .switchIfEmpty(Mono.error(new IllegalStateException("Keycloak no devolvio token")))
                .flatMap(chain::filter)
                .onErrorResume(ex -> !exchange.getResponse().isCommitted() && isAuthorizationError(ex), ex -> {
                    // Sin token de servicio los servicios responderian 401: se corta aqui con 503.
                    log.error("No se pudo obtener el token de servicio del Gateway", ex);
                    exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                    return exchange.getResponse().setComplete();
                });
    }

    private static boolean isAuthorizationError(Throwable ex) {
        return ex instanceof IllegalStateException
                || ex.getClass().getPackageName().startsWith("org.springframework.security.oauth2");
    }

    @Override
    public int getOrder() {
        // Despues del Correlation ID (HIGHEST_PRECEDENCE) y antes del enrutado.
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
