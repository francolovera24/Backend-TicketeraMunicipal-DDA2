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
| `ticketera-app` | 8080 | Spring Boot (Swagger UI en http://localhost:8080/swagger-ui.html) |
| `ticketera-postgres` | 5432 | PostgreSQL 16 |
| `ticketera-rabbitmq` | 5672 / 15672 | RabbitMQ 3 (panel en http://localhost:15672, usuario/clave de `.env`) |
| `ticketera-redis` | 6379 | Redis 7 (reservado, la cache hoy es en memoria) |

La app espera a que postgres y rabbitmq esten *healthy* antes de arrancar.

## Estado
Etapa 1: proyecto base + infraestructura. Los endpoints y ejemplos `curl` se
agregan en la etapa 4.
