package com.itinerarios.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimpleRateLimitGlobalFilterTest {

    @Test
    void permiteRequestsPorDebajoDelLimite() {
        SimpleRateLimitGlobalFilter filter = new SimpleRateLimitGlobalFilter(5);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());

        ServerWebExchange exchange = exchangeFromIp("10.0.0.1");

        for (int i = 0; i < 5; i++) {
            filter.filter(exchange, chain).block();
        }

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void bloqueaConHttp429AlSuperarElLimite() {
        SimpleRateLimitGlobalFilter filter = new SimpleRateLimitGlobalFilter(2);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());

        ServerWebExchange exchange = exchangeFromIp("10.0.0.2");

        filter.filter(exchange, chain).block();
        filter.filter(exchange, chain).block();
        filter.filter(exchange, chain).block(); // tercera request en el mismo segundo -> bloqueada

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private ServerWebExchange exchangeFromIp(String ip) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/airports")
                .remoteAddress(new InetSocketAddress(ip, 54321))
                .build();
        return MockServerWebExchange.from(request);
    }
}
