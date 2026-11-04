# Evidencias: alcance personal y colectivo

Verificacion del 4 de noviembre de 2026.

## Base y alcance del cambio

- Backend basado en `16718a6`; frontend basado en `08db229` de `main`.
- Los cambios locales anteriores se respaldaron antes de restablecer ambos repositorios.
- Alcance obligatorio en el alta, exposicion en los detalles y adicional de 10 puntos para colectivos.
- `SvcIA`, `SvcCuadrillas` y `ReclamoRepository` conservan el contenido de `main`.
- Las migraciones oficiales V1 a V8, las fotos, los eventos, SOAP y los clientes de IA conservan sus contratos.

## Pruebas automaticas

| Verificacion | Resultado |
|---|---|
| Backend antes del cambio: `mvn.cmd test` | 206 pruebas, 0 fallos, 0 errores, 0 omitidas |
| Backend final: `mvn.cmd test` | 224 pruebas, 0 fallos, 0 errores, 0 omitidas |
| Frontend base y final: `npm.cmd run build` | Correcto |
| Frontend base y final: `npm.cmd run lint` | Sin errores; mismas 11 advertencias preexistentes |
| PlantUML de los cinco diagramas modificados | SVG generado sin errores; dominio inspeccionado tambien en PNG |
| Revision de diferencias y `git diff --check` | Solo alcance, adicional, adaptaciones de pruebas y documentacion; sin errores de espacios |

Las 18 pruebas nuevas cubren:

- Los cinco tipos de problema: colectivo suma exactamente 10; personal e historico conservan los valores anteriores, incluso con antiguedad limitada por el tope y otros reclamos del barrio.
- La fabrica conserva el alcance y rechaza su ausencia.
- Altas personales y colectivas, persistencia, consulta, listado, historial y ranking; foto opcional recuperable con el contenido original.
- HTTP 400 ante alcance ausente, null, vacio, desconocido o numerico, sin crear reclamos.
- Historicos con null y contrato Swagger: obligatoriedad en solicitud, valores permitidos y respuesta nullable.
- Regresion del flujo de resolucion y reasignacion por especialidad.
- V8 a V9 en PostgreSQL descartable, con y sin columna preexistente: conservacion de filas, fotos, relaciones y estados; restriccion de valores y segunda ejecucion sin migraciones pendientes.

## Migracion de la base local

Se guardo un respaldo completo en `target/respaldos/` y se restauro en una base
de verificacion antes de actualizar la base de uso.

La base local tenia `V8__reclamo_alcance.sql`. Se verificaron su nombre,
version, checksum y estado antes de retirar exclusivamente ese registro del
historial. Las filas y la columna existentes se conservaron. Flyway aplico la
V8 oficial de fotos y luego la V9 de alcance, primero sobre la copia y despues
sobre la base local.

Los conteos y hashes de reclamos, ciudadanos, usuarios, cuadrillas y barrios
coincidieron antes y despues en la copia. Los cuatro reclamos preexistentes
tambien conservaron su contenido completo al finalizar la verificacion local
(excluyendo del hash la nueva columna de fotos, con valor inicial false).
No se eliminaron volumenes.

## Verificacion en navegador

Se utilizo una cuenta sintetica de vecino y otra municipal en un origen local
separado de la sesion del navegador de uso.

| Escenario | Resultado observado |
|---|---|
| Formulario inicialmente vacio | Selector en Selecciona una opcion |
| Envio sin alcance | Validacion del navegador; no se creo ciudadano ni reclamo |
| Error de titulo con PERSONAL elegido | Error visible, eleccion y demas campos conservados |
| Alta personal sin foto | Alcance Personal, score 30 para Alumbrado |
| Alta colectiva con foto sintetica | Alcance Colectivo, score 35 para Bacheo (25 + 10), imagen visible de 400 x 240 |
| Alta exitosa | Selector nuevamente vacio |
| Recarga y seleccion desde historial | Ambos alcances conservados |
| Consulta de historico | Sin especificar en vecino y municipio |
| Detalles municipales | Personal y Colectivo visibles, puntajes y foto conservados |
| Ranking de Belgrano | Puntajes 148, 35 y 30, ordenados de mayor a menor |
| Cambio de estado | Colectivo: ASIGNADO -> EN_PROCESO -> RESUELTO; personal: ASIGNADO -> RESUELTO |
| Equipos de las pruebas | Disponibles nuevamente despues de los cierres |
| Procesos al finalizar | Web en 5173 y API en 8080, ambas responden HTTP 200 |

Los dos reclamos sinteticos creados para la verificacion quedaron RESUELTO,
para liberar sus equipos. Los reclamos anteriores conservaron sus estados.

Capturas anteriores al cierre de los reclamos de prueba:

- [Alta personal](alcance/alta-personal.png).
- [Alta colectiva con foto](alcance/alta-colectiva.png).
- [Detalle municipal](alcance/detalle-municipal.png).
- [Ranking](alcance/ranking.png).

## Limitacion comprobada

El servidor local de Gemma no esta disponible en el puerto 8090. El resumen de
zona muestra el aviso de ausencia de texto y conserva el ranking calculado.
La generacion real y el analisis multimodal por Gemma no se pudieron validar
en esta maquina. Las pruebas automaticas utilizan el comparador stub y dobles
del cliente de IA, como la suite de `main`.
