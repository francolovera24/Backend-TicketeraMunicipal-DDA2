# Ticketera Municipal - Backend

Backend del sistema de reclamos de infraestructura urbana (TP Desarrollo de
Aplicaciones II, UADE). El diseno esta en `docs/` (PlantUML) y las decisiones en
`CLAUDE.md`.

## Arquitectura

Monolito modular con base de datos compartida, pensado para evolucionar a
microservicios: un solo JAR y un solo contenedor con los modulos Reclamos,
Ciudadanos, Cuadrillas e IA, que se comunican por llamadas en proceso y por
eventos en RabbitMQ. Detalle y motivos en `CLAUDE.md` ("Arquitectura: decision
de despliegue"); diagramas en `docs/`.

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
        "titulo": "Cable colgando sobre la vereda",
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
| `stub` (por defecto) | `GeneradorDeResumenStub`: plantilla fija, sin modelo |
| `llm` | `LlmClient`: Gemma 4 E4B local (`google/gemma-4-E4B-it`) |

El modelo corre en `ia/servidor.py` (Transformers). El backend le pide lo mismo
que antes: el resumen de zona y si un reclamo es duplicado. No usa la API de
Gemini ni una API key de Google.

En otra terminal, desde `ticketera/ia` (hace falta aceptar la licencia del
modelo en Hugging Face y, si la pide, `HF_TOKEN`):

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -U -r requirements.txt
python servidor.py
```

En `.env` (nunca en el repo). La app en Docker llega al proceso del host por
`host.docker.internal`; si Spring corre en el host, `LLM_URL=http://localhost:8090`.

```
IA_GENERADOR=llm
LLM_URL=http://host.docker.internal:8090
LLM_MODEL=google/gemma-4-E4B-it
```

`GET /salud` responde `listo` cuando el modelo termino de cargar. La primera
vez descarga los pesos. El razonamiento queda apagado (`enable_thinking=false`)
para que la respuesta sea directa; el servicio igual usa `parse_response` y
descarta el pensamiento. Si el pedido trae `imagen_url`, la imagen entra antes
del texto, como en la ficha del modelo. Los reclamos de esta app no tienen
foto, asi que resumen y duplicados siguen siendo solo texto.

Opcional, en CPU y sin la GPU del host: `docker compose --profile ia up gemma`.
- Al modelo solo se envian barrio, tipo, titulo y descripcion, hasta 20 reclamos.
  Antes se anonimizan el titulo y la descripcion (`util.Anonimizador`): se borran
  el nombre y el contacto del vecino y cualquier email, DNI o telefono. No
  detecta nombres de terceros escritos en el texto.
- Timeouts de 5 s (conexion) y 120 s (lectura). Si el LLM falla o no responde,
  se devuelve el ranking con un texto de fallback (`generadoPorIa: false`), que
  se cachea solo 30 s para reintentar pronto. Un resumen generado se cachea 5 min.

### Deteccion de duplicados

Cuando varios vecinos reportan el mismo problema, el segundo reporte queda en
estado `DUPLICADO` con `reclamoOriginalId` y no recibe cuadrilla:

1. Reglas: reclamos activos del mismo tipo, de los ultimos 30 dias y a menos de
   150 m (si no hay coordenadas, del mismo barrio). Casi siempre no hay
   candidatos y no se consulta al LLM.
2. Si hay candidatos, el LLM decide si alguno describe el mismo problema. Solo
   recibe tipo, titulo, descripcion y distancia en metros.

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

`NominatimRealIT` y `GemmaRealIT` arrancan la aplicacion con PostgreSQL y
RabbitMQ de Testcontainers. No se ejecutan con `mvn test`: el sufijo `IT`
permite seleccionarlas explicitamente. Nominatim llama a la API oficial;
Gemma llama al proceso local.

Nominatim: registrar una direccion publica de CABA sin barrio ni coordenadas,
comprobar que ambos se completan y verificar que quedan persistidos:

```bash
mvn test -Dtest=NominatimRealIT
```

Gemma: con `ia/servidor.py` ya en `http://localhost:8090` y el modelo cargado.
La prueba activa `llm`, crea dos reclamos de distinto tipo, espera su
validacion y consulta el resumen. Exige el cliente real, texto no vacio,
ausencia de fallback y el ranking esperado. Tambien comprueba que dos reportes
del mismo problema se vinculan como duplicados:

```bash
mvn test -Dtest=GemmaRealIT
```

Maven no carga `.env` automaticamente. La prueba fija la URL del servicio en
`http://localhost:8090`. Docker Compose si carga `.env`.
`HF_TOKEN` (si hace falta para bajar el modelo) se define solo en el proceso
de Python, nunca en los comandos documentados ni en Git.

Cada prueba exitosa guarda la solicitud o los datos de entrada y la respuesta
en `target/evidencias/nominatim.json`, `target/evidencias/gemma.json` o
`target/evidencias/gemma-duplicados.json`.
Revisar el texto generado para comprobar que describe los problemas y respeta
su prioridad: la prueba automatica no evalua toda su exactitud semantica.
Los archivos de evidencia son locales y `target/` esta ignorado por Git.
Nominatim requiere Internet. Gemma requiere el proceso local y, la primera
vez, descarga del modelo.
Ver la [evidencia de ejecucion](docs/evidencias/apis-reales.md).

## Errores
Formato RFC 7807 (`application/problem+json`): 400 datos invalidos, 401 sin
token o token invalido, 403 rol insuficiente, 404 recurso inexistente, 409 conflicto (contacto repetido, transicion invalida, modificacion
concurrente), 500 error inesperado.

## Estado
Etapas 1 a 7 completas: infraestructura, dominio, patrones, servicios, API
REST, mensajeria, IA con Gemma 4 E4B local (resumen y deteccion de duplicados),
geolocalizacion y tests.

Mejora futura: patron Outbox, para no perder un evento si RabbitMQ no esta
disponible justo despues del commit (hoy queda registrado en el log).
