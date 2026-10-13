package com.itinerarios.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting básico (sección 33 y 73) para Nivel 1: ventana fija de 1
 * segundo, contador en memoria por IP de origen.
 *
 * IMPORTANTE: esta implementación NO es apta para un despliegue multi-instancia
 * (cada réplica del Gateway tendría su propio contador). Es intencionalmente
 * simple para Nivel 1; en Nivel 2, cuando Redis esté disponible (sección 27),
 * debe migrarse al RequestRateLimiter oficial de Spring Cloud Gateway respaldado
 * por Redis, que sí es apto para múltiples instancias.
 */
@Component
public class SimpleRateLimitGlobalFilter implements GlobalFilter, Ordered {

    private final int maxRequestsPerSecond;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public SimpleRateLimitGlobalFilter(
            @Value("${gateway.rate-limit.requests-per-second:20}") int maxRequestsPerSecond) {
        this.maxRequestsPerSecond = maxRequestsPerSecond;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = resolveClientIp(exchange);
        long currentSecond = System.currentTimeMillis() / 1000;

        Window window = windows.compute(clientIp, (ip, existing) -> {
            if (existing == null || existing.second != currentSecond) {
                return new Window(currentSecond, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (window.count.get() > maxRequestsPerSecond) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null && remoteAddress.getAddress() != null
                ? remoteAddress.getAddress().getHostAddress()
                : "unknown";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private static final class Window {
        final long second;
        final AtomicInteger count;

        Window(long second, AtomicInteger count) {
            this.second = second;
            this.count = count;
        }
    }
}
