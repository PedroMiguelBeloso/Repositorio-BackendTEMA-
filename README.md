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
