# Ticketera Municipal - Backend

Backend del sistema de reclamos de infraestructura urbana (TP Desarrollo de
Aplicaciones II, UADE). El diseno esta en `docs/` (PlantUML) y las decisiones en
`CLAUDE.md`.

## Arquitectura

El backend admite un arranque integrado y dos aplicaciones separadas:

- Reclamos: REST de reclamos, ciudadanos y cuadrillas, autenticacion, SOAP y
  consumidor de `cuadrillas.eventos`.
- Zonas/Resumenes e IA: REST `/resumen-zona`, ranking, Gemini, cache, deteccion
  de duplicados y consumidor de `ia.eventos`.

Ambas aplicaciones usan PostgreSQL compartido y se comunican por eventos en
RabbitMQ. Cada arranque registra explicitamente sus componentes. El modo
integrado conserva todos los modulos y sigue siendo el usado por el
`docker-compose.yml` actual. El despliegue separado con Compose esta pendiente.
Ver [composicion, avances y ejecucion](docs/separacion-servicios.md),
`CLAUDE.md` y el diagrama `docs/09_diagrama_aplicaciones.puml`.

Diagramas en `docs/` (PlantUML). Para verlos como imagen, desde esta carpeta:
```bash
docker run --rm -e PLANTUML_LIMIT_SIZE=16384 -v "${PWD}/docs:/docs" plantuml/plantuml -tsvg -o /docs/_png /docs/*.puml
```
El diagrama de clases esta partido en una vista general (`01`) y cuatro de
detalle (`01a` dominio, `01b` patrones, `01c` servicios, `01d` seguridad).

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

### Ejecutar las aplicaciones separadas con Java

Con PostgreSQL y RabbitMQ disponibles y sus variables de entorno configuradas:

```bash
mvn -Preclamos package -DskipTests
mvn -Pia package -DskipTests
```

Ejecutar en terminales distintas:

```bash
java -jar target/ticketera-reclamos.jar
java -jar target/ticketera-ia.jar
```

Reclamos usa HTTP 8080 e IA HTTP 8081 por defecto. Cada proceso necesita las
variables de conexion a la misma base y broker, y el mismo `JWT_SECRET` para
aceptar los tokens emitidos por Reclamos. `SERVER_PORT` permite cambiar el
puerto de cada proceso. Java no carga `.env` automaticamente.
Detalle en [separacion de servicios](docs/separacion-servicios.md).

## Autenticacion y roles

Hay dos roles. **VECINO** crea reclamos (no necesita cuenta para eso) y
**ADMIN** gestiona: ve todos los reclamos, cambia estados, asigna cuadrillas,
consulta el historial de un ciudadano y el resumen de IA. Esos endpoints piden
un JWT de rol ADMIN en el header `Authorization: Bearer <token>`.

El rol lo decide el backend al registrarse: un email terminado en `@admin.com`
obtiene ADMIN; cualquier otro, VECINO. (Simplificacion del TP: en un sistema
real el alta de administradores no seria autoservicio.)

Registrar un usuario:
```bash
curl -X POST http://localhost:8080/auth/registro \
  -H "Content-Type: application/json" \
  -d '{"email":"operadora@admin.com","password":"clave-segura-123"}'
# 201 {"id":"...","email":"operadora@admin.com","rol":"ADMIN"}
```

Iniciar sesion (el token vale 24 horas):
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"operadora@admin.com","password":"clave-segura-123"}'
# 200 {"token":"eyJ...","tipo":"Bearer","expira":"..."}
```

Guardar el token y usarlo en un request protegido:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"operadora@admin.com","password":"clave-segura-123"}' \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')

curl http://localhost:8080/reclamos -H "Authorization: Bearer $TOKEN"
```
Sin token (o con uno invalido o vencido) la respuesta es **401**; con un token
de VECINO, **403**. En Swagger UI se carga con el boton "Authorize".

`JWT_SECRET` (clave de firma, al menos 32 caracteres) se define en `.env`. Si no
se define se usa un valor de ejemplo solo para desarrollo y la app lo avisa en
el log.

## Ejemplos con curl

Los ejemplos marcados con `$TOKEN` requieren un token de rol ADMIN (ver
"Autenticacion y roles").

Registrar un ciudadano (guardar el `id` que devuelve):
```bash
curl -X POST http://localhost:8080/ciudadanos \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Ana Perez","contacto":"ana.perez@example.com"}'
```

Registrar un reclamo. El barrio es opcional: si no se informa (o faltan las
coordenadas) se obtiene de la direccion con Nominatim. Se busca sin distinguir
mayusculas ni acentos:
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

Consultar reclamos: todos o los de un barrio (ADMIN), o uno por id (publico,
es el "consultar estado" del vecino):
```bash
curl http://localhost:8080/reclamos -H "Authorization: Bearer $TOKEN"
curl "http://localhost:8080/reclamos?barrio=Palermo" -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/reclamos/<id-del-reclamo>
```

Cambiar el estado (ADMIN):
```bash
curl -X PUT http://localhost:8080/reclamos/<id-del-reclamo>/estado \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"estado":"EN_PROCESO"}'
```
En este endpoint: `NUEVO -> EN_ANALISIS | RECHAZADO`,
`EN_ANALISIS -> RECHAZADO`, `ASIGNADO -> EN_PROCESO | RESUELTO`,
`EN_PROCESO -> RESUELTO`. Otra transicion devuelve 409. Para pasar de NUEVO
o EN_ANALISIS a ASIGNADO se usa `asignar-cuadrilla`, que vincula y ocupa la
cuadrilla; pedir ASIGNADO por `/estado` devuelve 409.
`DUPLICADO` solo lo asigna el sistema y es final.

Asignar una cuadrilla a mano (Panel Municipal, ADMIN). Primero se buscan las libres
de la especialidad del reclamo:
```bash
curl "http://localhost:8080/cuadrillas?especialidad=BACHEO&disponible=true" -H "Authorization: Bearer $TOKEN"
curl -X PUT http://localhost:8080/reclamos/<id-del-reclamo>/asignar-cuadrilla \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"cuadrillaId":"<id-de-la-cuadrilla>"}'
```
Devuelve 409 si la cuadrilla esta ocupada o es de otra especialidad, o si el
reclamo ya no espera asignacion.

Consultar un ciudadano y su historial (ADMIN, o el vecino dueno):
```bash
curl http://localhost:8080/ciudadanos/<id-del-ciudadano> -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/ciudadanos/<id-del-ciudadano>/reclamos -H "Authorization: Bearer $TOKEN"
```

Vecino con cuenta: si se da de alta como ciudadano estando logueado, queda
vinculado y despues puede ver solo sus datos y su historial:
```bash
TOKEN_VECINO=$(curl -s -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" \
  -d '{"email":"vecino@gmail.com","password":"clave-segura-123"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
curl -X POST http://localhost:8080/ciudadanos -H "Authorization: Bearer $TOKEN_VECINO" \
  -H "Content-Type: application/json" -d '{"nombre":"Ana Perez","contacto":"ana.perez@example.com"}'
curl http://localhost:8080/ciudadanos/yo -H "Authorization: Bearer $TOKEN_VECINO"
```

Resumen priorizado de un barrio (ADMIN), con filtros opcionales por tipo y por fecha de
creacion (desde el inicio de ese dia, hora de Buenos Aires):
```bash
curl "http://localhost:8080/resumen-zona?barrio=Palermo" -H "Authorization: Bearer $TOKEN"
curl "http://localhost:8080/resumen-zona?barrio=Palermo&tipo=BACHEO&desde=2026-09-01" \
  -H "Authorization: Bearer $TOKEN"
```

## Servicio SOAP

Consulta de estado de un reclamo para integraciones externas (Spring-WS,
contrato en `src/main/resources/xsd/reclamos.xsd`).

- WSDL: http://localhost:8080/ws/reclamos.wsdl
- Endpoint: `POST http://localhost:8080/ws`

```bash
curl -X POST http://localhost:8080/ws -H "Content-Type: text/xml" -d '
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:rec="http://municipio.com/ticketera/reclamos">
  <soapenv:Body>
    <rec:consultarEstadoReclamoRequest>
      <rec:id><id-del-reclamo></rec:id>
    </rec:consultarEstadoReclamoRequest>
  </soapenv:Body>
</soapenv:Envelope>'
```
Un id inexistente o mal formado devuelve un SOAP Fault de cliente.

## Mensajeria (RabbitMQ)

```
ticketera.eventos (topic)
  reclamo.validado, reclamo.resuelto -> cuadrillas.eventos -> SvcCuadrillas
  reclamo.#                          -> ia.eventos         -> SvcIA
ticketera.eventos.dlx (fanout)       -> ticketera.eventos.dlq
```

Flujo de alta: `reclamo.creado` -> `SvcIA` valida el reclamo (duplicados) ->
`reclamo.validado` -> `SvcCuadrillas` asigna.

- `SvcIA`: invalida la cache del resumen del barrio. Ante `reclamo.creado`
  busca si es duplicado; si no lo es, calcula su score inicial y publica
  `reclamo.validado`.
- Cada cambio de estado valido publica un evento: `reclamo.asignado`,
  `reclamo.resuelto` o `reclamo.estado_cambiado` (EN_ANALISIS, EN_PROCESO y
  RECHAZADO). Al consumirlo, SvcIA invalida todas las variantes del resumen del
  barrio; la siguiente consulta lo recalcula. La actualizacion es asincronica.
  El evento general no llega a la cola de cuadrillas.
- `SvcCuadrillas`: con `reclamo.validado` asigna una cuadrilla libre de la
  especialidad (si no hay, queda pendiente); al resolverse, libera la cuadrilla
  y le asigna el reclamo pendiente mas antiguo de ese tipo.
- Los eventos se publican despues del commit, persistentes y con confirmacion
  del broker. El consumidor confirma (ACK) al terminar; si falla reintenta 3
  veces con backoff y despues el mensaje va a la DLQ.
- Idempotencia: tabla `evento_procesado` con clave (eventId, cola); un evento
  repetido se descarta.

Las colas se pueden ver en el panel http://localhost:15672.

## Componente de IA

`GET /resumen-zona` arma el ranking de reclamos activos con las estrategias de
criticidad y pide el texto a un `GeneradorDeResumen`:

| `IA_GENERADOR` | Implementacion |
|---|---|
| `stub` (por defecto) | `GeneradorDeResumenStub`: plantilla fija, sin credenciales |
| `llm` | `LlmClient`: Gemini (Google AI Studio), requiere `LLM_API_KEY` |

Para usar Gemini, en `.env` (nunca en el repo):
```
IA_GENERADOR=llm
LLM_API_KEY=<tu key>
LLM_MODEL=gemini-3.8-flash
```
- Al modelo solo se envian barrio, tipo y descripcion, hasta 20 reclamos. Antes
  se anonimiza la descripcion (`util.Anonimizador`): se borran el nombre y el
  contacto del vecino y cualquier email, DNI o telefono. No detecta nombres de
  terceros escritos en el texto.
- Timeouts de 3 s (conexion) y 20 s (lectura). Si el LLM falla o no responde,
  se devuelve el ranking con un texto de fallback (`generadoPorIa: false`), que
  se cachea solo 30 s para reintentar pronto. Un resumen generado se cachea 5 min.

### Deteccion de duplicados

Cuando varios vecinos reportan el mismo problema, el segundo reporte queda en
estado `DUPLICADO` con `reclamoOriginalId` y no recibe cuadrilla:

1. Reglas: reclamos activos del mismo tipo, de los ultimos 30 dias y a menos de
   150 m (si no hay coordenadas, del mismo barrio). Casi siempre no hay
   candidatos y no se consulta al LLM.
2. Si hay candidatos, el LLM decide si alguno describe el mismo problema. Solo
   recibe tipo, descripcion y distancia en metros.

Si el LLM falla, el reclamo se trata como no duplicado (mejor atender dos veces
que no atender). Con `IA_GENERADOR=stub` la comparacion es por coincidencia de
palabras. `DUPLICADOS_HABILITADO=false` la desactiva.

## Geolocalizacion (Nominatim)

`GeoClient` consulta https://nominatim.openstreetmap.org respetando su politica:
User-Agent propio (con `GEO_CONTACTO` si se define), como maximo 1 pedido por
segundo y resultados cacheados. Si falla, el reclamo se registra igual siempre
que el barrio venga informado. `GEO_HABILITADO=false` lo desactiva.

## Tests

```bash
mvn test
```
Requiere Java 17, Maven y Docker corriendo (los de integracion usan
Testcontainers). La primera vez descarga las imagenes de Postgres y RabbitMQ.
El build de Docker (`docker compose up --build`) no corre los tests.

- **Unitarios** (sin Spring ni Docker): fabricas, estrategias de criticidad,
  transiciones de estado, cache con TTL, detector de duplicados, comparador
  stub y la logica de `LlmClient` y `GeoClient` (armado de prompts y lectura de
  respuestas, sin llamar a Internet).
- **Integracion** (`integracion/`, con Postgres y RabbitMQ reales): alta con
  validacion y asignacion de cuadrilla, reporte duplicado, liberacion de
  cuadrilla y reasignacion del pendiente, resumen de zona con cache e
  invalidacion, idempotencia de consumidores, DLQ, errores de la API, registro
  y login, y autorizacion por rol (401 sin token, 403 con VECINO). Actuan como
  ADMIN: `IntegracionBase` registra un usuario `@admin.com` y agrega su token. Usan el
  stub de IA y Nominatim apagado, asi no dependen de servicios externos.

### Pruebas con APIs externas reales

`NominatimRealIT` y `GeminiRealIT` arrancan la aplicacion con PostgreSQL y
RabbitMQ de Testcontainers y llaman a las APIs oficiales. No se ejecutan con
`mvn test`: el sufijo `IT` permite seleccionarlas explicitamente.

Nominatim: registrar una direccion publica de CABA sin barrio ni coordenadas,
comprobar que ambos se completan y verificar que quedan persistidos:

```bash
mvn test -Dtest=NominatimRealIT
```

Gemini: definir `LLM_API_KEY` en el entorno del proceso; `LLM_MODEL` es opcional
y conserva el valor de `application.yml` si no se informa. La prueba activa
`llm`, crea dos reclamos de distinto tipo, espera su validacion y consulta el
resumen. Exige el cliente real, texto no vacio, ausencia de fallback y el
ranking esperado. Tambien comprueba que dos reportes del mismo problema se
vinculan como duplicados:

```bash
mvn test -Dtest=GeminiRealIT
```

Maven no carga `.env` automaticamente; para estas pruebas la clave debe estar
exportada como variable de entorno. Docker Compose si carga `.env`.
Las credenciales nunca se incluyen en los comandos documentados ni en Git.

Cada prueba exitosa guarda la solicitud o los datos de entrada y la respuesta
en `target/evidencias/nominatim.json`, `target/evidencias/gemini.json` o
`target/evidencias/gemini-duplicados.json`.
Revisar el texto generado para comprobar que describe los problemas y respeta
su prioridad: la prueba automatica no evalua toda su exactitud semantica.
Los archivos de evidencia son locales y `target/` esta ignorado por Git.
Estas pruebas requieren Internet y disponibilidad o cuota del proveedor;
Gemini puede consumir cuota facturable segun la configuracion del proyecto.
Ver la [evidencia de ejecucion](docs/evidencias/apis-reales.md).

## Errores
Formato RFC 7807 (`application/problem+json`): 400 datos invalidos, 401 sin
token o token invalido, 403 rol insuficiente, 404 recurso inexistente, 409 conflicto (contacto repetido, transicion invalida, modificacion
concurrente), 500 error inesperado.

## Estado
Etapas 1 a 7 completas: infraestructura, dominio, patrones, servicios, API
REST, mensajeria, IA con Gemini (resumen y deteccion de duplicados),
geolocalizacion y tests.

Mejora futura: patron Outbox, para no perder un evento si RabbitMQ no esta
disponible justo despues del commit (hoy queda registrado en el log).
