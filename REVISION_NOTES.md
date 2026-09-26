# Backend TEMA - Revision Notes

This revision aligns the implemented Identity slice with the supplied OpenAPI contract, the DBML model, and the Identity UML tab.

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

The uploaded repository still contains placeholder packages for `catalog`, `process`, `service`, `verification`, and `integration`. This revision does not claim that the complete M1/M2 application has been implemented; it brings the implemented Identity slice and the persistence foundation into alignment with the supplied specifications.
