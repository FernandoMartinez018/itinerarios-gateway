package com.itinerarios.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Genera (o respeta, si ya viene del cliente) un X-Correlation-ID por request
 * y lo propaga hacia los microservicios downstream, además de devolverlo en
 * la respuesta. Ver sección 63 de la especificación maestra.
 *
 * Este filtro corre antes que el enrutamiento (orden muy alto de prioridad)
 * para garantizar que TODA request, incluida la que falla el ruteo, tenga
 * un identificador.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdGlobalFilter.class);

    public static final String HEADER_NAME = "X-Correlation-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HEADER_NAME, correlationId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
        String id = correlationId;
        // set (no add) justo antes de enviar: el servicio de abajo ya devuelve el header y quedaba duplicado.
        mutatedExchange.getResponse().beforeCommit(() -> {
            mutatedExchange.getResponse().getHeaders().set(HEADER_NAME, id);
            return Mono.empty();
        });

        // WebFlux no usa MDC por hilo: el Correlation ID va explicito en la linea de acceso (seccion 63).
        return chain.filter(mutatedExchange).doFinally(signal -> {
            if (!mutatedRequest.getPath().value().startsWith("/actuator")) {
                log.info("[{}] {} {} -> {}", id, mutatedRequest.getMethod(), mutatedRequest.getPath(),
                        mutatedExchange.getResponse().getStatusCode());
            }
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
