# Ticketera Municipal - Backend

Esqueleto funcional del backend, generado a partir del diagrama de clases
(dominio + arquitectura + patrones). Cubre el nivel de **TP Inicial + TP
Primera Parte**: capas, los 4 patrones de diseño, REST, persistencia y
mensajería asincrónica real (RabbitMQ).

## Cómo correrlo

Requisito único: Docker y Docker Compose instalados.

```bash
docker-compose up --build
```

Esto levanta 4 contenedores:
- **app** (Spring Boot, puerto 8080)
- **postgres** (puerto 5432)
- **rabbitmq** (puerto 5672, panel de administración en http://localhost:15672 con guest/guest)
- **redis** (puerto 6379, reservado para cuando se quiera cambiar la caché en memoria de `Svc_IA` por una real)

## Probar los endpoints

Crear un reclamo:
```bash
curl -X POST http://localhost:8080/reclamos \
  -H "Content-Type: application/json" \
  -d '{"descripcion":"Cable pelado en la vereda","tipo":"CABLEADO","barrio":"Palermo"}'
```

Consultar reclamos de una zona:
```bash
curl "http://localhost:8080/reclamos?barrio=Palermo"
```

Cambiar el estado de un reclamo:
```bash
curl -X PUT http://localhost:8080/reclamos/1/estado \
  -H "Content-Type: application/json" \
  -d '{"estado":"ASIGNADO"}'
```

Obtener el resumen de zona generado por el componente de IA:
```bash
curl "http://localhost:8080/resumen-zona?barrio=Palermo"
```

## Dónde está cada patrón en el código

| Patrón | Paquete / clase |
|---|---|
| Factory Method | `patterns/factory/ReclamoFactory.java` (abstracta) + `CableadoReclamoFactory`, `BacheoReclamoFactory`, `ArboladoReclamoFactory`, `AlumbradoReclamoFactory`, `RuidosReclamoFactory` |
| Facade | `service/SvcReclamos.java` (anotada `@Service`, orquesta Factory + Repository + Broker) |
| Strategy | `patterns/strategy/CriticidadStrategy.java` (interfaz) + `ScoreCableado`, `ScoreBacheo`, `ScoreGenerico` |
| Observer | `patterns/observer/Sujeto.java` / `Observador.java`, implementado por `messaging/Broker.java` (Sujeto) y `service/SvcCuadrillas.java` / `service/SvcIA.java` (Observadores) |

## Qué falta para las siguientes etapas del TP (a propósito, no es un olvido)

- **SOAP legacy (`ConsultaReclamoLegacy`)**: no está implementado. Se agrega en
  la Segunda Parte con Spring-WS (necesita definir el WSDL/XSD primero).
- **Llamada real al LLM**: `GeneradorDeResumenStub` devuelve un texto fijo de
  ejemplo. Reemplazar por una llamada real (Anthropic, OpenAI, etc.) en
  `GeneradorDeResumen`, respetando la regla de no enviar datos de contacto del
  ciudadano en el prompt.
- **API de geolocalización (Nominatim/OSM)**: el barrio hoy se recibe como
  texto plano desde el DTO; falta el servicio que lo resuelva a partir de una
  dirección.
- **Caché real (Redis)**: `Svc_IA` cachea en memoria (`ConcurrentHashMap`);
  el contenedor `redis` ya está en el `docker-compose.yml` para cuando se
  quiera migrar.
- **Autenticación/autorización**: no incluida, fuera del alcance de este
  esqueleto.

## Estructura de paquetes

```
domain/       -> entidades JPA + Value Object (ResumenDeZona)
patterns/     -> Factory Method, Strategy, Observer (interfaces + implementaciones)
messaging/    -> Broker (Sujeto), conectado a RabbitMQ
service/      -> Svc_Reclamos (Facade), Svc_Zonas, Svc_Cuadrillas, Svc_IA
repository/   -> interfaces Spring Data JPA
controller/   -> REST controllers
config/       -> configuración de RabbitMQ
dto/          -> objetos de transferencia para los endpoints REST
```
