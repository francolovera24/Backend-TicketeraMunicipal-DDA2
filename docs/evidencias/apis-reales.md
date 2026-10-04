# Pruebas de APIs externas

Fecha: 4 de octubre de 2026.

Aplicacion Spring Boot ejecutada por HTTP con PostgreSQL 16 y RabbitMQ 3
reales mediante Testcontainers. Los datos de los ciudadanos y reclamos son
sinteticos. Las credenciales no forman parte de las evidencias.

## Nominatim

Se registro un reclamo con direccion `Avenida Cabildo 2040`, sin barrio,
latitud ni longitud. El alta devolvio HTTP 201 y completo:

| Campo | Resultado |
| --- | --- |
| Barrio | Belgrano |
| Latitud | -34.5627267 |
| Longitud | -58.4564287 |

La consulta posterior del reclamo devolvio el mismo barrio y las mismas
coordenadas. La prueba `NominatimRealIT` paso sin modificar `GeoClient`.

## Gemini

El modelo predeterminado anterior, `gemini-2.5-flash`, devolvio HTTP 404:
Google informo que no esta disponible para usuarios nuevos. La aplicacion
respondio con el resumen de respaldo y `generadoPorIa=false`.

Se verifico `gemini-3.8-flash` con el cliente existente y se actualizaron los
valores predeterminados de la aplicacion y Docker Compose, junto con los
ejemplos de configuracion. `LLM_MODEL` permite elegir otro modelo compatible.
La API `generateContent` y el nivel de razonamiento `low` estan documentados
por [Google](https://ai.google.dev/gemini-api/docs/generate-content/latest-model).

Con el modelo actualizado, el resumen devolvio HTTP 200,
`generadoPorIa=true` y este ranking:

| Orden | Reclamo | Score |
| --- | --- | --- |
| 1 | Cable pelado sobre la vereda con riesgo electrico | 100 |
| 2 | Pozo profundo en la calzada | 25 |

Texto observado:

> En el barrio se registran reclamos por problemas de cableado y bacheo. Se debe atender en primer lugar el cable pelado sobre la vereda, ya que implica un riesgo electrico directo y urgente para la integridad de los peatones. En segundo lugar, se debe intervenir sobre el pozo profundo en la calzada para prevenir accidentes viales y danos en los vehiculos.

La revision del texto confirma que describe ambos problemas y respeta su
prioridad. No constituye una evaluacion exhaustiva de exactitud del modelo.

Tambien se registraron dos reportes del mismo cable, con descripciones
distintas y las mismas coordenadas. Gemini selecciono al candidato original:
el segundo quedo en `DUPLICADO`, vinculado al primer reclamo y sin cuadrilla.

## Reproduccion y resultados

```bash
mvn test
mvn test -Dtest=NominatimRealIT,GeminiRealIT
```

La suite habitual no consulta estas APIs. Las pruebas externas se seleccionan
explicitamente y requieren Internet, Docker y `LLM_API_KEY` en el entorno.
Los detalles de configuracion estan en el README. Cada prueba exitosa guarda
los datos de entrada y la respuesta en `target/evidencias/`, sin headers ni
claves. Los JSON son locales y no se incluyen en Git.

La suite habitual completo 200 pruebas sin fallos, errores ni omisiones.
Las tres pruebas externas tambien pasaron sin fallos ni omisiones. La disponibilidad
de las APIs, los modelos, las cuotas y las respuestas puede variar entre
ejecuciones.

En una repeticion de la prueba de duplicados, Gemini respondio HTTP 503 por
alta demanda, tambien en el reintento. El flujo existente conservo el reclamo
como no duplicado y continuo su validacion y asignacion. La prueba externa
fallo porque no obtuvo la clasificacion esperada; no se considero esa
respuesta de respaldo como una validacion exitosa del modelo.
Un ultimo intento de la misma prueba paso y vinculo correctamente el duplicado.
