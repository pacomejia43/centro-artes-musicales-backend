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
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:3000,http://localhost:5500,http://127.0.0.1:5500` | Orígenes permitidos (separados por coma). Admite patrones con `*`, útil para Vercel: `https://tu-sitio.vercel.app,https://*.vercel.app` cubre también los despliegues de preview |
| `PORT` | No | `8080` | La define automáticamente Railway/Render/etc. — no hace falta configurarla a mano en esas plataformas |
| `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` | No | vacío | Si no existe ningún administrador al iniciar, se crea uno con estos datos. Si se dejan vacías, no se crea admin (con un WARN en el log) |
| `ADMIN_BOOTSTRAP_NOMBRE` | No | `Administrador` | Nombre del admin inicial |
| `APP_TIMEZONE` | No | `America/Mexico_City` | Zona horaria de negocio (agenda de clases, plazos) |
| `CLASES_LIMITE_MENSUAL` | No | `4` | Máximo de clases por alumno al mes (total, sin importar el instrumento) |
| `CLASES_HORAS_MINIMAS_REAGENDAR` | No | `4` | Horas mínimas de anticipación para que un alumno pueda solicitar reagendar una clase |
| `CLASES_DURACION_DEFAULT_MINUTOS` | No | `60` | Duración por defecto de una clase nueva |
| `PAGOS_MONTO_MENSUAL_DEFAULT` | No | `600.00` | Monto por defecto al crear un cargo mensual |
| `STRIPE_SECRET_KEY` | No* | vacío | Clave secreta de Stripe (modo prueba: `sk_test_...`). Nunca se expone al frontend |
| `STRIPE_PUBLISHABLE_KEY` | No* | vacío | Clave publicable de Stripe (`pk_test_...`) — no es secreta, pero se sirve desde el backend (`GET /api/alumno/pagos/config`) para no hardcodearla en el sitio estático |
| `STRIPE_WEBHOOK_SECRET` | No* | vacío | Firma con la que Stripe firma cada webhook (`whsec_...`). Sin ella, `POST /api/stripe/webhook` rechaza todo con 400 |
| `FRONTEND_URL` | No | `https://cedam.vercel.app` | Base del frontend desplegado (sin slash final) — el backend arma `success_url`/`cancel_url` de Stripe Checkout con esto |

\* El backend arranca sin estas tres, a diferencia de `DB_PASSWORD`/`JWT_SECRET` (para poder desplegar el código antes de terminar de configurar Stripe) — pero cualquier endpoint que dependa de Stripe responde con un error claro hasta que se configuren. Ver la sección [Stripe](#stripe) más abajo.

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

Única excepción: `POST /api/stripe/webhook` no lleva JWT (Stripe no puede mandar uno) — su
autenticidad la garantiza la firma `Stripe-Signature` verificada con `STRIPE_WEBHOOK_SECRET`, no
Spring Security. Ver la sección [Stripe](#stripe).

## Documentación de la API

Con la aplicación corriendo: `http://localhost:8080/swagger-ui.html`. Usa el botón **Authorize**
con un token obtenido de `POST /api/auth/login` para probar los endpoints protegidos.

## Pruebas

```bash
./mvnw test
```

Las pruebas de lógica de negocio (`ClaseServiceTest`, `PagoServiceTest` — cupo mensual, conflicto
de horario, plazo de reagendo, cálculo de saldo; `StripeWebhookServiceTest`, `StripeCheckoutServiceTest`,
`SuscripcionServiceTest`, `StripeWebhookControllerTest` — checkout con el saldo real de la BD,
idempotencia de webhooks, pagos/cobros fallidos, altas/cancelaciones de suscripción, firma
inválida) usan Mockito puro, sin base de datos. No requieren Docker. `StripeWebhookServiceTest`
construye cada `Event` deserializando JSON con la forma real de un webhook (vía el Gson que trae
el SDK) en vez de armar los objetos del modelo a mano, para ejercitar la misma ruta de
deserialización que un webhook real.

`CentroArtesMusicalesBackendApplicationTests.contextLoads` sí necesita una base MySQL real
corriendo (levanta el contexto completo de Spring) — es el único test que no corre sin una base de
datos disponible; el resto de la suite (incluida toda la lógica de Stripe) no depende de ella.

Si en el futuro se agregan pruebas de integración contra una base real (recomendado para
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
  hasta que el administrador los confirme o rechace; los que llegan por Stripe (Checkout o un
  cobro recurrente de Billing) quedan confirmados directamente por el webhook, sin intervención
  humana. Ver la sección [Stripe](#stripe).

## Stripe

Pagos con tarjeta (únicos y recurrentes) vía Stripe Checkout + Stripe Billing. El backend nunca
procesa números de tarjeta — solo crea la sesión de Checkout y confía en el webhook de Stripe
como única fuente de verdad de si un pago se completó.

### 1. Crear/configurar la cuenta de Stripe

1. Crea una cuenta en [stripe.com](https://dashboard.stripe.com/register) (o usa una existente).
2. Trabaja en **modo de prueba** (toggle "Test mode" en el Dashboard) mientras desarrollas — nunca
   uses claves `sk_live_`/`pk_live_` fuera de producción.
3. Copia `STRIPE_SECRET_KEY` y `STRIPE_PUBLISHABLE_KEY` desde
   [Developers → API keys](https://dashboard.stripe.com/test/apikeys).
4. Moneda: todo se cobra en **MXN**. Los montos se mandan a Stripe en centavos (`monto * 100`,
   como enteros) — nunca con floats, siguiendo la recomendación de Stripe.

### 2. Variables de entorno

Copia `.env.example` a `.env` (o configura las mismas variables en Railway) y llena la sección
`Stripe`. Ver la tabla de variables de entorno más arriba para el detalle de cada una.

### 3. Stripe CLI para pruebas locales

Los webhooks necesitan una URL pública; en local se usa el [Stripe CLI](https://docs.stripe.com/stripe-cli)
para reenviarlos a tu máquina:

```bash
stripe login
stripe listen --forward-to localhost:8080/api/stripe/webhook
```

El comando imprime un `whsec_...` — úsalo como `STRIPE_WEBHOOK_SECRET` mientras desarrollas en
local (es distinto del que usarás en producción, donde lo genera el endpoint del Dashboard).

### 4. Probar un pago único

1. Con el backend corriendo y `stripe listen` activo, crea un cargo para un alumno (`POST /api/admin/alumnos/{id}/pagos` o desde el panel admin).
2. Como ese alumno, llama `POST /api/alumno/pagos/{id}/checkout` (o usa el botón "Pagar ahora" en `mi-cuenta.html`) y abre la `url` que devuelve.
3. Paga con una [tarjeta de prueba](https://docs.stripe.com/testing#cards), ej. `4242 4242 4242 4242`, cualquier fecha futura, cualquier CVC.
4. Stripe redirige a `success_url` (`mi-cuenta.html?pago=exito&...`), que muestra "Estamos confirmando tu pago…".
5. `stripe listen` reenvía `checkout.session.completed` al backend, que crea una `PagoTransaccion` CONFIRMADA y recalcula el estado del `Pago`.
6. La app hace polling corto y muestra "Pagado ✓" en cuanto el webhook se procesó — no antes.

Para probar un pago rechazado, usa la tarjeta `4000 0000 0000 0002` (siempre declinada).

### 5. Probar una suscripción (pago automático)

1. Como alumno, llama `POST /api/alumno/suscripcion` (o el botón "Activar pago automático") y paga con `4242 4242 4242 4242` en el Checkout que abre.
2. Stripe crea la suscripción y dispara `customer.subscription.created` + `invoice.paid` (el primer cobro) — el backend guarda la fila `Suscripcion` y confirma el primer `Pago` del ciclo.
3. Para simular el siguiente cobro mensual sin esperar un mes real, usa `stripe trigger invoice.paid` o adelanta el reloj de facturación de la suscripción desde el Dashboard (Test clocks).
4. Para cancelar, usa `DELETE /api/alumno/suscripcion` (o el botón correspondiente) — programa la cancelación al final del periodo ya pagado; `customer.subscription.deleted` es lo que finalmente marca la suscripción como CANCELADA.

### 6. Webhooks que maneja el sistema (`POST /api/stripe/webhook`)

| Evento | Qué hace el backend |
|---|---|
| `checkout.session.completed` | Si es de un pago único (`mode=payment`) y quedó pagado, confirma la `PagoTransaccion` del cargo indicado en `metadata.payment_id` |
| `payment_intent.payment_failed` | Registra una `PagoTransaccion` en estado `FALLIDA` (no mueve el saldo) |
| `customer.subscription.created` / `.updated` | Crea o actualiza la fila `Suscripcion` del alumno (estado, precio, si tiene cancelación programada) |
| `customer.subscription.deleted` | Marca la `Suscripcion` como `CANCELADA` |
| `invoice.paid` | Cobro recurrente exitoso: crea el `Pago` del periodo si todavía no existía y lo confirma |
| `invoice.payment_failed` | Cobro recurrente fallido: registra una `PagoTransaccion` `FALLIDA` para ese periodo |
| `charge.refunded` | Marca la `PagoTransaccion` original como `REEMBOLSADA` y el `Pago` como `REEMBOLSADO` |

Todo evento se verifica con `STRIPE_WEBHOOK_SECRET` antes de procesarse (firma inválida → 400) y
se registra por su `id` nativo en `stripe_webhook_evento` para que una entrega duplicada de Stripe
no duplique pagos.

### 7. Qué ve el alumno en cada caso

- **Pago exitoso**: `mi-cuenta.html` muestra "Estamos confirmando tu pago…" al volver de Stripe y
  hace polling corto hasta ver "Pagado ✓" — nunca se lo muestra solo porque volvió del Checkout.
- **Pago fallido**: el cargo sigue `PENDIENTE`/`PARCIAL`; el alumno puede reintentar con "Pagar ahora" cuando quiera.
- **Suscripción cancelada**: sigue activa (con aviso de "se cancelará el [fecha]") hasta el fin del
  periodo ya pagado; después de eso, `mi-cuenta.html` vuelve a ofrecer "Activar pago automático".

### 8. Pasar a producción

1. Activa la cuenta de Stripe (datos fiscales/bancarios reales).
2. En el Dashboard, cambia a modo Live y copia las claves `sk_live_...` / `pk_live_...`.
3. Crea el endpoint de webhook real en [Developers → Webhooks](https://dashboard.stripe.com/webhooks)
   apuntando a `https://<tu-backend-en-railway>/api/stripe/webhook`, seleccionando al menos los
   eventos listados arriba, y copia su `whsec_...`.
4. Reemplaza `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY` y `STRIPE_WEBHOOK_SECRET` en Railway por
   los valores live, y confirma que `FRONTEND_URL` apunta al dominio real de producción.
5. Haz una prueba real con una tarjeta propia por un monto pequeño antes de anunciarlo a los alumnos.

## Fuera de alcance (decisiones deliberadas, no pendientes)

- Integración con Google Drive/Docs API: el backend guarda y sirve el link a la bitácora
  (`googleDocsUrl` en `Alumno`), pero no crea ni comparte el documento — eso lo sigue haciendo el
  Centro manualmente, como ya hace hoy.
- Recuperación de contraseña por correo: no hay proveedor de email configurado; el administrador
  resetea la contraseña de un alumno manualmente (`PUT /api/admin/alumnos/{id}/password`).
- Interfaz administrativa de reembolsos: no hay un botón en el panel admin para iniciar un
  reembolso — se hacen desde el Dashboard de Stripe, y el webhook `charge.refunded` sincroniza el
  resultado hacia la base de datos.
