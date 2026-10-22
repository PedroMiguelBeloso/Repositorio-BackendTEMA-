# Organización de paquetes del backend

Paquete base: `com.temaconsulting.agente_iso`. Los módulos de negocio siguen siendo
`catalog`, `identity`, `integration`, `process`, `service` y `verification`.
`shared` contiene únicamente piezas transversales. Esta reorganización cambia
ubicaciones de clases Java; no cambia rutas HTTP, tablas ni reglas funcionales.

## Estructura dentro de cada módulo

```text
<modulo>/
  api/                 Controladores y orquestación HTTP
    dto/               Requests y responses del contrato HTTP
    mapper/            Conversión entre DTO y modelo, cuando se necesita
    error/             Traducción de excepciones a respuestas HTTP
  application/         Casos de uso y servicios de aplicación
    port/              Interfaces de dependencias externas
    model/             Datos internos de los casos de uso
    mapper/            Conversión entre modelos internos, cuando se necesita
    error/             Fallos de un caso de uso
  domain/              Entidades, valores y reglas de negocio
    error/             Violaciones de reglas del dominio
  infrastructure/      Implementaciones técnicas de los puertos
    persistence/       Repositorios JDBC
```

Solo existen las subcarpetas que tienen clases concretas. Las clases principales
de un nivel permanecen en `api`, `application` o `domain` si otra subcarpeta no
expresa mejor su responsabilidad. Un `record` se ubica por su función: un DTO
HTTP en `api.dto`, un dato interno en `application.model` y un valor de negocio
en `domain`; no se agrupan todos los `record` por su sintaxis Java.

## Especializaciones de infraestructura

| Módulo | Subpaquete | Responsabilidad |
| --- | --- | --- |
| `identity` | `bootstrap` | Creación inicial del administrador. |
| `identity` | `security` | JWT, filtros y configuración de Spring Security. |
| `integration` | `config` | Configuración de clientes y pruebas de conexión. |
| `integration` | `drive`, `redmine` | Adaptadores de lectura para cada fuente. |
| `verification` | `ai` | Cliente y adaptador de IA. |
| `verification` | `config` | Configuración del ejecutor de verificaciones. |
| `verification` | `lifecycle` | Recuperación de ejecuciones interrumpidas. |

En `shared`, `config` agrupa configuración transversal; `api.error` contiene el
manejo común de errores HTTP, `api.mapper` la paginación HTTP, `api.dto` sus DTO compartidos y
`application.error`/`application.model` los tipos compartidos entre casos de uso.

Al agregar una clase, elegí primero el módulo dueño de la regla. Después elegí
la capa según la responsabilidad de la clase y solo entonces la subcarpeta.
Evitá que un módulo acceda directamente a la implementación JDBC de otro;
consumí su interfaz pública cuando exista.
