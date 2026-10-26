# Observabilidad y comprobación M1–M2

## Correlación y trazabilidad

Cada respuesta HTTP lleva `X-Request-ID`. El cliente puede enviar un identificador de hasta 64 caracteres alfanuméricos, punto, guion o guion bajo; los demás valores se reemplazan por un UUID. Los logs de acceso registran método, ruta sin query, estado, duración y `requestId`. Los `ProblemDetail` añaden `requestId` para localizar el error en el servidor. Nunca se registran cuerpos, tokens, contraseñas ni API keys.

El ejecutor `@Async` hereda el contexto de correlación del POST que inició la verificación. Durante cada trabajo añade `runId` al MDC y registra inicio, resultado de cada expectativa, finalización, fallo y rechazo de la cola. Los eventos de creación/publicación de procesos, servicios, usuarios y decisiones humanas registran IDs, no contenido sensible. Para una incidencia, buscar primero `requestId` o `runId` en los logs.

## Reglas de evidencia

`TOOL_RECORD` con nombre **Servicio en herramienta de tracking del servicio** consulta mediante lectura el proyecto Redmine capturado en el servicio. Si Redmine confirma la identidad, propone `COMPLIES` y guarda una referencia al proyecto. Si devuelve 404, propone `DOES_NOT_COMPLY`. Si falta configuración o la consulta falla, propone `REVIEW_REQUIRED`. Otros registros de herramienta siguen requiriendo un lector propio; la existencia del proyecto no prueba cualquier otra configuración.

Un **ticket existente** se comprueba por ID y proyecto. Para **ticket actualizado**, **ticket cerrado con solución validada** y otros requisitos sobre contenido o estado, Gemini recibe el snapshot de Redmine: estado, cierre, descripción, fechas, campos personalizados e historial. Devuelve una propuesta con fundamento y campos citados. Una mera marca `updatedAt` no basta para declarar una actualización; un ticket abierto no cumple un requisito de cierre. Si Gemini no está configurado, falla o no fundamenta una conclusión, se propone `REVIEW_REQUIRED`. La decisión humana sigue registrándose de forma separada. Los textos de tickets se tratan como datos, no instrucciones.

## Nombres históricos

La migración V10 agrega al run los nombres del servicio, proceso, versión oficial y macroactividad en el momento de inicio. El frontend los muestra en progreso e historial aunque luego se renombre el servicio o se cambie la versión asignada. Para runs anteriores a V10, la migración hace un *backfill* con los nombres disponibles al migrar; si ya habían cambiado antes, el nombre original no puede reconstruirse.

## Matriz de verificación automatizada

| Recorrido | Prueba |
|---|---|
| Migraciones desde PostgreSQL vacío | `DatabaseMigrationIntegrationTest` |
| M1: borradores, publicación y servicio | `ProcessDraftIntegrationTest`, `ProcessVersioningPublicationIntegrationTest`, `ServicePersistenceIntegrationTest` |
| M1→M2: proceso publicado, servicio, Drive, Redmine, IA, worker, resultados e historial | `JdbcVerificationResultRepositoryIntegrationTest#publishedM1ConfigurationRunsWithFakeDriveRedmineAndAiAndPersistsHistory` |
| TOOL_RECORD, tickets existentes, actualizados y cerrados | `TicketAndToolEvidenceTest` |
| Contrato y fallos de Gemini | `GeminiTicketEvidenceAdapterTest`, `GeminiApplicabilityAdapterTest` |
| Historial y nombres en Flutter | `verification_history_page_test.dart`, `verification_run_snapshot_dto_test.dart` |

Las pruebas externas usan servidores locales y dobles; no verifican permisos reales en las cuentas de TEMA. Antes de una release, realizar una prueba manual en un entorno de ensayo con una carpeta Drive compartida, un proyecto Redmine accesible y la clave Gemini provista por TEMA. Confirmar que los logs y respuestas no exponen credenciales.
