# Segunda Parte: integracion y evidencias

Esta guia reune los servicios, contratos y resultados del backend separado.
El codigo de referencia es el commit `976c46b`; las ejecuciones registradas
corresponden al 4 de octubre de 2026. Los datos de prueba son sinteticos.

## Cobertura de la entrega

| Requisito | Implementacion | Contrato o documentacion | Evidencia |
|---|---|---|---|
| Un servicio SOAP con WSDL y contrato | Consulta de estado en Reclamos | [XSD](../src/main/resources/xsd/reclamos.xsd), WSDL `/ws/reclamos.wsdl` | Cuatro casos de `SoapReclamosIntegracionTest` y consulta SOAP entre contenedores |
| Al menos dos servicios REST con OpenAPI | API de Reclamos en 8080 y API de Resumen por Zona en 8081 | OpenAPI `/v3/api-docs` en cada aplicacion; contratos abajo | `DespliegueComposeIT` comprueba rutas exclusivas, OpenAPI y JWT compartido |
| Mensajeria y colas | RabbitMQ, exchange topic y colas durables | [Flujo de mensajes](06_diagrama_flujo_mensajes.puml) y contrato abajo | Eventos entre procesos, idempotencia, reintento y DLQ |
| Integracion SOA / servicios ligeros | Dos aplicaciones con ejecutables y contenedores propios | [Separacion de servicios](separacion-servicios.md), [aplicaciones](09_diagrama_aplicaciones.puml) y [despliegue](05_diagrama_despliegue.puml) | Reclamos acepta el alta con IA detenida; el mensaje se procesa al volver IA |
| Productores y consumidores | Brokers por aplicacion; ConsumidorIA y ConsumidorCuadrillas | [Broker](../src/main/java/com/municipio/ticketera/messaging/Broker.java), [consumidores](../src/main/java/com/municipio/ticketera/messaging/) | Alta, validacion, asignacion, resolucion y reasignacion de pendientes |
| Una API externa real | Nominatim desde Reclamos | [Configuracion y pruebas](evidencias/apis-reales.md) | Direccion sin barrio ni coordenadas; HTTP 201 y geodatos persistidos |
| IA funcional y accesible por REST o cola | Gemini redacta el resumen; `SvcIA` consume eventos para validar y detectar duplicados | [IA en README](../README.md#componente-de-ia), `GET /resumen-zona` | Texto real sin fallback, ranking esperado y duplicado vinculado sin cuadrilla |
| Evidencias de prueba | Suite habitual y pruebas explicitas de Compose y APIs | [Indice de evidencias](#evidencias-conservadas) | 206 pruebas habituales y 7 explicitas, sin fallos, errores ni omisiones |

El despliegue conserva un unico proyecto de codigo y un esquema PostgreSQL
compartido. Las aplicaciones se ejecutan por separado; la mensajeria interna
incluye las responsabilidades de notificacion de eventos. Redis esta reservado:
la cache de resumenes es en memoria, dentro del proceso de IA.

## Contratos REST

Las rutas usan HTTP local. Swagger esta en `/swagger-ui.html` y OpenAPI en
`/v3/api-docs`, en cada puerto. El contrato se genera desde los controladores y
DTO; las pruebas comprueban que Reclamos no publica `/resumen-zona` y que IA
publica solamente esa ruta.

| API | Operacion | Entrada | Resultado y acceso |
|---|---|---|---|
| Reclamos, 8080 | `POST /reclamos` | `ciudadanoId`, `tipo`, `descripcion`, `direccion`; `barrio`, `lat` y `lon` opcionales | 201 y reclamo creado; publico. Cuando faltan geodatos, se consulta Nominatim |
| Reclamos, 8080 | `GET /reclamos/{id}` | UUID del reclamo | 200 y reclamo; publico, 404 si no existe |
| Reclamos, 8080 | `GET /reclamos?barrio=...` | Barrio opcional | 200 y lista; ADMIN |
| Reclamos, 8080 | `PUT /reclamos/{id}/estado` | `{"estado":"EN_PROCESO"}` u otra transicion permitida | 200; ADMIN. Pedir ASIGNADO directamente devuelve 409 |
| Reclamos, 8080 | `PUT /reclamos/{id}/asignar-cuadrilla` | `{"cuadrillaId":"<UUID>"}` | 200; ADMIN. Requiere cuadrilla libre de la especialidad y reclamo pendiente |
| Resumen por Zona, 8081 | `GET /resumen-zona?barrio=...&tipo=...&desde=...` | Barrio obligatorio; tipo y fecha ISO opcionales | 200 con ranking, texto y timestamp; ADMIN. Barrio inexistente: 404 |

La autenticacion usa `/auth/registro` y `/auth/login` en Reclamos. El JWT
emitido alli se acepta en IA porque ambas aplicaciones comparten `JWT_SECRET`.
Una ruta protegida devuelve 401 sin token o con uno invalido, y 403 con rol
VECINO. Los errores REST usan `application/problem+json`.

Los esquemas de respuesta completos estan en OpenAPI de cada aplicacion.
Los ejemplos de ciudadanos, cuadrillas, autenticacion y filtros estan en
[README](../README.md#ejemplos-con-curl).
La fecha `desde` filtra desde el inicio del dia en la zona horaria configurada;
la API actual no tiene un parametro de fecha final.

## Contrato SOAP

| Elemento | Valor |
|---|---|
| WSDL | `GET http://localhost:8080/ws/reclamos.wsdl` |
| Endpoint | `POST http://localhost:8080/ws`, `Content-Type: text/xml` |
| Servicio / portType | `ReclamosService` / `ReclamosPort` |
| Namespace | `http://municipio.com/ticketera/reclamos` |
| Operacion | `consultarEstadoReclamo` |
| Pedido | `consultarEstadoReclamoRequest`, campo `id` con formato UUID |
| Respuesta | `consultarEstadoReclamoResponse/reclamo`: id, tipo, estado, urgente, barrio, cuadrillaAsignada, reclamoOriginalId opcional y fechas |
| Errores | UUID mal formado o reclamo inexistente: SOAP Fault de cliente; otros errores: Fault de servidor |

El servicio es propio de este backend y permite la consulta desde clientes
externos. El XSD es la fuente del contrato; el WSDL y las clases JAXB se generan
a partir de el. La consulta es publica, como `GET /reclamos/{id}`.

La [peticion XML de ejemplo](../README.md#servicio-soap) permite consultar el
ID devuelto por un alta REST. Las pruebas verifican WSDL, respuesta de consulta,
reclamo inexistente y rechazo de un ID mal formado.

## Contrato de mensajes

Exchange `ticketera.eventos`, tipo topic, durable. Cada aplicacion publica
mediante su instancia de Broker. El cuerpo JSON es el record
[Evento](../src/main/java/com/municipio/ticketera/patterns/observer/Evento.java):

| Campo | Significado |
|---|---|
| `eventId` | UUID de publicacion; junto con la cola identifica el procesamiento |
| `tipo` | Valor de `TipoEvento`; determina la routing key |
| `timestamp` | Instante registrado por Broker al completar el evento |
| `version` | Version del mensaje, actualmente 1 |
| `correlationId` | Correlacion de la peticion o evento |
| `reclamoId` | UUID del reclamo; null para `zona.resumen` |
| `barrio` | Barrio afectado |

| Routing key | Productor | Cola / consumidor | Efecto |
|---|---|---|---|
| `reclamo.creado` | SvcReclamos | `ia.eventos` / ConsumidorIA | Valida, detecta duplicados, calcula score e invalida cache |
| `reclamo.validado` | SvcIA | Ambas colas | Cuadrillas asigna un equipo; IA invalida cache |
| `reclamo.asignado` | SvcCuadrillas | `ia.eventos` / ConsumidorIA | Invalida cache |
| `reclamo.resuelto` | SvcReclamos | Ambas colas | Libera y reasigna cuadrilla; invalida cache |
| `reclamo.estado_cambiado` | SvcReclamos | `ia.eventos` / ConsumidorIA | Invalida todas las variantes del resumen del barrio |
| `zona.resumen` | SvcIA | Sin binding en las colas actuales | Registra la generacion; evita invalidar el resumen recien creado |

`cuadrillas.eventos` recibe `reclamo.validado` y `reclamo.resuelto`;
`ia.eventos` recibe `reclamo.#`. Los mensajes son persistentes y se publican
despues del commit. Hay confirmaciones de publicacion y ACK al terminar el
consumidor. Un evento repetido se descarta por `(event_id, consumidor)` en
PostgreSQL. La configuracion limita el procesamiento a tres intentos con
backoff; los mensajes rechazados van al exchange fanout `ticketera.eventos.dlx`
y a `ticketera.eventos.dlq`.

La generacion del texto del resumen ocurre al consultar REST. Los eventos
invalidan la cache y validan reclamos de forma asincronica; el proximo GET
recalcula el resumen. No hay generacion anticipada del texto en cada evento.
La publicacion no usa Outbox: un fallo del broker despues del commit puede
perder el evento y se registra en logs. Esta limitacion esta documentada en
[separacion de servicios](separacion-servicios.md#datos-y-eventos).

## APIs externas e IA

Nominatim completa barrio y coordenadas desde la direccion. La evidencia
conservada muestra Avenida Cabildo 2040, Belgrano, latitud -34.5627267 y longitud
-58.4564287, con los mismos datos en la consulta posterior.

El ranking es deterministico: Strategy calcula score por tipo, antiguedad y
reclamos similares. Gemini redacta el texto a partir de los reclamos ordenados;
no decide el orden del ranking. En la ejecucion conservada se uso
`gemini-3.8-flash`: cableado 100 antes de bacheo 25, `generadoPorIa=true` y texto
correspondiente a ambos problemas. Tambien se verifico un duplicado vinculado
al original y sin cuadrilla.

La cache usa barrio normalizado, tipo y fecha como clave, con TTL de cinco
minutos. Ante fallo del generador se conserva el ranking, se indica
`generadoPorIa=false` y el fallback se cachea 30 segundos. El ranking de la
respuesta no demuestra por si solo que respondio Gemini: el modo stub tambien
genera texto; las pruebas reales activan `llm` dentro del contenedor y exigen
ausencia de fallback.

Las descripciones se anonimizan antes de enviarlas al LLM. No se envian los
datos de contacto del ciudadano. La deteccion de nombres de terceros no es
completa. Disponibilidad, cuota y texto del proveedor pueden variar; una
respuesta de respaldo no cuenta como validacion exitosa de Gemini.

## Evidencias conservadas

Las ejecuciones verificadas quedan conservadas como una copia
revisada en [evidencias/segunda-parte/](evidencias/segunda-parte/):

| Archivo | Contenido observado |
|---|---|
| [pruebas.json](evidencias/segunda-parte/pruebas.json) | Resumen extraido de Surefire: 206 pruebas habituales y 7 explicitas; clases, casos de integracion, comandos equivalentes y hashes de las capturas |
| [compose.json](evidencias/segunda-parte/compose.json) | Contratos y permisos, SOAP 200, cambio de timestamp de cache, duplicados, reasignacion, evento retenido con IA detenida y modo integrado |
| [nominatim.json](evidencias/segunda-parte/nominatim.json) | Pedido sin barrio ni coordenadas y respuesta persistida |
| [gemini.json](evidencias/segunda-parte/gemini.json) | Modelo, texto real y ranking con scores 100 y 25 |
| [gemini-duplicados.json](evidencias/segunda-parte/gemini-duplicados.json) | Reclamo original y duplicado vinculado, sin cuadrilla |

Las cuatro capturas conservan las respuestas JSON generadas por las pruebas.
Se normalizan los saltos de linea a LF para versionarlas; los hashes corresponden
a ese contenido. Sus UUID y nombres de barrio pertenecen a entornos
de prueba ya eliminados. Los timestamps internos usan UTC; los campos `fecha`
numericos de las APIs representan segundos Unix. En Nominatim, NUEVO y score 0
son el estado inmediato del alta; esa prueba comprueba geodatos, no la
finalizacion del procesamiento asincronico.

Los archivos no contienen claves, tokens ni cabeceras de autorizacion. Cada
nueva ejecucion guarda resultados locales en `target/evidencias/` y reportes
en `target/surefire-reports/`; no reemplaza automaticamente estas capturas.
La [evidencia de APIs](evidencias/apis-reales.md) conserva los resultados y las
limitaciones observadas del proveedor.

## Reproducir y demostrar

Para levantar el sistema y recorrer sus flujos, seguir
[Como correrlo](../README.md#como-correrlo), los
[ejemplos REST](../README.md#ejemplos-con-curl), la
[consulta SOAP](../README.md#servicio-soap) y el
[recorrido de demostracion](../README.md#recorrido-de-demostracion).
Se necesita `.env` local; para Gemini real, `IA_GENERADOR=llm` y una clave valida.

Para repetir las comprobaciones automaticas se necesita Java 17, Maven 3.9,
Docker corriendo y Docker Compose. Desde la raiz del repo:

```powershell
mvn test
mvn test "-Dtest=DespliegueComposeIT,NominatimRealIT,GeminiRealIT"
```

La segunda orden necesita `LLM_API_KEY` en el entorno de esa terminal, porque
Maven no carga `.env`. La configuracion de las pruebas es independiente del
despliegue manual: usa datos nuevos, puertos aleatorios, y Gemini real solo en
su clase. Si Docker no esta en PATH, agregar `"-Ddocker.bin=<ruta a docker>"`.
Las pruebas eliminan unicamente sus propios contenedores y volumenes.

Para mostrar los resultados: abrir las dos interfaces Swagger y el WSDL,
crear un reclamo, verificar sus geodatos, observar su asignacion por eventos,
consultar el resumen en IA y demostrar la invalidacion de cache tras cambiar
el estado. RabbitMQ Management permite observar las colas y consumidores.
El modo integrado sigue disponible con su archivo Compose; detener un modo
antes de iniciar el otro contra las mismas colas.

Esta entrega documenta integracion y pruebas funcionales. La evidencia actual
no incluye una prueba de carga ni una evaluacion exhaustiva de exactitud del
LLM, correspondientes a la validacion del Integrador.
