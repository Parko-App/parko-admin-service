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

Filtros del listado (todos opcionales):

- `from`, `to`: fecha `yyyy-MM-dd`, ambas inclusivas, sobre la fecha de emisión. Un solo día: `from` y `to` iguales.
- `status`: `PENDING_PAYMENT`, `PAID`, `USED` o `CANCELLED`.
- `page`: base 0.

El detalle incluye ticket, sesión de estacionamiento, vehículo y titular (solo sesiones de usuarios registrados) y el cobro asociado.

El monto sale de la transacción `CHARGE` de la sesión. Los tickets de visitantes no generan transacción: su monto es la tarifa fija de visitante (`PARKING_VISITOR_ENTRY_FEE`, 3000).

### Usuarios (`/api/v1/admin/users`)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `?q=&role=&active=&page=` | Listado paginado de a 20. `q` busca por nombre, email o legajo |
| GET | `/{id}` | Detalle con saldo y vehículos |
| PATCH | `/{id}` | Modifica cualquier dato: `fullName`, `email`, `studentId`, `role`, `active`, `emailVerified` |
| DELETE | `/{id}` | Baja lógica: `active = false` y cuenta de Firebase deshabilitada |

Reglas: el legajo y la división institucional se derivan del email (`legajo@division.frc.utn.edu.ar`). Si se cambia el email se actualizan ambos, y un legajo que no coincide con el email da 400. Email y legajo repetidos dan 409. Los cambios de email, nombre, estado y verificación se sincronizan con Firebase; si Firebase falla, el cambio en la base se revierte. Los administradores no se les cambia el rol desde este recurso. Los usuarios no se crean desde el panel: se registran desde la app.

### Vehículos (`/api/v1/admin/vehicles`)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `?plate=&userId=&active=&page=` | Listado paginado de a 20 |
| GET | `/{id}` | Detalle con datos del titular |
| POST | | Alta para un usuario (`userId`, `plate`, `brand`, `model`) |
| PATCH | `/{id}` | Modifica `plate`, `brand`, `model`, `userId` (reasignar) y `active` |
| DELETE | `/{id}` | Baja lógica: libera la patente agregándole `#id`, igual que identity-service |

Reglas: formato de patente `ABC123` o `AB123CD`, marca y modelo de hasta 15 caracteres, patente única entre vehículos activos (409) y máximo 3 vehículos activos por usuario, también al reasignar o reactivar.

## Variables de entorno

| Variable | Default |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/parko_db` / `user` / `password` |
| `FIREBASE_PROJECT_ID` | `parko-2271f` |
| `FIREBASE_CREDENTIALS_PATH` | `/run/secrets/firebase_credentials` |
| `PARKING_VISITOR_ENTRY_FEE` | `3000` |
| `VEHICLES_MAX_ACTIVE_PER_USER` | `3` |
| `HIBERNATE_DDL_AUTO` | `validate` |

## Pruebas y cobertura

```
./mvnw verify
```

`verify` falla si la cobertura de líneas (JaCoCo) baja de 70 %. Reporte en `target/site/jacoco/index.html`.

Para compilar hace falta acceso a GitHub Packages (`GITHUB_ACTOR` y `PACKAGES_READ_TOKEN`, ver `.github/maven-settings.xml`).
