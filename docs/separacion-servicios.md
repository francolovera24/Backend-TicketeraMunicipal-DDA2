# Separacion de servicios

## Avances

| Avance | Estado | Resultado |
|---|---|---|
| Consumidores independientes | Implementado | ConsumidorIA y ConsumidorCuadrillas dependen solo de su observador. ConsumidorEventos conserva transacciones, idempotencia y tratamiento de errores. |
| Aplicaciones independientes | Implementado | ReclamosApplication e IAApplication tienen componentes y ejecutables propios, con PostgreSQL compartido. |
| Despliegue separado con Docker Compose | Pendiente | Integrar los dos ejecutables como contenedores y verificar el flujo entre procesos. |

## Composicion

Los paquetes de dominio, servicios, repositorios y contratos conservan su
estructura. Los puntos de entrada estan en `com.municipio.ticketera.aplicaciones`.
La seleccion de componentes se realiza mediante `@Import` y escaneos limitados
a las fabricas o estrategias de cada modulo. Estas clases de composicion se
registran explicitamente, sin `@Component` ni un escaneo general del backend.

| Responsabilidad | Reclamos | Zonas/Resumenes e IA |
|---|---|---|
| API | `/auth`, `/ciudadanos`, `/reclamos`, `/cuadrillas`, `/ws` y WSDL | `/resumen-zona` |
| Servicios | SvcAuth, SvcCiudadanos, SvcReclamos y SvcCuadrillas | SvcIA, DetectorDeDuplicados, CacheResumenes, generador y comparador |
| Patrones | Factory, Facade, Repository y Observer | Strategy, Repository y Observer |
| Consumidor | ConsumidorCuadrillas, cola `cuadrillas.eventos` | ConsumidorIA, cola `ia.eventos` |
| Integracion externa | GeoClient/Nominatim | LlmClient/Gemini, o implementaciones stub |
| Datos | Repositorios del esquema compartido | ReclamoRepository, BarrioRepository y EventoProcesadoRepository |

`ModuloComun` registra seguridad JWT, errores HTTP, correlacion de mensajes,
OpenAPI, broker, configuracion RabbitMQ, SvcBarrios y las entidades del esquema.
Cada proceso tiene sus propias instancias de estos componentes. SvcBarrios se
conserva en ambos porque el alta de reclamos resuelve barrios y el resumen los
consulta. Las relaciones JPA permiten a IA leer los datos del ciudadano para
anonimizar las descripciones sin cargar el servicio operativo de ciudadanos.

La autenticacion y las reglas de pertenencia a un ciudadano quedan en Reclamos.
IA valida el JWT y exige ADMIN para el resumen; no expone registro ni login.
Reclamos puede arrancar sin clave de Gemini aunque IA_GENERADOR seleccione llm,
porque ese proceso no registra LlmClient.

## Datos y eventos

Ambas aplicaciones apuntan a la misma base PostgreSQL. Se conservan las
migraciones Flyway y la validacion del esquema por Hibernate. IA necesita
lectura y escritura: calcula y persiste puntajes, y marca un reclamo duplicado
con su relacion al original. Reclamos sigue administrando estados y cuadrillas.

Los mensajes conservan sus campos, version y routing keys. No transportan una
copia completa del reclamo: los consumidores recuperan los datos por su ID.
Los avisos internos son esta comunicacion por RabbitMQ; no hay un servicio
adicional de mensajes a vecinos.

1. Reclamos persiste el alta y publica `reclamo.creado` despues del commit.
2. IA detecta duplicados. Si no hay duplicado, persiste el score y publica
   `reclamo.validado`.
3. Cuadrillas asigna una cuadrilla disponible y publica `reclamo.asignado`.
4. Al resolver, Reclamos publica `reclamo.resuelto`; Cuadrillas libera el equipo
   y atiende un pendiente, mientras IA invalida las variantes del resumen.

La tabla evento_procesado mantiene la clave (event_id, consumidor). El registro
del evento y su procesamiento permanecen en la misma transaccion. Se conservan
el ACK tras procesar, los reintentos limitados y la DLQ. La publicacion conserva
la limitacion actual: sin Outbox, un fallo de RabbitMQ despues del commit puede
perder el evento y queda registrado en el log.

## Compilacion y arranque

Requisitos: Java 17, Maven 3.9, PostgreSQL 16 y RabbitMQ 3. Las pruebas usan Docker.

```bash
mvn test
mvn -Preclamos package -DskipTests
mvn -Pia package -DskipTests
```

Cada perfil Maven selecciona la clase principal y el nombre del artefacto.
No modifica los contratos ni las reglas de negocio. Las dos aplicaciones
comparten el proyecto y las dependencias; cada ejecutable registra solo su
composicion de componentes. El arranque activa el perfil Spring correspondiente,
que configura nombre de aplicacion y puerto.

Definir en el entorno de ambos procesos:

- DB_HOST, DB_PORT, DB_NAME, DB_USER y DB_PASSWORD para la misma PostgreSQL.
- RABBITMQ_HOST, RABBITMQ_PORT, RABBITMQ_USER y RABBITMQ_PASSWORD para el mismo broker.
- JWT_SECRET con el mismo valor en ambos, para validar los tokens de Reclamos en IA.

Para IA real, definir IA_GENERADOR=llm, LLM_API_KEY y, si corresponde, LLM_MODEL
y LLM_URL solo en el proceso de IA. Sin credenciales se puede usar el stub.
Nominatim se configura en el proceso de Reclamos con las variables GEO_* existentes.
Java y Maven no cargan `.env` automaticamente; Docker Compose si lo hace.

En una terminal:

```bash
java -jar target/ticketera-reclamos.jar
```

En otra terminal:

```bash
java -jar target/ticketera-ia.jar
```

| Aplicacion | Puerto HTTP por defecto | Nombre |
|---|---|---|
| Reclamos | 8080 | ticketera-reclamos |
| IA | 8081 | ticketera-ia |

SERVER_PORT permite cambiar el puerto de cada proceso. Swagger/OpenAPI queda
disponible en cada aplicacion y publica sus endpoints. El token se obtiene en
Reclamos y se envia como Authorization: Bearer al consultar el resumen de IA.

El modo integrado sigue usando `mvn package` y `ticketera-backend.jar`.
El Dockerfile y Docker Compose actuales levantan ese modo. No ejecutar el modo
integrado y las aplicaciones separadas contra las mismas colas a la vez: sus
consumidores competirian por los mensajes destinados a cada modulo.

## Verificacion

`AplicacionesSeparadasIntegracionTest` levanta dos servidores HTTP en contextos
Spring diferentes, con PostgreSQL y RabbitMQ reales y puertos aleatorios. Usa
el stub de IA y Nominatim deshabilitado, sin claves ni llamadas externas.

Comprueba componentes y consumidores exclusivos, separacion de endpoints,
SOAP en Reclamos, JWT compartido y permisos 401/403/200. El flujo funcional
verifica score persistido por IA, duplicado sin cuadrilla, asignacion automatica,
liberacion y reasignacion de un equipo e invalidacion del resumen filtrado.
Las pruebas del modo integrado siguen verificando los flujos existentes.

Los diagramas 04 y 05 describen el modo integrado. El 09 muestra los limites de
las aplicaciones separadas; el despliegue en contenedores se documentara al
incorporarlo a Docker Compose.
