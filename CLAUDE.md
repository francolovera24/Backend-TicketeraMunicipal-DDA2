# Ticketera Municipal - Backend

Contexto: TP de Desarrollo de Aplicaciones II (UADE, Mg. Christian Parkinson).
Sistema de reclamos de infraestructura urbana: los vecinos reportan problemas
(cableado expuesto, baches, luminarias, arbolado, ruidos) y el municipio los
prioriza, asigna a cuadrillas y resuelve. Diferencial: un componente de IA que
genera un resumen priorizado por zona.

Este repo es el BACKEND. Los diagramas UML en `docs/` son la fuente de verdad del
diseno. Si algo del codigo contradice un diagrama, senalalo antes de decidir.

## Stack (decidido)
- Java 17, Spring Boot 3.3.x, Maven
- Spring Web, Spring Data JPA, Spring AMQP, Validation, springdoc-openapi
- PostgreSQL 16, RabbitMQ 3 (con management), Redis 7 (reservado, opcional)
- Docker: Dockerfile multi-etapa + docker-compose (app, postgres, rabbitmq, redis)
- Configuracion externalizada por variables de entorno. Sin secretos en el repo.

## Alcance
DENTRO: Primera Parte completa (arquitectura en capas, patrones Factory,
Repository, Strategy, Observer, Facade, servicios por componentes, eventos de
dominio) + mensajeria real con RabbitMQ + componente de IA + una API externa real.

FUERA (no implementar):
- Sistema legado y `SOAP_Legacy`: descartados a proposito. NO agregarlos.
- Autenticacion/autorizacion y frontend.
- Outbox pattern: solo mencionarlo como mejora futura.

PENDIENTE (decision abierta): la Segunda Parte exige al menos un servicio SOAP.
Idea: `SOAP_Reclamos`, consulta de estado de un reclamo, que delega en
`SvcReclamos`. No hacerlo hasta que se pida.

## Paquetes (base: com.municipio.ticketera)
controller, service, repository, domain, patterns/{factory,strategy,observer},
messaging, config, dto. La IA vive dentro de `service` (SvcIA).

## Mapeo diagrama -> Java
| Diagrama | Java |
|---|---|
| REST_Reclamos / REST_Ciudadanos / REST_ResumenZona | ReclamoController / CiudadanoController / ResumenZonaController |
| Svc_Reclamos (Facade) | SvcReclamos |
| Svc_Zonas / Svc_Cuadrillas / Svc_IA / Svc_Ciudadanos | SvcZonas / SvcCuadrillas / SvcIA / SvcCiudadanos |
| Repo_Reclamo / Repo_Zona / Repo_Cuadrilla / Repo_Ciudadano | ReclamoRepository / BarrioRepository / CuadrillaRepository / CiudadanoRepository |
| Broker, Consumidor_Eventos, Evento | Broker, ConsumidorEventos, Evento |
| Cache_Resumenes | CacheResumenes |
| API_Geo / API_LLM | GeoClient / LlmClient (implementa GeneradorDeResumen) |

## Dominio
- Reclamo: id, descripcion, tipo, ubicacion (value object: direccion, lat, lon),
  barrio, ciudadano, estado, fechas, scoreCriticidad, urgente.
  Metodos: cambiarEstado, marcarUrgente, calcularAntiguedad (horas).
- Estado: NUEVO, EN_ANALISIS, ASIGNADO, EN_PROCESO, RESUELTO, RECHAZADO.
- TipoDeReclamo (enum con pesoRiesgo): CABLEADO 10, BACHEO 5, ALUMBRADO 6,
  ARBOLADO 2, RUIDOS_MOLESTOS 3. Sin metodos de negocio.
- Ciudadano (historialReclamos, agregarReclamoAlHistorial), Barrio (catalogo,
  sin metodos), Cuadrilla (especialidad, disponible, marcarDisponible/Ocupada).
- ResumenDeZona: Value Object inmutable, NO se persiste.

## Endpoints REST (documentar con springdoc/OpenAPI)
- POST /reclamos, GET /reclamos?barrio=, PUT /reclamos/{id}/estado
- POST /ciudadanos, GET /ciudadanos/{id}, GET /ciudadanos/{id}/reclamos
- GET /resumen-zona?barrio=
- Errores con @RestControllerAdvice, logging SLF4J.

## Patrones (implementarlos tal cual)
- Factory Method: `ReclamoFactory` abstracta (metodo plantilla `crear` valida y
  llama al hook `construir`) + una fabrica @Component por tipo. CABLEADO se crea
  urgente. `SvcReclamos` NUNCA hace `new Reclamo`.
- Facade: `SvcReclamos` orquesta factory + repositorio + broker.
- Strategy: `CriticidadStrategy` + una por tipo (Cableado, Bacheo) + `ScoreGenerico`
  de respaldo. Formula: pesoRiesgo*k + min(antiguedadHoras, tope) + similaresEnZona*m.
  Cableado k=10 tope=48 m=5; Bacheo k=5 tope=72 m=15; Generico k=5 tope=96 m=3.
- Observer: `Sujeto` (implementa Broker) y `Observador` (SvcCuadrillas, SvcIA).
- Repository: interfaces Spring Data JPA.

## Mensajeria (RabbitMQ) - decisiones cerradas
- Exchange topic `ticketera.eventos`. Colas durables, mensajes persistentes.
- `Evento`: eventId (UUID), tipo, timestamp, version, correlationId, reclamoId, barrio.
- Routing keys: reclamo.creado, reclamo.asignado, reclamo.resuelto, zona.resumen.
- `Broker.publicar` completa eventId/timestamp/correlationId y publica SOLO a
  RabbitMQ. NO debe llamar a los observadores en memoria (un esqueleto previo lo
  hacia y dejaba las colas sin consumidores: es un error conocido).
- `ConsumidorEventos` con @RabbitListener por cola, entrega al Observador:
  - cuadrillas.eventos: bindings reclamo.creado y reclamo.resuelto -> SvcCuadrillas
  - ia.eventos: binding reclamo.# -> SvcIA (invalida la cache de esa zona)
  - `zona.resumen` NO se bindea a ia.eventos (evita un ciclo de invalidacion).
- Confiabilidad: ACK tras procesar, reintentos limitados (3, con backoff), luego
  Dead Letter Exchange -> cola `ticketera.eventos.dlq`.
- Idempotencia: tabla `evento_procesado` con eventId unico en Postgres; el
  consumidor descarta duplicados. No usar un Set en memoria.
- "suscribir" = registrar el observador y su binding/listener; "notificar" =
  publicar al exchange.

## Componente de IA (SvcIA)
Flujo de GET /resumen-zona: cache (TTL 5 min) -> si no hay, leer reclamos activos
-> score por Strategy -> ordenar -> texto via puerto `GeneradorDeResumen` -> guardar
en cache -> publicar `zona.resumen`.
- `GeneradorDeResumen` (interfaz): `GeneradorDeResumenStub` (por defecto, sin
  credenciales) y `LlmClient` real (RestClient, URL/clave/modelo por env:
  LLM_URL, LLM_API_KEY, LLM_MODEL), elegido por perfil o propiedad.
- Resiliencia: timeout y fallback (si el LLM falla, devolver solo el ranking).
- Privacidad: al LLM solo tipo, barrio y descripcion; nunca nombre ni contacto.
- Cache en memoria; Redis queda como mejora.
- API externa real: `GeoClient` con Nominatim (direccion -> barrio). Respetar su
  politica de uso: User-Agent propio y maximo 1 pedido por segundo.

## Como trabajar
Etapas, con un commit por etapa y `docker compose up --build` funcionando al final
de cada una:
1. Proyecto base + docker-compose (app, postgres, rabbitmq, redis).
2. Dominio + repositorios.
3. Patrones + servicios de negocio.
4. Controladores REST + DTOs + manejo de errores + OpenAPI.
5. Mensajeria completa (consumidores, reintentos, DLQ, idempotencia).
6. Componente de IA + GeoClient.
7. Tests: unitarios (factories, strategies) e integracion con Testcontainers.
Reglas: nombres del diagrama, codigo y comentarios en espanol, README con como
correrlo y ejemplos curl. Ante una duda de diseno, preguntar antes de inventar.

## Material en el repo
- `docs/`: diagramas PlantUML (clases, secuencia x2, componentes, despliegue).
- `referencia/`: esqueleto previo, DESACTUALIZADO en mensajeria. Usar solo como
  guia para dominio, factories y strategies; el diseno de este archivo manda.
