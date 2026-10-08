# Backend TEMA - Revision Notes

This revision contains the Identity, M1 process/service/catalog, and M2 verification-preview slices on the integration branch, along with the persistence foundation and corresponding tests.

## Identity changes

- Added `UserController` with `GET /users` and `POST /users`.
- Added `CreateReaderRequest` and `ChangePasswordRequest` with OpenAPI validation constraints.
- Added `UserService` for paginated user listing and Reader creation.
- Extended `UserRepository` with pagination and password update operations.
- Extended `JdbcUserRepository` accordingly.
- Added `PUT /auth/password`.
- Refactored `AuthService.currentUser` to return the domain `User` and moved API mapping to the controller.
- Added `JwtTokenIssuer` application port; `JwtTokenService` implements it in infrastructure.
- Added dedicated `JsonAuthenticationEntryPoint` and `JsonAccessDeniedHandler`.
- Moved the BCrypt `PasswordEncoder` bean into `SecurityConfig`.
- Kept CORS and the application `Clock` in shared configuration; `SecurityConfig` consumes the CORS bean.
- Replaced `GlobalExceptionHandler` with `ApiExceptionHandler` and aligned security/problem codes with the OpenAPI `ProblemCode` values.
- Kept Bootstrap Admin credentials external to source control through environment-backed properties and made an existing admin a no-op for bootstrap.
- Added a Flyway `V1__base_schema.sql` derived from the supplied DBML, including the Identity `users` table and the M1/M2 persistence schema defined by that model.
- Updated Testcontainers configuration to force UTC for tests so the container does not inherit an invalid local PostgreSQL timezone identifier.
- Added/updated unit and MVC security tests for login, `/auth/me`, password change, ADMIN/READER authorization, Reader creation, pagination, password hashing, JWT role conversion, and expired JWT validation.

## Important scope note

M1 owns the published service/process/catalog configuration. M2 reads those public application boundaries to form an in-memory verification context; this task does not implement external evidence retrieval or verification-run persistence.

## Task 7.1.1 — Verification preview and domain-rule alignment

- Implemented `POST /api/v1/verifications/preview` as a read-only ADMIN use case.
- Added `VerificationTarget`, `VerificationPreview`, `PreviewExpectation`, `TicketSelectionGroup`, and the `TicketSelectionPlanner` domain service.
- Moved local invariants into domain objects: scope/macroActivity consistency, expectation location/activity consistency, stable ticket-group identity, ticket-input cardinality, related expectation membership, and preview group/expectation relationships.
- `TicketSelectionPlanner` now receives only the `MacroActivity` aggregate and derives its expectation set from the aggregate, so grouping decisions are not made by the application service.
- `VerificationContextLoader` coordinates public read boundaries for `service`, `process` and `catalog`, validates publication/assignment/M2 eligibility, and resolves PROCESS vs MACRO_ACTIVITY.
- Application-layer preview failures no longer carry `HttpStatus`; `verification.api.VerificationApiExceptionHandler` translates semantic failures to RFC 9457 `ProblemDetail` responses.
- No Drive, Redmine, GPT, verification-run persistence, ticket-selection persistence, or new database migration was added for this preview-only task.
- Added domain, context-loader, and controller security tests plus `docs/7.1.1-domain-rule-placement.md`.
- The supplied DBML does not model an explicit ticket-context foreign key for documentary evidence. The implementation therefore uses only typed evidence data (`DOCUMENT` + `CASE_DEPENDENT`) for the permitted contextual association and never inspects visible names.

## Task 7.1.2 — Verification context

- Added immutable in-memory service, process, scope, macroactivity and expectation context for reuse by preview and future execution.
- Resolved catalog definitions in one batch; retained each expectation independently and rejected missing definitions or published DOCUMENT/FOLDER definitions without official nomenclature.
- Kept the 7.1.1 preview response shape unchanged because the referenced OpenAPI YAML is not present in this checkout.
- Added fixture-based loader tests for source absence, exact assignments, scope/order, applicability/cardinality, catalog types and preview/context ID consistency.
