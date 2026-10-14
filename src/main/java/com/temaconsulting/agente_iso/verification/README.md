# Módulo de verificación (M2)

## Alcance y estado

Esta parte del backend concentra la preparación del contexto de verificación y la decisión de si una expectativa publicada es aplicable al caso concreto antes de evaluar cumplimiento documental o de tickets.

### 7.1.2 — `VerificationContextLoader`

Objetivo: construir un `VerificationContext` válido a partir del servicio, la versión publicada del proceso, las macroactividades y los requisitos de evidencia catalogados.

Qué se hizo:
- Se validó la asociación del servicio con la versión del proceso.
- Se comprobó que el proceso esté publicado y habilitado para M2.
- Se cargaron las macroactividades, expectativas y definiciones del catálogo.
- Se preservaron `ApplicabilityMode`, `Cardinality`, `EvidenceLocation`, `EvidenceType` y el grupo de selección de tickets asociado.
- Se mantuvo el comportamiento seguro para fuentes faltantes: `driveFolder` o `redmineProject` pueden quedar en `SourceStatus.MISSING`, pero no se convierten en datos inventados.

Qué queda fuera:
- No se crea una ejecución de verificación ni se decide cumplimiento.
- No se consulta Drive ni Redmine desde este loader; solo se leen snapshots y definiciones ya publicadas.
- No se resuelve la aplicabilidad de cada expectativa aquí; ese paso pertenece a la política de TC-179.

### TC-179 — resolución de aplicabilidad

Objetivo: decidir si exige o no la evidencia para un caso concreto sin mezclar la pregunta de aplicabilidad con la pregunta de cumplimiento.

Qué se hizo:
- Se definió la política de resolución en `DefaultApplicabilityPolicy`.
- Se introdujo `ApplicabilityAssessment` como contrato interno de salida con `decision`, `origin`, `rationale` y `limitations`.
- Se introdujo `EvaluationContext` como entrada estructurada para la política.
- Se usó `ApplicabilityAiPort` como fallback solo para `CASE_DEPENDENT`.
- Se validó que la IA no se invoque cuando la expectativa no es case-dependent.

Qué queda fuera:
- No hay un endpoint público nuevo para aplicar IA.
- Este módulo no reemplaza la decisión humana ni la persistencia de resultados finales de verificación.
- La evaluación documental (`DriveDocumentaryAssessmentService`) usa un `ApplicabilityDecision` ya resuelto; no infiere la aplicabilidad a partir de nombres de archivo.

## Cómo funciona

### 7.1.2 — flujo del `VerificationContextLoader`

`VerificationContextLoader.load(VerificationTarget)` sigue este orden:

1. `ServiceSnapshotReader.find(serviceId)` para recuperar el servicio y sus fuentes.
2. `PublishedProcessReader.getPublished(processVersionId)` para leer la versión publicada.
3. `ProcessDefinitionReader.find(version.processId())` para validar que el proceso exista.
4. Validaciones funcionales:
   - servicio existente;
   - proceso publicado;
   - proceso asignado al servicio;
   - proceso habilitado para M2;
   - macroactividad perteneciente a la versión.
5. Selección de macroactividades según `VerificationScope.PROCESS` o `VerificationScope.MACRO_ACTIVITY`.
6. Carga de `RegistryDefinition` para cada expectativa.
7. Validaciones del catálogo:
   - la expectativa y la definición tienen el mismo `EvidenceType`;
   - la nomenclatura oficial coincide con la publicada;
   - si la definición exige nomenclatura y no la tiene, se rechaza la carga.
8. Planificación de `TicketSelectionGroup` y vinculación por `expectationId`.
9. Construcción de `VerificationContext` con:
   - `service`
   - `process`
   - `scope`
   - `macroActivityId`
   - `macroActivities`
   - `expectations`
   - `ticketSelectionGroups`

Reglas clave del dominio:
- `VerificationContext.ServiceContext` exige `id`, `type`, `revision`, `driveFolder` y `redmineProject`.
- `Source` permite `SourceStatus.CONFIGURED` o `SourceStatus.MISSING`, pero no combina `MISSING` con `id`/`name`.
- `VerificationScope.PROCESS` no acepta un `macroActivityId`.
- `VerificationScope.MACRO_ACTIVITY` exige un `macroActivityId` válido.
- Se mantienen los grupos de tickets por macroactividad y sus expectativas relacionadas.

Errores funcionales reales en `VerificationErrors`:
- `SERVICE_NOT_FOUND`
- `PROCESS_VERSION_NOT_FOUND`
- `PROCESS_VERSION_NOT_PUBLISHED`
- `PROCESS_VERSION_NOT_ASSIGNED`
- `PROCESS_NOT_M2_ENABLED`
- `MACRO_ACTIVITY_NOT_IN_VERSION`
- `CATALOG_DEFINITION_NOT_FOUND`
- `PUBLISHED_CONFIGURATION_INCONSISTENT`
- `TICKET_SELECTION_INVALID`

### TC-179 — resolución de aplicabilidad

La política es conservadora y separada de la comprobación de cumplimiento:

- `ALWAYS` => `APPLIES`, resultado determinista sin IA.
- `CASE_DEPENDENT` => se intenta resolver primero con hechos estructurados del caso.
- Si no hay coincidencia clara, se usa `ApplicabilityAiPort` como fallback.
- Si la IA no puede resolverlo o la información es insuficiente, se devuelve `UNDETERMINED`.

La implementación real en `DefaultApplicabilityPolicy.assess(EvaluationContext)`:

1. Si `context.applicability() == ALWAYS`, devuelve `ApplicabilityAssessment.deterministic(APPLIES, ...)`.
2. Si no es `CASE_DEPENDENT`, devuelve `UNDETERMINED` con `DecisionOrigin.NOT_EVALUATED`.
3. Ejecuta `evaluateStructuredFacts(context)`.
4. Normaliza labels y valores (`Normalizer`, stripping diacritics y lowercase) y busca tokens positivos y negativos.
5. Regla estructurada explícita:
   - positivos: `desarrollo`, `configuración`, `corrección`, `implementación`, `ajuste`, `cambio`, etc.
   - negativos: `no hubo`, `sin desarrollo`, `no aplica`, `no requiere`, `no se requiere`, etc.
   - si hay positivo y no negativo => `APPLIES`.
   - si hay negativo y no positivo => `NOT_APPLICABLE`.
   - si hay ambos o no hay ninguno => `UNDETERMINED` / fallback o revisión.
6. Si la regla estructurada no resuelve, y existe un `ApplicabilityAiPort`, arma un `AiApplicabilityRequest` con los hechos autorizados del caso.
7. `OpenAiApplicabilityAdapter` valida que la respuesta:
   - pertenezca a la misma `expectationId`;
   - use solo referencias del caso recibido;
   - no invente `evidenceRefs`;
   - no devuelva respuestas estructuradas inválidas; y
   - requiera `evidenceRefs` para `APPLIES` y `NOT_APPLICABLE`.
8. Si la IA falla o responde inválida, la decisión queda `UNDETERMINED`.

Qué nunca se envía a la IA:
- credenciales;
- contenido de archivos de Drive;
- adjuntos de Redmine;
- datos de tickets ajenos;
- evaluaciones de cumplimiento;
- instrucciones para “inventar” hechos no incluidos.

Qué sí se envía a la IA:
- `expectationId`
- `evidenceName`
- `serviceType`
- políticas aplicables
- instrucciones de análisis
- `facts`: referencia + label + value de los datos autorizados

La trazabilidad se guarda en `ApplicabilityAssessment`:
- `decision`: `APPLIES`, `NOT_APPLICABLE` o `UNDETERMINED`
- `origin`: `DETERMINISTIC`, `AI_APPLICABILITY`, `NOT_EVALUATED`
- `rationale`: fundamento breve
- `limitations`: limitaciones técnicas o de alcance

Esto se serializa luego en el DTO `ApplicabilityAssessmentResponse` sin perder la semántica del dominio.

### 7.1.4 — comprobación programada de evidencias

`ProgrammedEvidenceCheckService.assess(...)` recibe una expectativa, el snapshot de fuentes del servicio, la aplicabilidad ya resuelta y, para `TICKET`, el ID del ticket seleccionado para ese resultado.

- `NOT_APPLICABLE` devuelve `NOT_APPLICABLE` y `UNDETERMINED` devuelve `REVIEW_REQUIRED` antes de consultar cualquier fuente. Resolver aplicabilidad sigue siendo responsabilidad del paso anterior.
- `DOCUMENT` y `FOLDER` usan `DriveEvidenceSearch` a través de `DriveDocumentaryAssessmentService`; la búsqueda recibe la carpeta del servicio, el tipo, la nomenclatura oficial del catálogo y la cardinalidad de M1.
- Una búsqueda completa sin coincidencias solo produce `DOES_NOT_COMPLY`. Error de permisos, fuente faltante/inaccesible, búsqueda incompleta o datos inconsistentes producen `REVIEW_REQUIRED`.
- `COMPLIES` documental acredita únicamente existencia y nomenclatura; no acredita contenido, completitud ni aprobación.
- Cada evaluación `TICKET` consulta solo `RedmineTicketReader.getTicket(projectId, selectedTicketId)`, conserva `expectationId` y `subjectTicketId`, y adjunta el snapshot consultado como referencia. La existencia, `updatedAt` o `closed=true` no prueban por sí solos una condición de actividad; sin un control identificado, el estado queda `REVIEW_REQUIRED`.
- `IMPUTATIONS` y `TOOL_RECORD` quedan `REVIEW_REQUIRED` mientras no exista un lector y una regla verificable.
- El servicio devuelve propuestas internas y no llama a OpenAI. TC-176 persiste por separado esas valoraciones mediante el contrato `VerificationResultPort`; la orquestación y el estado final del run quedan fuera de este servicio.

### TC-176 — persistencia y consulta de resultados

- `VerificationResultPort` es el contrato interno para que el futuro trabajador guarde una valoración evaluada y consulte el progreso persistido.
- `JdbcVerificationResultRepository` valida la expectativa contra la versión y el alcance del run, valida los tickets seleccionados y escribe el resultado, sus dos listas de limitaciones y las referencias en una única transacción.
- Una repetición idéntica es idempotente; una valoración distinta para la misma expectativa/unidad genera conflicto y no reemplaza la primera.
- `GET /api/v1/verifications/{runId}/results` solo está disponible para runs `COMPLETED`. Los resultados se leen por página y sus relaciones en lote, con orden estable.
- El tipo y la ubicación de evidencia se leen de la expectativa publicada. El historial humano se lee en orden cronológico; sus operaciones de escritura permanecen fuera del alcance.

### WBS 7.2.4 — decisión humana del estado final

- `PUT /api/v1/verifications/{runId}/results/{resultId}/decision` requiere un
  Administrador y un run `COMPLETED`. El body contiene la `revision` vigente,
  `finalStatus` y un `rationale` no vacío.
- Para aplicabilidad efectiva `APPLIES` se permiten `COMPLIES`,
  `DOES_NOT_COMPLY` y `REVIEW_REQUIRED`. `NOT_APPLICABLE` solo se permite para
  una expectativa `CASE_DEPENDENT` cuya aplicabilidad efectiva sea
  `NOT_APPLICABLE`; `UNDETERMINED` no admite una decisión de estado final.
- Cada confirmación o corrección agrega un evento `RESULT_STATUS` inmutable con
  el usuario autenticado y la hora del `Clock`, actualiza `effectiveStatus` e
  incrementa `revision` en una única transacción. `proposedStatus` y la
  justificación de la evaluación automática permanecen intactos.
- El bloqueo del resultado y la revisión condicional evitan registrar eventos
  para revisiones obsoletas. El historial se ordena cronológicamente, con un
  ordinal de inserción para decisiones con igual instante.
- Las decisiones solo modifican el resultado solicitado; no inician otra
  evaluación ni consultan Drive, Redmine u OpenAI.

## Diagrama Mermaid

```mermaid
flowchart TD
    A[POST /api/v1/verifications/preview o carga de contexto] --> B[VerificationContextLoader]
    B --> C[VerificationContext]
    C --> D[DefaultApplicabilityPolicy.assess]
    D --> E{ApplicabilityMode}
    E -- ALWAYS --> F[ApplicabilityAssessment.deterministic(APPLIES)]
    E -- CASE_DEPENDENT --> G[Reglas estructuradas del caso]
    G --> H{Coincidencia inequívoca?}
    H -- Sí --> I[APPLIES / NOT_APPLICABLE]
    H -- No --> J[ApplicabilityAiPort]
    J --> K[OpenAiApplicabilityAdapter]
    K --> L[AiApplicabilityRequest con facts autorizados]
    L --> M[IA propone decision + evidenceRefs + limitations]
    M --> N[ApplicabilityAssessment]
    N --> O[Evaluador documental / decision humana / persistencia posterior]
```

## Decisiones, supuestos y limitaciones conocidas

- La aplicabilidad es una pregunta previa al cumplimiento. No se sustituyen una por la otra.
- El contexto que procesa la IA debe ser de `CASE_DEPENDENT`; si no lo es, se rechaza explícitamente.
- La política requiere hechos autorizados y no inferencias amplias del texto libre.
- La IA es un fallback, no la fuente de verdad del dominio ni una forma de “corregir” la lógica determinista.
- Si el caso tiene contradictorias señales positivas y negativas, o no hay datos suficientes, la decisión final queda en `UNDETERMINED` o revisión.
- La salida estructurada de OpenAI se valida estrictamente para evitar respuestas truncadas o inventadas.
- La comprobación documental sigue siendo un componente posterior: la política de aplicabilidad no decide sobre contenido ni aprobación documental.

## Tests reales y verificados

| Clase | Qué cubre | Tipo de prueba | Requiere mocks / IA / Docker |
|---|---|---|---|
| `VerificationContextLoaderTest` | carga del contexto, validaciones de M2, catálogo, fuentes, scope, selección de tickets y errores funcionales | JUnit + Mockito | Mockito, sin Docker |
| `VerificationPreviewControllerSecurityTest` | preview de verificación, validación/seguridad del `POST /api/v1/verifications/preview` | Spring MVC + MockMvc + Mockito | Mockito, sin Docker |
| `OpenAiApplicabilityAdapterTest` | validación de `AiApplicabilityRequest`, rechazo de referencias inventadas, ausencia de hechos, y no-IA para `ALWAYS` | JUnit + Mockito | Mock `OpenAIClient`/`ResponseService`, sin OpenAI real |
| `DriveDocumentaryAssessmentServiceTest` | resultado documental según aplicabilidad, cardinalidad, ausencia, ambigüedad y estados `REVIEW_REQUIRED` | JUnit + FakeSearch | `FakeSearch` y `Clock.fixed`, sin Docker |
| `ProgrammedEvidenceCheckServiceTest` | orquestación de Drive/Redmine, aplicabilidad, referencias, aislamiento por ticket y fuentes sin regla/lector | JUnit + lectores falsos | lectores falsos y `Clock.fixed`, sin credenciales ni Docker |
| `JdbcVerificationResultRepositoryIntegrationTest` | persistencia PostgreSQL, validaciones, transacciones, idempotencia/conflictos, progreso, paginación, seguridad y respuestas HTTP | Spring Boot + Testcontainers | PostgreSQL 17 en Docker; sin cuentas externas |

## Requisitos previos

- Java 21 (`<java.version>21</java.version>` en `pom.xml`)
- Maven wrapper del proyecto: `./mvnw`
- Docker para la prueba de integración PostgreSQL de TC-176 y las demás pruebas que usan Testcontainers.

## Comandos exactos

### Compilar

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location 'C:\Users\User\OneDrive\Documentos\Repositorio-BackendTEMA-'
.\mvnw -q -DskipTests compile
```

### Suite completa

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location 'C:\Users\User\OneDrive\Documentos\Repositorio-BackendTEMA-'
.\mvnw -q test
```

La suite completa se ejecutó durante la validación de TC-176 y terminó correctamente: 202 pruebas, sin fallas ni errores. Las pruebas que usan Testcontainers necesitan Docker.

### TC-176 — persistencia PostgreSQL

```bash
./mvnw -Dtest=JdbcVerificationResultRepositoryIntegrationTest test
```

La clase contiene cuatro pruebas de integración y requiere Docker para iniciar
PostgreSQL 17 mediante Testcontainers. No requiere cuentas reales de Drive,
Redmine ni OpenAI.

### Solo tests 7.1.2

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location 'C:\Users\User\OneDrive\Documentos\Repositorio-BackendTEMA-'
.\mvnw -q -Dtest=VerificationContextLoaderTest test
```

### Solo tests TC-179

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location 'C:\Users\User\OneDrive\Documentos\Repositorio-BackendTEMA-'
.\mvnw -q -Dtest="OpenAiApplicabilityAdapterTest,DriveDocumentaryAssessmentServiceTest" test
```

### Clase o método puntual

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location 'C:\Users\User\OneDrive\Documentos\Repositorio-BackendTEMA-'
.\mvnw -q -Dtest=VerificationContextLoaderTest#loadsStartupInputsActivityRecordsOutputsAndServiceProcessContext test
```

## Cómo interpretar resultados

- Si el comando de compilación termina con exit code `0`, el código compila con Java 21.
- Si una clase de 7.1.2 o TC-179 termina con exit code `0`, se validó el comportamiento específico cubierto por esa prueba.
- Si la suite completa falla en una máquina sin Docker, la causa esperable es que varias pruebas del proyecto usan Testcontainers y requieren un entorno con Docker disponible.
- Los reportes de Surefire se generan en `target/surefire-reports` y se revisan ahí si falla una clase puntual.

## Qué hacer si Docker no está disponible

- Ejecutar los tests unitarios de verificación y aplicabilidad que no dependen de contenedores.
- Omitir temporalmente la integración PostgreSQL de TC-176 y las demás pruebas
  Testcontainers; no sustituirlas por mocks al validar transacciones o restricciones.
- Para validar esos casos, ejecutar `./mvnw test` en un entorno con Docker habilitado.

## Prueba manual del preview

El endpoint real es `POST /api/v1/verifications/preview` y la validación del request se prueba en `VerificationPreviewControllerSecurityTest`.

Ejemplo válido de request:

```http
POST /api/v1/verifications/preview HTTP/1.1
Content-Type: application/json
Authorization: Bearer <jwt-admin>

{
  "serviceId": "11111111-1111-4111-8111-111111111111",
  "processVersionId": "22222222-2222-4222-8222-222222222222",
  "scope": "MACRO_ACTIVITY",
  "macroActivityId": "33333333-3333-4333-8333-333333333333"
}
```

Ejemplo de respuesta esperada (forma real, no un payload de BD):

```json
{
  "serviceId": "11111111-1111-4111-8111-111111111111",
  "serviceRevision": 5,
  "processVersionId": "22222222-2222-4222-8222-222222222222",
  "scope": "MACRO_ACTIVITY",
  "macroActivityId": "33333333-3333-4333-8333-333333333333",
  "expectations": [
    {
      "id": "44444444-4444-4444-8444-444444444444",
      "location": "INPUT",
      "displayName": "Ticket",
      "applicability": "ALWAYS",
      "cardinality": "ONE",
      "ticketSelectionGroupId": "group-1"
    }
  ],
  "ticketSelectionGroups": [
    {
      "id": "group-1",
      "name": "Tickets",
      "inputExpectationIds": ["44444444-4444-4444-8444-444444444444"],
      "relatedExpectationIds": ["44444444-4444-4444-8444-444444444444"]
    }
  ]
}
```

No se usa la API real de OpenAI en los tests. El adapter se valida con un cliente mockeado; la IA real requiere variables del entorno y una cuenta con permisos.

## Estado de tareas

Completado:
- 7.1.2: carga de contexto y preview del proceso de verificación.
- 7.1.3: creación de runs y persistencia de selecciones de tickets.
- TC-176: persistencia transaccional de resultados, consulta de progreso y endpoint paginado.
- TC-178: integración de OpenAI para aplicabilidad.
- TC-179: política determinista + fallback IA + contratos de salida.

Pendiente en siguiente etapa:
- TC-175: trabajador asíncrono que evaluará y persistirá todas las unidades antes de completar el run.
- tareas posteriores: escritura de decisiones humanas y flujo completo de comentarios/resultado final del M2.
