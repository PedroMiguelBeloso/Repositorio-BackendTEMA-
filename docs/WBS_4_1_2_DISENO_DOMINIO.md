# WBS 4.1.2 - Diseño de dominio: Gestión de Lector y contraseña

## Invariantes y operaciones

| Regla | Ubicación | Motivo |
|---|---|---|
| Un alta por la API crea siempre un `READER` activo | `identity.domain.User.createReader(...)` | El rol no forma parte de la operación de negocio ni puede ser sustituido por el servicio o por el request. |
| Un usuario debe tener id, email, display name, hash y rol válidos | `identity.domain.User` | Son invariantes propias de la entidad y deben protegerse aunque la entidad sea creada fuera del controlador. |
| El email se almacena canónicamente en minúsculas y sin espacios externos | `identity.domain.User` | Evita que distintas representaciones del mismo correo recorran el dominio como usuarios distintos. |
| El hash de contraseña no se asigna libremente | `identity.domain.User.changePasswordHash(...)` | La modificación de estado pasa por una operación de negocio explícita. |
| El cambio de contraseña no modifica id, email, display name, rol, active ni createdAt | `User.changePasswordHash(...)` + `UserRepository.updatePassword(User)` | El caso de uso persiste únicamente `password_hash`. |
| La nueva contraseña debe tener al menos 8 caracteres | `ChangePasswordRequest` / `CreateReaderRequest` | Es una regla de forma de la solicitud y está expresada en el contrato HTTP. |
| La contraseña actual debe coincidir antes de cambiarla | `identity.application.AuthService.changePassword(...)` | Requiere consultar al usuario autenticado y usar un verificador de hashes; no es una regla que deba vivir en la entidad. |
| El usuario afectado es el sujeto autenticado | `identity.api.AuthController` | El `userId` no proviene del cliente: se obtiene del contexto de seguridad/JWT. |
| Solo `ADMIN` puede listar/crear usuarios | `identity.infrastructure.SecurityConfig` | Es una regla de autorización de transporte/seguridad, no una invariante interna de `User`. |
| El correo debe ser único bajo concurrencia | `V4__identity_constraints.sql` | La comprobación previa de la aplicación mejora el error normal, pero el índice único en PostgreSQL es la garantía final ante solicitudes concurrentes. |
| La respuesta pública nunca contiene `password_hash` | `UserResponse` + `UserApiMapper` | Se separa deliberadamente el modelo persistente del contrato HTTP público. |

## Responsabilidades por capa

- **Controller/DTO:** forma HTTP, validación de campos, extracción del sujeto autenticado y códigos HTTP.
- **Application:** coordina repositorio, hashing y transacción; no decide el rol de un Reader ni muta directamente el hash.
- **Domain:** concentra invariantes y operaciones propias de `User`, sin Spring, HTTP, DTO ni JDBC.
- **Infrastructure:** persiste con JDBC, traduce el conflicto de clave única a una excepción de aplicación y aplica la restricción concurrente en PostgreSQL.
