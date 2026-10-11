# Repositorio-BackendTEMA-

## Servicios M1

El módulo `service` expone `GET/POST /api/v1/services`, `GET/PUT /api/v1/services/{serviceId}`
y `GET /api/v1/services/{serviceId}/verifiable-processes`. Usa las tablas existentes
`services` y `service_process_versions`; no se requiere una migración nueva.
`GET /api/v1/services?page=0&size=20` requiere una sesión autenticada y devuelve
`items` y `page`. Solo el Administrador puede crear o editar. `PUT` requiere la
`revision` actual y responde `409` si hubo otra edición.

Un servicio puede crearse sin fuentes mientras se configuran las integraciones.
Cuando el request incluye `driveFolderId` o `redmineProjectId`, el backend exige
un `ServiceSourceResolver` que lea la fuente con la identidad técnica y devuelva
su nombre; sin acceso responde `503` y no persiste el servicio. Los adaptadores
de Drive y Redmine implementan esta lectura y validan el proyecto o carpeta real
antes de guardar el servicio. El endpoint `GET /api/v1/services/{serviceId}/tickets`
usa `ServiceTicketSearch` con el proyecto asociado al servicio y responde `503`
si no existe un proyecto Redmine validado o si el conector no está disponible.
No se generan nombres ni tickets ficticios en el perfil de ejecución normal.

## Disponibilidad general de fuentes M1

`GET /api/v1/integrations/status` devuelve el último estado conocido de Google
Drive y Redmine. `POST /api/v1/integrations/test` solicita una prueba nueva de
lectura para cada fuente. Ambos endpoints requieren un JWT de Administrador y
devuelven `source`, `status`, `checkedAt` y un `message` sin datos sensibles.
El estado general no comprueba el acceso a una carpeta, proyecto o ticket de
un servicio particular.

La variable opcional `TEMA_INTEGRATION_PROBE_TIMEOUT` controla el tiempo máximo
por fuente en formato ISO-8601 (`PT3S` por defecto; máximo `PT30S`). No se
necesitan variables de Drive ni Redmine para arrancar el backend. Configurar
credenciales por sí solo nunca produce `AVAILABLE`: ese estado exige una lectura
exitosa. `NOT_CONFIGURED` sigue siendo el estado inicial para una fuente sin
configuración.

## Resumen de corrección y validación

Se corrigió la lectura individual de tickets de Redmine y la validación del historial para que el adaptador cumpla con el contrato de M2:

- Se incluye explícitamente `include=journals` al consultar el ticket.
- Si Redmine responde `404`, el backend lo transforma en `ApiException` de dominio y no en un error técnico escapado.
- El snapshot del ticket ahora conserva correctamente `statusId`, `statusName`, `closed`, `customFields` y `journals`.
- Los cambios del historial (`details`) se parsean y quedan accesibles como `JournalChange`.
- El estado cerrado solo se marca cuando Redmine lo señala explícitamente; si no hay esa señal, se devuelve como no determinado en lugar de inferirse por el nombre.

La validación quedó cubierta por tests de Redmine y la suite completa del backend ejecuta correctamente con `./mvnw test` sin fallos reportados en Surefire.

## Conector Redmine de solo lectura (TC-6.2.1)

El backend incluye un adaptador de lectura de Redmine para proyectos y tickets
que usa `GET` sobre la API REST JSON de Redmine, envía la clave en la cabecera
`X-Redmine-API-Key` y nunca la incluye en la URL, respuestas ni logs. El acceso
es solo de lectura: no crea proyectos ni tickets ni descarga adjuntos.

Para habilitarlo, configurar:

- `TEMA_REDMINE_ENABLED=true`
- `TEMA_REDMINE_API_BASE_URL=http://<host>:<port>/redmine`
- `TEMA_REDMINE_API_KEY=<clave del usuario del servicio>
- `TEMA_REDMINE_TIMEOUT=PT5S` (o un valor ISO-8601 válido entre 1 ms y 30 s)

`POST /api/v1/integrations/test` ejecuta una prueba real de lectura; un
resultado `AVAILABLE` exige que Redmine responda correctamente. Si la clave falla,
Redmine responde `401`; si se carece de permisos, `403`; si no es alcanzable o
falla la lectura, el estado queda en `UNAVAILABLE`.

El selector de proyectos expone `GET /api/v1/integrations/redmine/projects` para
Administradores, con paginación y la posibilidad de filtrar por nombre o
identifier. Al guardar o editar un servicio, se valida que el proyecto exista,
sea accesible y que el backend pueda devolver su nombre para persistir el `id`
real de Redmine junto con el nombre visible.

Antes de ejecutar una verificación de M2, `GET /api/v1/services/{serviceId}/tickets`
usa exclusivamente el proyecto Redmine guardado en el servicio, solicita
`status_id=*` para incluir tickets abiertos y cerrados y restringe los
resultados al proyecto asociado. La búsqueda por ID o asunto se hace dentro del
proyecto del servicio y la paginación se conserva con `totalElements`, `page` y
`size`.

La lectura individual de un ticket comprueba que `project.id` coincide con el
proyecto del servicio antes de devolver los metadatos, historial y campos
personalizados de Redmine. Eso permite a M2 analizar la evolución del ticket sin
mezclar tickets de otros proyectos.

En la consulta de detalle se incluye explícitamente `include=journals`; si la
respuesta de Redmine omite ese campo, la lectura del historial se considera
incompleta y no se interpreta como un historial vacío. El estado cerrado se
respetará solo cuando Redmine lo informe explícitamente; si no existe esa señal,
se devolverá como `desconocido` y nunca se deducirá por el nombre del estado.

Para M2, este snapshot debe usarse como evidencia de lectura, no como prueba de
cumplimiento por sí mismo: el backend recupera y organiza la información del
ticket y su historial, pero la decisión de si un cambio cumple una expectativa del
proceso se realizará en la capa de verificación. Antes de devolver el snapshot,
se valida que el ticket pertenezca al proyecto de Redmine asociado al servicio y
que cada consulta de detalle se refiera a un ticket concreto, sin mezclar datos
entre tickets o proyectos.

## Conector Google Drive de solo lectura (TC-163)

El backend arranca con `TEMA_DRIVE_ENABLED=false` por defecto. Para probar Drive,
habilitar la API de Google Drive en el proyecto de prueba, dar acceso de lectura
a una carpeta de prueba a la identidad técnica y configurar las credenciales
mediante Application Default Credentials. En desarrollo local se puede indicar
la ruta de un archivo de credenciales **fuera del repositorio** con
`GOOGLE_APPLICATION_CREDENTIALS`; no copiar el JSON a `src/main/resources` ni
registrarlo en Git. La identidad necesita el alcance `drive.metadata.readonly`.

Con `TEMA_DRIVE_ENABLED=true`, `POST /api/v1/integrations/test` ejecuta una
lectura real; `GET /api/v1/integrations/drive/folders?query=&page=0&size=20`
lista carpetas para el Administrador. Al guardar `driveFolderId` en un servicio,
el backend comprueba el ID y toma el nombre desde Drive. Sin credenciales o
sin acceso, el selector y el guardado informan un error seguro y no simulan
disponibilidad. `TEMA_DRIVE_TIMEOUT` usa una duración ISO-8601 (`PT5S` por
defecto). `TEMA_DRIVE_API_BASE_URL` solo se cambia en pruebas con un servidor
HTTP simulado. Las credenciales no se muestran en respuestas ni en logs.

El contrato actual exige totales de paginación. Drive solo entrega
`nextPageToken`, así que el selector recorre las páginas accesibles y calcula
los totales antes de paginar. Está pensado para un Drive de prueba acotado;
si se supera el límite de 100 páginas, la consulta falla sin entregar una
lista parcial como si fuese completa.

## Búsqueda de evidencias de Drive (TC-164)

`DriveEvidenceSearch` es una operación interna para M2; no agrega un endpoint
público. Recibe el ID de la carpeta configurada para el servicio, el tipo
`DOCUMENT` o `FOLDER`, la nomenclatura oficial y la cardinalidad `ONE` o
`MULTIPLE`. Solo lee metadatos dentro de esa carpeta y sus subcarpetas. Un
nombre se compara exactamente; una ruta se compara desde la carpeta del
servicio. No abre archivos ni sigue accesos directos.

El resultado distingue `FOUND`, `ABSENT`, `AMBIGUOUS` y `NOT_CHECKABLE`.
`ABSENT` se devuelve solo tras completar todas las páginas y subcarpetas.
Errores de autenticación, permisos, timeout, respuestas incompletas y límites
de recorrido producen `NOT_CHECKABLE`; no equivalen a incumplimiento. La
operación limita el recorrido a 100 páginas, 1000 carpetas, 100000 elementos
y 30 segundos. M2 decidirá después la aplicabilidad y traducirá el resultado
a un estado de verificación. No se requiere migración ni credenciales nuevas:
se reutiliza la configuración de Drive descrita arriba.

```mermaid
classDiagram
    class DriveEvidenceSearch {
      <<interface>>
      +search(SearchRequest) SearchResult
    }
    class GoogleDriveAdapter
    class SearchRequest {
      +rootFolderId
      +kind
      +officialNomenclature
      +cardinality
    }
    class SearchResult {
      +outcome
      +problem
      +matches
    }
    DriveEvidenceSearch <|.. GoogleDriveAdapter
    DriveEvidenceSearch --> SearchRequest
    DriveEvidenceSearch --> SearchResult
```

El futuro verificador convierte los tipos y cardinalidad de la expectativa
publicada de M1 al `SearchRequest`, y traduce el `SearchResult` a su propio
resultado. `integration` no depende del módulo `verification`.

## Regla de diseño de dominio — M2 preview

La implementación de `POST /api/v1/verifications/preview` protege las invariantes locales en `verification.domain`, mantiene las reglas que requieren múltiples objetos en servicios de dominio/aplicación y deja la traducción HTTP en `verification.api`. El detalle de ubicación y justificación está en `docs/7.1.1-domain-rule-placement.md`.


## 7.1.1 — Verification preview

Implemented `POST /api/v1/verifications/preview` as a read-only M2 use case. Domain invariants live in `verification.domain` and `process.domain`; cross-object validation is coordinated by `VerificationScopeResolver`; HTTP validation/error mapping stays in `verification.api`. The preview does not create runs or call external sources. See `docs/7.1.1-domain-rule-placement.md` and `docs/7.1.1-alignment-report.md`.

## 7.1.3 — Selección de tickets y cardinalidad

`POST /api/v1/verifications` vuelve a resolver el alcance y los grupos publicados, comprueba que `serviceRevision` siga vigente y valida la selección recibida sin inferir tickets por nombre ni reemplazar IDs elegidos. `ONE` requiere exactamente un ticket; `MULTIPLE`, uno o más. Cada ID se consulta mediante `RedmineTicketReader` y debe pertenecer al proyecto Redmine configurado para el servicio. Si falla la validación, no se crea la ejecución.

El run y sus filas de `verification_ticket_selections` se guardan en una transacción, conservando `run_id`, `group_id` y `ticket_id`. Las expectativas TICKET de una macroactividad comparten su grupo; los grupos de macroactividades distintas se validan por separado. Ver [docs/7.1.3-ticket-selection.md](docs/7.1.3-ticket-selection.md) para el flujo y las reglas completas.

## Integración OpenAI para aplicabilidad de M2 (TC-178)

El backend usa el SDK oficial `openai-java` y la Responses API con salida
estructurada. La IA solo propone la aplicabilidad de evidencias marcadas
`CASE_DEPENDENT` cuando los datos estructurados no permiten resolverla. No
consulta Drive/Redmine ni decide si una evidencia cumple. El puerto interno
`ApplicabilityAiPort` queda disponible para el evaluador de TC-179; esta tarea
no agrega un endpoint público ni ejecuta automáticamente la IA en una revisión.

Para habilitar llamadas reales, configurar fuera del repositorio:

```powershell
$env:TEMA_OPENAI_ENABLED='true'
$env:OPENAI_API_KEY='<clave-de-proyecto>'
$env:TEMA_OPENAI_MODEL='<modelo-compatible-con-structured-outputs>'
$env:TEMA_OPENAI_TIMEOUT='PT20S'
```

Sin clave, modelo o habilitación, el backend sigue iniciando y el puerto devuelve
`UNDETERMINED`, con un límite técnico para revisión humana. El timeout permitido
es mayor que cero y hasta 60 segundos; el SDK realiza como máximo un reintento
de transporte y hay un segundo intento solo para una respuesta estructurada
inválida. Las llamadas usan `store=false`; no se registran credenciales, prompts
ni datos de tickets en logs. La entrada se limita a 20.000 caracteres.

La ubicación de clases, el contrato interno y las responsabilidades de TC-179
están en `docs/7.2.1-openai-integration.md`. Las pruebas de esta tarea usan un
cliente simulado y no consumen la API real.

## M2 — 7.1.2 y TC-179

El estado de la capa M2 queda separado en dos piezas:

- `7.1.2` prepara el contexto de verificación con `VerificationContextLoader`, `VerificationContext`, `VerificationPreviewController` y la validación del proceso publicado, su asignación y la consistencia de los catálogos.
- `TC-179` resuelve la aplicabilidad con `DefaultApplicabilityPolicy`, `EvaluationContext`, `ApplicabilityAssessment`, `ApplicabilityAiPort` y la integración `OpenAiApplicabilityAdapter` como fallback de `CASE_DEPENDENT`.

Documentación técnica del módulo:
- [src/main/java/com/temaconsulting/agente_iso/verification/README.md](src/main/java/com/temaconsulting/agente_iso/verification/README.md)

Guía rápida de pruebas:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location 'C:\Users\User\OneDrive\Documentos\Repositorio-BackendTEMA-'
.\mvnw -q -DskipTests compile
.\mvnw -q -Dtest=VerificationContextLoaderTest test
.\mvnw -q -Dtest="OpenAiApplicabilityAdapterTest,DriveDocumentaryAssessmentServiceTest" test
```

Estado actual:
- Completado: contexto de verificación y política de aplicabilidad M2.
- Pendiente: integración final de la decisión de aplicabilidad con la pipeline completa del resultado de verificación y la persistencia/human review final.
- La suite completa del repositorio no se pudo verificar en esta máquina porque Docker/Testcontainers no está disponible (`docker --version` no existe en el entorno actual).
