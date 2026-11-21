# parko-admin-service

Backoffice del módulo web de administración de Parko (`parko-admin-web-app`). Es el único backend con el que habla el módulo web.

- Java 21, Spring Boot 4, Maven. Puerto `8083`, publicado por Caddy bajo `/api/v1/admin`.
- Lee la base compartida `parko_db` con las entidades de `parko-persistence-core`.
- Seguridad: valida el token de Firebase y exige que el usuario exista en `users` con `role = ADMIN` y `active = true`. Sin token responde 401; con token de otro usuario, 403.

## Endpoints

### Tickets (`/api/v1/admin/tickets`)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/{id}` | Todos los datos de un ticket por id (UUID) |
| GET | `/number/{ticketNumber}` | Lo mismo, por número de ticket |
| GET | `?from=&to=&status=&page=` | Listado paginado de a 20, del más reciente al más antiguo |
| POST | `/{id}/pay` | Registra el pago en efectivo de un ticket pendiente (visitante o usuario registrado) |

Filtros del listado (todos opcionales):

- `from`, `to`: fecha `yyyy-MM-dd`, ambas inclusivas, sobre la fecha de emisión. Un solo día: `from` y `to` iguales.
- `status`: `PENDING_PAYMENT`, `PAID`, `USED` o `CANCELLED`.
- `page`: base 0.

El detalle incluye ticket, sesión de estacionamiento, vehículo y titular (solo sesiones de usuarios registrados) y el cobro asociado.

El monto sale de la transacción `CHARGE` de la sesión. Los tickets de visitantes pagados con Mercado Pago no generan transacción: su monto es la tarifa fija de visitante (`PARKING_VISITOR_ENTRY_FEE`, 3000).

Pago en efectivo (`POST /{id}/pay`): tickets en `PENDING_PAYMENT`; en otro caso responde 409. Visitantes: la sesión debe estar `ACTIVE` y el monto es la tarifa de visitante. Usuarios registrados (ticket que quedó pendiente por saldo insuficiente): la sesión puede estar `ACTIVE` o `COMPLETED` (no `CANCELLED`) y el monto es la tarifa de entrada (`PARKING_ENTRY_FEE`, 1500). Pasa el ticket a `PAID` con `paidAt` y registra una transacción `CHARGE` `COMPLETED` con proveedor `CASH`, sin cuenta de saldo y con `externalRef = ADMIN:<uid del admin>`. No cierra el ticket ni la sesión: lo hace el tótem (visitantes) o la salida por patente (registrados), que al ver el cobro completado no descuenta saldo. El ticket se bloquea durante la operación para evitar doble cobro.

### Accesos (`/api/v1/admin/accesos`)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `?page=` | Listado paginado de a 20 de `access_log`, del más reciente al más antiguo |

Cada acceso incluye evento (`ENTRY`/`EXIT`), método (`PLATE`, `TICKET`, `PROXIMITY_KEY`), resultado (`AUTHORIZED`/`DENIED`), fecha, id y tipo de sesión, y patente (la de visitante si existe; si no, la registrada en la sesión). `page`: base 0.

### Usuarios (`/api/v1/admin/users`)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `?q=&role=&active=&page=` | Listado paginado de a 20. `q` busca por nombre, email o legajo |
| GET | `/{id}` | Detalle con saldo y vehículos |
| POST | | Alta de usuario: `fullName`, `email`, `password` obligatorios; `studentId`, `role` (por defecto `STUDENT`) y `emailVerified` (por defecto `false`) opcionales |
| PATCH | `/{id}` | Modifica cualquier dato: `fullName`, `email`, `studentId`, `role`, `active`, `emailVerified` |
| DELETE | `/{id}` | Baja lógica: `active = false` y cuenta de Firebase deshabilitada |

Reglas: el legajo y la división institucional se derivan del email (`legajo@division.frc.utn.edu.ar`). Si se cambia el email se actualizan ambos, y un legajo que no coincide con el email da 400. Email y legajo repetidos dan 409. Los cambios de email, nombre, estado y verificación se sincronizan con Firebase; si Firebase falla, el cambio en la base se revierte. Los administradores no se les cambia el rol desde este recurso.

Alta (`POST`): el admin define la contraseña inicial, que debe tener al menos 10 caracteres con mayúscula, minúscula, número y carácter especial (la misma política que identity-service). La contraseña solo viaja en la creación hacia Firebase: no se guarda en la base ni se devuelve. Se crea primero la cuenta de Firebase y después el usuario y su cuenta de saldo (en 0) en la base; si la base falla, la cuenta de Firebase se elimina. Responde 201 con el detalle. El legajo, si se envía, debe coincidir con el del email. No se puede crear con rol `ADMIN`. Email o legajo repetidos, en la base o en Firebase, dan 409.

### Vehículos (`/api/v1/admin/vehicles`)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `?plate=&userId=&active=&page=` | Listado paginado de a 20 |
| GET | `/{id}` | Detalle con datos del titular |
| POST | | Alta para un usuario (`userId`, `plate`, `brand`, `model`) |
| PATCH | `/{id}` | Modifica `plate`, `brand`, `model`, `userId` (reasignar) y `active` |
| DELETE | `/{id}` | Baja lógica: libera la patente agregándole `#id`, igual que identity-service |

Reglas: formato de patente `ABC123` o `AB123CD`, marca y modelo de hasta 15 caracteres, patente única entre vehículos activos (409) y máximo 3 vehículos activos por usuario, también al reasignar o reactivar.

### Reportes (`/api/v1/admin/reports`)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/occupancy?from=&to=` | Por día: pico y promedio de sesiones activas. Pico y promedio general del rango |
| GET | `/revenue?from=&to=` | Recaudación por día (registrados, visitantes, total), total por semana, promedio diario y mejor día de cada mes |
| GET | `/access?from=&to=` | Por día: ingresos y egresos de registrados y de visitantes, con totales |

`from` y `to` son fechas `yyyy-MM-dd`, inclusivas. Sin `to` se usa hoy; sin `from`, los 30 días anteriores a `to`. `from` posterior a `to` o un rango de más de 366 días dan 400. Todas las series incluyen los días sin datos con valores en 0. Los días se calculan en la zona horaria `REPORTS_ZONE` (por defecto `America/Argentina/Buenos_Aires`); la base guarda las fechas sin zona y se interpretan como hora local de esa zona.

- Ocupación: sesiones no canceladas, calculadas por intervalos de entrada y salida. El pico es el máximo de sesiones simultáneas dentro del día (una salida y una entrada en el mismo instante no se superponen). El promedio es ponderado por tiempo: segundos ocupados divididos por segundos del día (el día de hoy, hasta el momento actual). Una sesión que cruza la medianoche cuenta en ambos días; una sesión activa cuenta hasta ahora.
- Recaudación: transacciones `CHARGE` en estado `COMPLETED` agrupadas por fecha de creación, separadas por tipo de sesión (`REGISTERED` o `VISITOR`). Incluye saldo, efectivo y Mercado Pago. El promedio diario divide por todos los días del rango, también los de recaudación 0. Las semanas empiezan el lunes y se rotulan con ese lunes; en las semanas cortadas por el rango solo suman los días del rango. El mejor día de cada mes ignora los días sin recaudación y, ante empate, queda el más temprano.
- Accesos: eventos de `access_log` con resultado `AUTHORIZED`, separados por tipo de sesión y evento (`ENTRY` o `EXIT`).

Los pagos de visitantes por Mercado Pago aparecen en la recaudación solo desde que access-service registra la transacción al confirmar el pago; los anteriores no tienen transacción.

## Variables de entorno

| Variable | Default |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/parko_db` / `user` / `password` |
| `FIREBASE_PROJECT_ID` | `parko-2271f` |
| `FIREBASE_CREDENTIALS_PATH` | `/run/secrets/firebase_credentials` |
| `PARKING_ENTRY_FEE` | `1500` |
| `PARKING_VISITOR_ENTRY_FEE` | `3000` |
| `VEHICLES_MAX_ACTIVE_PER_USER` | `3` |
| `REPORTS_ZONE` | `America/Argentina/Buenos_Aires` |
| `REPORTS_DEFAULT_DAYS` / `REPORTS_MAX_DAYS` | `30` / `366` |
| `HIBERNATE_DDL_AUTO` | `validate` |

## Pruebas y cobertura

```
./mvnw verify
```

`verify` falla si la cobertura de líneas (JaCoCo) baja de 70 %. Reporte en `target/site/jacoco/index.html`.

Para compilar hace falta acceso a GitHub Packages (`GITHUB_ACTOR` y `PACKAGES_READ_TOKEN`, ver `.github/maven-settings.xml`).
