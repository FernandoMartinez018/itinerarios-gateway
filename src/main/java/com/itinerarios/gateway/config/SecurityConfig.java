package com.itinerarios.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.web.server.WebFilter;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Seguridad del Gateway como Backend-for-Frontend (BFF, ADR-018):
 * <ul>
 *   <li>Login de usuarios con Keycloak (OIDC Authorization Code + PKCE) hecho aqui, del lado servidor:
 *       el navegador solo tiene una cookie de sesion; los tokens nunca llegan al frontend. Funciona en
 *       HTTP plano (la demo en AWS no tiene TLS, y sin contexto seguro el navegador no expone Web Crypto
 *       para hacer PKCE en una SPA).</li>
 *   <li>{@code /api/**} exige usuario autenticado; sin sesion responde 401 (no redirige: es una API).</li>
 *   <li>CSRF con cookie XSRF-TOKEN legible por Angular (la reenvia en X-XSRF-TOKEN): la sesion va
 *       en cookie, asi que CSRF es obligatorio.</li>
 *   <li>Hacia los servicios internos se sigue usando el token de servicio (Client Credentials, paso 31).</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                  @Value("${gateway.auth.end-session-path}") String endSessionPath) {
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                        // Sin XOR (BREACH): el SPA reenvia tal cual el valor de la cookie.
                        .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler()))
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/**", "/oauth2/**", "/login/oauth2/**").permitAll()
                        .anyExchange().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))
                .oauth2Login(login -> login.authenticationSuccessHandler(new RedirectServerAuthenticationSuccessHandler("/dashboard")))
                .logout(logout -> logout.logoutSuccessHandler(keycloakLogout(endSessionPath)))
                .build();
    }

    /** En WebFlux la cookie XSRF-TOKEN se escribe de forma perezosa: este filtro la materializa en cada respuesta. */
    @Bean
    WebFilter csrfCookieWebFilter() {
        return (exchange, chain) -> {
            Mono<CsrfToken> token = exchange.getAttributeOrDefault(CsrfToken.class.getName(), Mono.empty());
            return token.then(chain.filter(exchange));
        };
    }

    /**
     * Tras cerrar la sesion local cierra tambien la de Keycloak y vuelve a /login. Ruta relativa: Keycloak
     * se publica por el mismo origen que el frontend (nginx), cuyo host cambia entre local y AWS.
     */
    static ServerLogoutSuccessHandler keycloakLogout(String endSessionPath) {
        return (webFilterExchange, authentication) -> {
            var exchange = webFilterExchange.getExchange();
            String base = UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
                    .replacePath(null).replaceQuery(null).build().toUriString();
            UriComponentsBuilder location = UriComponentsBuilder.fromPath(endSessionPath)
                    .queryParam("client_id", "gateway")
                    .queryParam("post_logout_redirect_uri", base + "/login");
            if (authentication != null && authentication.getPrincipal() instanceof OidcUser user) {
                location.queryParam("id_token_hint", user.getIdToken().getTokenValue());
            }
            exchange.getResponse().setStatusCode(HttpStatus.FOUND);
            exchange.getResponse().getHeaders().setLocation(URI.create(location.encode().build().toUriString()));
            return exchange.getResponse().setComplete();
        };
    }

    @Bean
    ReactiveOAuth2AuthorizedClientManager authorizedClientManager(ReactiveClientRegistrationRepository registrations,
                                                                  ReactiveOAuth2AuthorizedClientService clients) {
        AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(registrations, clients);
        manager.setAuthorizedClientProvider(ReactiveOAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }
}
