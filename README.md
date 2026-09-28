# Ticketera Municipal - Backend

Backend del sistema de reclamos de infraestructura urbana (TP Desarrollo de
Aplicaciones II, UADE). El diseno esta en `docs/` (PlantUML) y las decisiones en
`CLAUDE.md`.

## Requisitos
- Docker y Docker Compose.
- (Opcional, para desarrollo local) Java 17 y Maven 3.9.

## Como correrlo

```bash
cp .env.example .env   # ajustar las claves
docker compose up --build
```

Levanta cuatro contenedores:

| Contenedor | Puerto | Uso |
|---|---|---|
| `ticketera-app` | 8080 | Spring Boot |
| `ticketera-postgres` | 5432 | PostgreSQL 16 (esquema con Flyway) |
| `ticketera-rabbitmq` | 5672 / 15672 | RabbitMQ 3 (panel en http://localhost:15672, usuario/clave de `.env`) |
| `ticketera-redis` | 6379 | Redis 7 (reservado, la cache hoy es en memoria) |

La app espera a que postgres y rabbitmq esten *healthy* antes de arrancar.

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI (JSON): http://localhost:8080/v3/api-docs

## Ejemplos con curl

Registrar un ciudadano (guardar el `id` que devuelve):
```bash
curl -X POST http://localhost:8080/ciudadanos \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Ana Perez","contacto":"ana.perez@example.com"}'
```

Registrar un reclamo (el barrio se busca sin distinguir mayusculas ni acentos):
```bash
curl -X POST http://localhost:8080/reclamos \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: demo-1" \
  -d '{
        "ciudadanoId": "<id-del-ciudadano>",
        "tipo": "CABLEADO",
        "descripcion": "Cable pelado colgando sobre la vereda",
        "direccion": "Av. Santa Fe 3200",
        "barrio": "Palermo"
      }'
```
Tipos: `CABLEADO`, `BACHEO`, `ALUMBRADO`, `ARBOLADO`, `RUIDOS_MOLESTOS`.

Consultar reclamos (todos, de un barrio, o uno por id):
```bash
curl http://localhost:8080/reclamos
curl "http://localhost:8080/reclamos?barrio=Palermo"
curl http://localhost:8080/reclamos/<id-del-reclamo>
```

Cambiar el estado:
```bash
curl -X PUT http://localhost:8080/reclamos/<id-del-reclamo>/estado \
  -H "Content-Type: application/json" \
  -d '{"estado":"EN_PROCESO"}'
```
Transiciones permitidas: `NUEVO -> EN_ANALISIS | ASIGNADO | RECHAZADO`,
`EN_ANALISIS -> ASIGNADO | RECHAZADO`, `ASIGNADO -> EN_PROCESO | RESUELTO`,
`EN_PROCESO -> RESUELTO`. Otra transicion devuelve 409.

Consultar un ciudadano y su historial:
```bash
curl http://localhost:8080/ciudadanos/<id-del-ciudadano>
curl http://localhost:8080/ciudadanos/<id-del-ciudadano>/reclamos
```

Resumen priorizado de un barrio:
```bash
curl "http://localhost:8080/resumen-zona?barrio=Palermo"
```

## Errores
Formato RFC 7807 (`application/problem+json`): 400 datos invalidos, 404 recurso
inexistente, 409 conflicto (contacto repetido, transicion invalida, modificacion
concurrente), 500 error inesperado.

## Estado
Etapas 1 a 4: infraestructura, dominio, patrones, servicios y API REST.
Pendiente: mensajeria completa (consumidores, reintentos, DLQ), LLM real y
geolocalizacion, tests.
