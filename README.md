# itinerarios-gateway

API Gateway — Sistema de Itinerarios Personales.

## Responsabilidad

- Único punto de entrada para Angular (sección 33/103): nunca se expone Airport Service
  o Itinerary Service directamente al frontend.
- Routing hacia `airport-service` e `itinerary-service`.
- CORS.
- Correlation ID (genera o respeta `X-Correlation-ID` y lo propaga downstream).
- Rate limiting básico en memoria (Nivel 1 — migrar a Redis en Nivel 2, ver comentario en `SimpleRateLimitGlobalFilter`).
- **No** contiene lógica de negocio, no toca PostgreSQL, no valida aeropuertos.

## Stack

- Java 21
- Spring Boot 4.1.1
- Spring Cloud 2025.1.1 ("Oakwood") — primera línea de Spring Cloud confirmada compatible con Spring Boot 4.x
- Spring Cloud Gateway (stack reactivo WebFlux)

## ⚠️ Verificación pendiente antes de ejecutar

Este es un ecosistema (Spring Boot 4 + Spring Cloud Gateway sobre Spring Framework 7) muy
reciente al momento de generar este scaffold, y no se pudo validar contra Maven Central
desde este entorno. Antes de dar esto por funcional:

1. Confirmar el `artifactId` exacto del starter tras el renombre de artefactos del release
   train 2025.0 (`spring-cloud-starter-gateway-server-webflux` es el nombre esperado, pero
   verificar contra el BOM real).
2. Confirmar si la clave de configuración de rutas en `application.yml` sigue siendo
   `spring.cloud.gateway.routes` o cambió a `spring.cloud.gateway.server.webflux.routes`
   (ver nota extensa dentro del propio `application.yml`).
3. Ejecutar `mvn dependency:tree` y revisar que no haya conflictos de versión entre
   Spring Framework 7 (traído por Spring Boot 4) y Spring Cloud Gateway.

Si alguno de estos tres puntos falla, la alternativa documentada es bajar temporalmente
`itinerarios-gateway` a Spring Boot 3.5.x + Spring Cloud 2024.0.x (línea anterior, estable
pero en EOL) mientras el ecosistema 4.x/2025.x madura — ver `ADR-010-spring-cloud-version.md`.

## Ejecutar localmente

```bash
export AIRPORT_SERVICE_URL=http://localhost:8081
export ITINERARY_SERVICE_URL=http://localhost:8082
export FRONTEND_URL=http://localhost:4200

mvn spring-boot:run
```

## Rutas

| Path | Destino |
|---|---|
| `/api/airports/**` | `airport-service` |
| `/api/itineraries/**` | `itinerary-service` |
| `/api/notifications/**` | `notification-service` |

## Observabilidad (Nivel 2, Fase 11)

Agente Java de OpenTelemetry hacia Jaeger (vía `otel-collector`), y métricas
Micrometer/Prometheus en `/actuator/prometheus`. Como el Gateway es el primer punto
de entrada, sus trazas son la raíz visible de cada solicitud completa en Jaeger —
útil para ver de un vistazo cuánto tiempo se va en cada microservicio downstream.

## Pendiente (Nivel 2+)

- Rate limiting respaldado por Redis (`RequestRateLimiter` oficial), apto multi-instancia.
- Propagación de JWT cuando se implemente seguridad.
