# Centro de Artes Musicales — Backend

API REST para la administración del Centro de Artes Musicales: alumnos, profesores, clases,
asistencia, reagendos y pagos. Construida en Spring Boot para ser consumida por el frontend del
Centro, que vive en un repositorio aparte.

## Stack

- Java 21, Spring Boot 4.1 (Spring Framework 7 / Spring Security 7.1)
- Spring Data JPA + MySQL
- Flyway para migraciones versionadas (`src/main/resources/db/migration`)
- JWT (jjwt) para autenticación, sin sesiones de servidor
- springdoc-openapi para documentación interactiva (Swagger UI)
- Lombok

## Requisitos

- JDK 21
- MySQL 8.x corriendo localmente (o accesible por red)
- No requiere Maven instalado — usa el wrapper (`./mvnw`)

## Variables de entorno

No hay valores por defecto para secretos: la aplicación falla al iniciar si faltan `DB_PASSWORD`
o `JWT_SECRET`, a propósito, para no repetir el problema de contraseña hardcodeada que tenía el
`application.properties` original.

| Variable | Requerida | Default | Descripción |
|---|---|---|---|
| `DB_URL` | No | `jdbc:mysql://localhost:3306/centro_artes_musicales` | URL JDBC de MySQL |
| `DB_USERNAME` | No | `root` | Usuario de MySQL |
| `DB_PASSWORD` | **Sí** | — | Contraseña de MySQL |
| `JWT_SECRET` | **Sí** | — | Secreto para firmar tokens JWT (HMAC-SHA256, mínimo 256 bits / 32+ caracteres) |
| `JWT_EXPIRATION_MS` | No | `86400000` (24h) | Vigencia del token |
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:3000,http://localhost:5500,http://127.0.0.1:5500` | Orígenes permitidos (separados por coma) — agregar aquí la URL del frontend desplegado |
| `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` | No | vacío | Si no existe ningún administrador al iniciar, se crea uno con estos datos. Si se dejan vacías, no se crea admin (con un WARN en el log) |
| `ADMIN_BOOTSTRAP_NOMBRE` | No | `Administrador` | Nombre del admin inicial |
| `APP_TIMEZONE` | No | `America/Mexico_City` | Zona horaria de negocio (agenda de clases, plazos) |
| `CLASES_LIMITE_MENSUAL` | No | `4` | Máximo de clases por alumno al mes (total, sin importar el instrumento) |
| `CLASES_HORAS_MINIMAS_REAGENDAR` | No | `4` | Horas mínimas de anticipación para que un alumno pueda solicitar reagendar una clase |
| `CLASES_DURACION_DEFAULT_MINUTOS` | No | `60` | Duración por defecto de una clase nueva |
| `PAGOS_MONTO_MENSUAL_DEFAULT` | No | `600.00` | Monto por defecto al crear un cargo mensual |

Ejemplo para correr en local (PowerShell):

```powershell
$env:DB_PASSWORD = "tu-password-mysql"
$env:JWT_SECRET = "una-cadena-aleatoria-de-al-menos-32-caracteres"
$env:ADMIN_BOOTSTRAP_EMAIL = "admin@centroartesmusicales.com"
$env:ADMIN_BOOTSTRAP_PASSWORD = "cambia-esta-password"
./mvnw spring-boot:run
```

## Base de datos y migraciones

El esquema se versiona con Flyway (`src/main/resources/db/migration/V*.sql`); Hibernate corre con
`ddl-auto=validate`, nunca genera ni modifica tablas por su cuenta. Crea la base vacía
(`CREATE DATABASE centro_artes_musicales;`) antes del primer arranque — Flyway se encarga del
resto automáticamente al iniciar la aplicación.

## Autenticación y roles

Dos roles: `ADMIN` y `ALUMNO`. El registro público (`POST /api/auth/registro`) solo puede crear
alumnos — el primer administrador se crea vía las variables `ADMIN_BOOTSTRAP_*` de arriba. Todas
las rutas bajo `/api/admin/**` requieren rol ADMIN; todas bajo `/api/alumno/**` requieren rol
ALUMNO y siempre están auto-referidas al usuario autenticado (nunca se pasa el id del alumno por
URL). El token JWT se envía como `Authorization: Bearer <token>`.

## Documentación de la API

Con la aplicación corriendo: `http://localhost:8080/swagger-ui.html`. Usa el botón **Authorize**
con un token obtenido de `POST /api/auth/login` para probar los endpoints protegidos.

## Pruebas

```bash
./mvnw test
```

Las pruebas de lógica de negocio (`ClaseServiceTest`, `PagoServiceTest` — cupo mensual, conflicto
de horario, plazo de reagendo, cálculo de saldo) usan Mockito puro, sin base de datos. No requieren
Docker. Si en el futuro se agregan pruebas de integración contra una base real (recomendado para
validar las queries JPQL del cupo mensual y el conflicto de horario, que aquí solo se probaron a
nivel de servicio), usar Testcontainers con MySQL en vez de H2 — Flyway apunta específicamente a
MySQL y H2 no lo emula con fidelidad suficiente.

## Reglas de negocio relevantes

- **Cupo mensual**: 4 clases por alumno al mes en total (no por instrumento) — un alumno puede
  repartirlas entre distintos instrumentos. `PROGRAMADA`, `REALIZADA` y `AUSENTE` cuentan contra
  el cupo; `CANCELADA` y `REAGENDADA` no.
- **Reagendo**: el alumno solo puede *solicitar* un cambio de horario (con la anticipación mínima
  configurada); el cambio no se aplica hasta que un administrador lo aprueba, verificando
  disponibilidad del profesor y el cupo del mes destino. El administrador sí puede reagendar
  directamente sin pasar por ese flujo de aprobación.
- **Falta no gestionada**: si el alumno no solicita reagendo a tiempo y no asiste, la clase se
  marca `AUSENTE` y de todas formas cuenta como clase tomada del mes.
- **Pagos**: un cargo (`Pago`) por alumno por mes, sin importar cuántos instrumentos curse. Cada
  pago registrado es una transacción (`PagoTransaccion`) separada — los que registra el
  administrador quedan confirmados de inmediato; los que autorreporta el alumno quedan pendientes
  hasta que el administrador los confirme o rechace.

## Fuera de alcance (decisiones deliberadas, no pendientes)

- Integración con Google Drive/Docs API: el backend guarda y sirve el link a la bitácora
  (`googleDocsUrl` en `Alumno`), pero no crea ni comparte el documento — eso lo sigue haciendo el
  Centro manualmente, como ya hace hoy.
- Pasarela de pago real (Stripe/MercadoPago/etc.): no se integró ninguna porque no se especificó
  proveedor ni hay credenciales. El flujo de autorreporte + confirmación está diseñado para que un
  webhook de una pasarela real pueda conectarse más adelante al mismo mecanismo de confirmación.
- Recuperación de contraseña por correo: no hay proveedor de email configurado; el administrador
  resetea la contraseña de un alumno manualmente (`PUT /api/admin/alumnos/{id}/password`).
