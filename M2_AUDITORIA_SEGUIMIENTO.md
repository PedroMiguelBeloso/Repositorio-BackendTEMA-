# Correcciones de auditoría M1–M2

Esta rama agrega dos consultas para revisar una verificación sin interpretar un resultado pendiente como evidencia ausente.

`GET /api/v1/verifications/{runId}/work-items` (ADMIN o LECTOR) devuelve una fila por expectativa y ticket seleccionado, en el orden de la versión publicada. `searchStatus` es `NOT_ATTEMPTED` hasta que comienza su evaluación, `IN_PROGRESS` mientras el trabajador de esta instancia la procesa, y después `FOUND`, `NOT_FOUND`, `INCOMPLETE` o `UNKNOWN` según el resultado. `resultStatus` es nulo antes de que exista un resultado. El estado activo es transitorio en memoria; si el proceso se reinicia, la ejecución interrumpida pasa a `FAILED` y las evidencias sin resultado vuelven a verse como no intentadas.

`GET /api/v1/verifications/{runId}/results/{resultId}/ticket-observation` (solo ADMIN) devuelve `observedAt` y `ticket` con la copia de campos e historial leídos inicialmente de Redmine. Responde 404 para resultados sin copia, incluidos los anteriores a la migración V12. El snapshot es de solo lectura y no se expone en la lista de resultados ni a LECTOR.

La migración V12 depende de V11, que define la función de protección de las copias de auditoría. Ambas deben incluirse al integrar esta rama.

La publicación de una versión exige al menos una política y una expectativa de evidencia, además de las validaciones previas. Esto evita publicar una estructura vacía; no certifica que se transcribió íntegramente cada PDF. El Administrador debe comparar las políticas, actividades y registros cargados con el procedimiento correspondiente.

Los cuatro procedimientos recibidos incluyen políticas: Start Up (3), Take Over (1), Soporte y Software Factory (7) y Calidad y Mejora Continua (1). El validador exige presencia, sin codificar esos conteos como regla de negocio.

En el Índice de registros, `canonicalName` es el nombre reutilizable y `officialNomenclature` es el nombre o ruta exacta que se buscará en Drive. `displayName` permite llamar a ese registro de otra manera en cada proceso, sin cambiar la búsqueda. Para DOCUMENT y FOLDER el Administrador debe cargar una nomenclatura real o una convención acordada; no se deduce del nombre visible. Los nombres faltantes pueden agregarse al Índice o precargarse con una migración revisada.

Las expectativas de ticket consultan exclusivamente Redmine. «Servicio en herramienta de tracking» comprueba que el proyecto Redmine vinculado exista y sea accesible; esa comprobación no acredita por sí sola la gestión de tickets. Los demás registros de herramienta y las imputaciones sin lector permanecen en `REVIEW_REQUIRED` para decisión humana. No se marca conformidad ficticia.

Para modelar los procedimientos recibidos se usa la versión y fecha de la portada y el nombre/instrucciones del detalle de cada actividad cuando el propio PDF discrepa. Se registra la elección en la carga; no se bloquea esperando una aclaración de TEMA. Calidad y Mejora Continua sigue siendo consultable en M1, pero no verificable en M2 según el alcance actual.

`POST /api/v1/registry-definitions` responde 201 Created. El texto móvil de la navegación cambia a «Verificar» y conserva «Verificaciones» como ayuda accesible.

Pendiente para aceptación integral: transcribir y revisar el contenido completo de los cuatro PDF, probar Drive y Gemini con casos reales controlados, y validar nomenclaturas por servicio. Estas pruebas no se sustituyen por fixtures.
