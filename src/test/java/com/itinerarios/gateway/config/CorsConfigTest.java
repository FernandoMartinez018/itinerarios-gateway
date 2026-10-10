package com.itinerarios.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.reactive.CorsWebFilter;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    private final CorsWebFilter filter = new CorsConfig().corsWebFilter(List.of("http://localhost:4200"));

    @Test
    void preflightDelFrontend_recibeCabecerasCors() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .options("http://localhost:8080/api/itineraries")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"));

        filter.filter(exchange, ex -> Mono.empty()).block();

        assertThat(exchange.getResponse().getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:4200");
    }

    @Test
    void origenNoPermitido_esRechazado() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("http://localhost:8080/api/airports")
                .header(HttpHeaders.ORIGIN, "http://evil.example"));

        filter.filter(exchange, ex -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
