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
su nombre; sin ese adaptador responde `503` y no persiste el servicio. El módulo
de integraciones implementará ese puerto. El endpoint
`GET /api/v1/services/{serviceId}/tickets` ya está preparado para consumir
`ServiceTicketSearch`; sin proyecto Redmine o sin adaptador responde `503`.
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
necesitan variables de Drive ni Redmine para arrancar el backend en esta etapa.
Los adaptadores de lectura reales corresponden a 6.1.1 y 6.2.1. Esas tareas
reemplazarán `UnconfiguredDriveProbe` y `UnconfiguredRedmineProbe` por
implementaciones de `IntegrationProbe` que realicen una lectura efectiva; hasta
entonces, ambos endpoints informan `NOT_CONFIGURED`. Configurar una URL o
credenciales por sí solo nunca produce `AVAILABLE`; ese estado exige una lectura
exitosa. Las futuras credenciales deben mantenerse fuera del repositorio.
