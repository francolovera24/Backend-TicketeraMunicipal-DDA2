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
dominio) + mensajeria real con RabbitMQ + componente de IA + una API externa real
+ autenticacion y autorizacion basica con JWT (ver seccion propia; antes estaba
FUERA y se incorporo a pedido).

FUERA (no implementar):
- Sistema legado y `SOAP_Legacy`: descartados a proposito. NO agregarlos.
- Frontend.
- Outbox pattern: solo mencionarlo como mejora futura.

SOAP (Segunda Parte, implementado): `SoapReclamos` con Spring-WS, contract-first
(`src/main/resources/xsd/reclamos.xsd`, clases generadas en `dto.soap`).
Operacion `consultarEstadoReclamo(id)`, delega en `SvcReclamos.buscarReclamo`.
Endpoint `/ws`, WSDL en `/ws/reclamos.wsdl`. Es un servicio propio para
integraciones externas: NO es un sistema legado.

## Arquitectura: decision de despliegue
**Monolito modular, con base de datos compartida, pensado para evolucionar a
microservicios.**
- Un solo artefacto (`ticketera-backend.jar`) en un solo contenedor (`app`).
  Los "Servicios" de los diagramas (Reclamos, Ciudadanos, Cuadrillas, IA) son
  modulos dentro de ese JAR, no procesos separados.
- Una sola base PostgreSQL compartida por todos los modulos.
- Los modulos se comunican de dos formas: llamadas en proceso (por ejemplo,
  controlador -> servicio) y eventos por RabbitMQ entre el modulo que publica y
  los observadores (SvcCuadrillas, SvcIA). La mensajeria es real aunque todo
  corra en el mismo proceso: es la costura por donde se separaria un modulo.
- Camino de evolucion (no implementado): extraer el modulo de IA a su propio
  JAR/contenedor que consuma `ia.eventos` y exponga `/resumen-zona`, con su
  propia base o solo lectura; los contratos de eventos (`Evento` con `version`)
  ya estan pensados para eso. El Outbox pattern seria necesario en ese paso.
- Motivo: el alcance del TP (un equipo, un despliegue, sin requisitos de
  escalado independiente) no justifica el costo operativo de microservicios.
- Todos los diagramas deben usar esta postura: nada de "microservicios" como
  estado actual.

## Paquetes (base: com.municipio.ticketera)
controller, service, repository, domain, patterns/{factory,strategy,observer},
messaging, config, dto, util. La IA vive dentro de `service` (SvcIA).

Componente de utilidad (`util`, reutilizable e independiente de las capas):
- `Validador` + `ValidacionException`: validaciones comunes (requerido, largo,
  coordenadas, uuid). ReclamoFactory.validar() las usa; la API responde 400 y el
  SOAP un Fault de cliente.
- `Bitacora`: wrapper de SLF4J, formato unico `evento clave=valor`. Todos los
  servicios la usan en lugar de LoggerFactory.
- `ConfiguracionTicketera`: @ConfigurationProperties("ticketera") con las
  secciones ia, geo y duplicados. Nada de @Value sueltos.

## Autenticacion y autorizacion
- Roles: enum `Rol` { VECINO, ADMIN }. Entidad `Usuario` (id, email unico en
  minusculas, passwordHash, rol, fechaAlta) separada de `Ciudadano`: un admin no
  tiene por que ser vecino. `UsuarioRepository` (Spring Data). Migracion V5.
- Registro y login: `AuthController` (`POST /auth/registro`, `POST /auth/login`)
  -> `SvcAuth`. Password con BCrypt, nunca en texto plano. El login responde lo
  mismo para email inexistente y password incorrecta (401).
- Regla de rol (backend, `SvcAuth.rolPara`): email terminado en `@admin.com`
  (sin distinguir mayusculas) -> ADMIN; cualquier otro -> VECINO. Un campo "rol"
  del request se ignora. Simplificacion del TP: en un sistema real el alta de
  un admin no seria autoservicio (invitacion o aprobacion manual).
- JWT (`config.ProveedorJwt`, jjwt): subject = id, claims `email` y `rol`,
  vence en 24 h, firma HMAC-SHA con `JWT_SECRET` (min. 32 bytes, si no la app no
  arranca; jjwt elige HS256/384/512 segun el largo). application.yml y
  docker-compose traen un valor de ejemplo solo para desarrollo, que la app
  avisa en el log.
- `config.JwtAuthenticationFilter`: lee `Authorization: Bearer`, valida la firma y
  arma el Authentication con `ROLE_<rol>` del claim, sin consultar la base. Sin
  header -> anonimo; token invalido o vencido -> 401 (aun en endpoints publicos).
- `config.SeguridadConfig`: API sin sesion, sin CSRF, sin form login ni basic
  auth, `@EnableMethodSecurity`. La autorizacion se declara por endpoint.
- Solo ADMIN (`@PreAuthorize("hasRole('ADMIN')")`): GET /reclamos,
  PUT /reclamos/{id}/estado, PUT /reclamos/{id}/asignar-cuadrilla,
  GET /resumen-zona, GET /cuadrillas.
- ADMIN o el vecino dueno (`Permisos.ADMIN_O_CIUDADANO_PROPIO`, bean
  `config.Permisos`): GET /ciudadanos/{id} y GET /ciudadanos/{id}/reclamos.
  Esta regla si consulta la base (de quien es el ciudadano); el rol sigue
  saliendo del token.
- Solo VECINO: GET /ciudadanos/yo (su ciudadano vinculado).
- Publicos: POST /reclamos (el vecino reclama sin cuenta, con `ciudadanoId`),
  GET /reclamos/{id}, POST /ciudadanos, /auth/**, Swagger y el SOAP /ws.
- Vinculo Usuario-Ciudadano (opcional, migracion V6): si un VECINO logueado
  hace POST /ciudadanos, el ciudadano queda asociado a su cuenta (una cuenta,
  a lo sumo un ciudadano). Sin cuenta se sigue pudiendo reclamar.
- Errores: 401 sin token o token invalido (con `WWW-Authenticate: Bearer`), 403
  con token valido y rol insuficiente; ambos en formato RFC 7807
  (`ManejadorDeErrores` y `RespuestaDeError` para la cadena de filtros).
- Mejora futura: vincular un ciudadano ya existente a una cuenta (hoy solo se
  vincula al darse de alta logueado) y que un vecino logueado solo pueda crear
  reclamos para su propio ciudadano.

## Nombres: Barrio y Zona
Barrio es el unico nombre para la entidad, el repositorio, el servicio
(`SvcBarrios`), metodos y parametros (`similaresEnBarrio`). "Zona" queda solo en
nombres del contrato publico que no se cambian: `ResumenDeZona`,
`GET /resumen-zona` y la routing key `zona.resumen`.

## Mapeo diagrama -> Java
| Diagrama | Java |
|---|---|
| REST_Reclamos / REST_Ciudadanos / REST_ResumenZona | ReclamoController / CiudadanoController / ResumenZonaController |
| Svc_Reclamos (Facade) | SvcReclamos |
| Svc_Barrios / Svc_Cuadrillas / Svc_IA / Svc_Ciudadanos | SvcBarrios / SvcCuadrillas / SvcIA / SvcCiudadanos |
| Repo_Reclamo / Repo_Barrio / Repo_Cuadrilla / Repo_Ciudadano | ReclamoRepository / BarrioRepository / CuadrillaRepository / CiudadanoRepository |
| Broker, Consumidores de eventos, Evento | Broker, ConsumidorEventos (base), ConsumidorIA, ConsumidorCuadrillas, Evento |
| Cache_Resumenes | CacheResumenes |
| API_Geo / API_LLM | GeoClient / LlmClient (implementa GeneradorDeResumen) |
| REST_Auth / Svc_Auth / Repo_Usuario | AuthController / SvcAuth / UsuarioRepository |
| ProveedorJwt / JwtAuthenticationFilter | config.ProveedorJwt / config.JwtAuthenticationFilter |

## Dominio
- Reclamo: id, descripcion, tipo, ubicacion (value object `Ubicacion`: direccion +
  `Coordenadas` opcionales; `Coordenadas` es un record lat/lon siempre valido
  que sabe calcular la distancia a otro punto),
  barrio, ciudadano, estado, fechas, scoreCriticidad, urgente.
  Metodos: cambiarEstado, marcarUrgente, calcularAntiguedad (horas).
- Estado: NUEVO, EN_ANALISIS, ASIGNADO, EN_PROCESO, RESUELTO, RECHAZADO, DUPLICADO.
  Reclamo tiene `reclamoOriginal` (si es DUPLICADO) y `marcarDuplicadoDe`.
- `Reclamo.cambiarEstado` rechaza ASIGNADO y DUPLICADO. Esos estados requieren
  `asignarCuadrilla` (cuadrilla no nula) o `marcarDuplicadoDe` respectivamente.
  PUT /reclamos/{id}/estado con ASIGNADO devuelve 409; se debe usar
  PUT /reclamos/{id}/asignar-cuadrilla. SvcCuadrillas publica reclamo.asignado.
- TipoDeReclamo (enum con pesoRiesgo): CABLEADO 10, BACHEO 5, ALUMBRADO 6,
  ARBOLADO 2, RUIDOS_MOLESTOS 3. Sin metodos de negocio.
- Ciudadano (historialReclamos, agregarReclamoAlHistorial), Barrio (catalogo,
  sin metodos), Cuadrilla (especialidad, disponible, marcarDisponible/Ocupada).
- ResumenDeZona: Value Object inmutable, NO se persiste.

## Endpoints REST (documentar con springdoc/OpenAPI)
- POST /reclamos, GET /reclamos?barrio=, GET /reclamos/{id}, PUT /reclamos/{id}/estado
- PUT /reclamos/{id}/asignar-cuadrilla (asignacion manual del Panel, delega en
  SvcCuadrillas.asignarCuadrilla), GET /cuadrillas?especialidad=&disponible=
- POST /ciudadanos, GET /ciudadanos/{id}, GET /ciudadanos/{id}/reclamos
- POST /auth/registro, POST /auth/login (ver "Autenticacion y autorizacion" para
  que endpoints requieren rol ADMIN)
- GET /resumen-zona?barrio=&tipo=&desde= (tipo y desde opcionales; desde es una
  fecha ISO interpretada en `ticketera.zona-horaria`; la clave de cache incluye
  los tres filtros y un evento del barrio invalida todas sus variantes)
- Errores con @RestControllerAdvice, logging SLF4J.

## Patrones (implementarlos tal cual)
- Factory Method: `ReclamoFactory` abstracta (metodo plantilla `crear` valida y
  llama al hook `construir`) + una fabrica @Component por tipo. CABLEADO se crea
  urgente. `SvcReclamos` NUNCA hace `new Reclamo`.
- Facade: `SvcReclamos` orquesta factory + repositorio + broker.
- Strategy: `CriticidadStrategy` + una por tipo (Cableado, Bacheo) + `ScoreGenerico`
  de respaldo. Formula: pesoRiesgo*k + min(antiguedadHoras, tope) + similaresEnBarrio*m.
  Cableado k=10 tope=48 m=5; Bacheo k=5 tope=72 m=15; Generico k=5 tope=96 m=3.
- Observer: `Sujeto` (implementa Broker) y `Observador` (SvcCuadrillas, SvcIA).
- Repository: interfaces Spring Data JPA.

## Mensajeria (RabbitMQ) - decisiones cerradas
- Exchange topic `ticketera.eventos`. Colas durables, mensajes persistentes.
- `Evento`: eventId (UUID), tipo, timestamp, version, correlationId, reclamoId, barrio.
- Routing keys: reclamo.creado, reclamo.validado, reclamo.asignado, reclamo.resuelto,
  reclamo.estado_cambiado, zona.resumen.
- Todo cambio de estado valido publica un evento. EN_ANALISIS, EN_PROCESO y
  RECHAZADO usan reclamo.estado_cambiado; SvcIA invalida las variantes del barrio
  al consumirlo. La actualizacion del resumen es asincronica.
- `Broker.publicar` completa eventId/timestamp/correlationId y publica SOLO a
  RabbitMQ. NO debe llamar a los observadores en memoria (un esqueleto previo lo
  hacia y dejaba las colas sin consumidores: es un error conocido).
- `ConsumidorEventos` es la base comun del procesamiento transaccional. Cada
  consumidor tiene su propio @RabbitListener y depende solo de su Observador:
  - `ConsumidorCuadrillas`: cuadrillas.eventos, bindings reclamo.validado y reclamo.resuelto -> SvcCuadrillas
  - `ConsumidorIA`: ia.eventos, binding reclamo.# -> SvcIA (invalida la cache de esa zona y, ante
    reclamo.creado, valida el reclamo: deteccion de duplicados)
  - `zona.resumen` NO se bindea a ia.eventos (evita un ciclo de invalidacion).
- Confiabilidad: ACK tras procesar, reintentos limitados (3, con backoff), luego
  Dead Letter Exchange -> cola `ticketera.eventos.dlq`.
- Idempotencia: tabla `evento_procesado` con (eventId, consumidor) unico en Postgres; el
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
  Paso explicito `SvcIA.anonimizar` (y en DetectorDeDuplicados) con
  `util.Anonimizador`: borra de la descripcion el nombre y el contacto del
  vecino y cualquier email, DNI o telefono. Limitacion conocida: no detecta
  nombres de terceros escritos en el texto.
- Cache en memoria; Redis queda como mejora.
- Deteccion de duplicados (decidido): ante reclamo.creado, `DetectorDeDuplicados`
  busca candidatos por reglas (mismo tipo, activo, ultimos 30 dias, a menos de
  150 m o mismo barrio si no hay coordenadas) y, solo si hay, el
  `ComparadorDeReclamos` (LLM o stub) decide. Duplicado -> estado DUPLICADO +
  `reclamoOriginal`, sin cuadrilla. Si no -> score inicial + reclamo.validado, y
  recien ahi SvcCuadrillas asigna. Si el LLM falla, no es duplicado. Al LLM solo
  tipo, descripcion y distancia en metros.
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
- `docs/`: diagramas PlantUML. Clases en cinco vistas: `01` general (solo
  nombres y dependencias entre capas) y detalle en `01a` dominio, `01b`
  patrones, `01c` API/negocio/datos/IA y `01d` seguridad. Ademas: secuencias
  de alta (02), resumen (03), asignacion/resolucion (07) y autenticacion (08),
  componentes (04), despliegue (05) y flujo de mensajes (06). Para verlos:
  renderizar local (el servidor online de PlantUML rechaza diagramas grandes).
- El esqueleto previo (`referencia/`) se elimino del repositorio: todo su
  contenido fue reemplazado por la implementacion actual.
