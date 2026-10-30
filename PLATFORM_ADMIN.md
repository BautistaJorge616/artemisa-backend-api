# Administración de plataforma

La administración de Artemisa está separada de los roles institucionales. `PLATFORM_ADMIN` vive en `platform_role_assignments`; no se agrega a `roles` ni a `membership_role_assignments`.

## Primera cuenta de Artemisa

La migración V10 crea `platform_users` y `platform_role_assignments`. Para crear la primera cuenta, inicia una sola vez la aplicación con estas variables:

```bash
export PLATFORM_ADMIN_BOOTSTRAP_ENABLED=true
export PLATFORM_ADMIN_EMAIL='ops@artemisa.example'
export PLATFORM_ADMIN_FULL_NAME='Operador Artemisa'
export PLATFORM_ADMIN_PASSWORD='usa-una-clave-larga-y-unica'
```

El bootstrap solo crea la cuenta si ese correo aún no existe. Después del primer arranque, desactiva `PLATFORM_ADMIN_BOOTSTRAP_ENABLED` y elimina la contraseña del entorno. No publiques estas variables ni las guardes en Git.

## Inicio de sesión de plataforma

`POST /api/v1/platform/auth/login` recibe correo y contraseña y devuelve un token con `principal_type=PLATFORM` y el rol de plataforma.

## Alta de institución

`POST /api/v1/platform/tenants` requiere Bearer token de plataforma con `PLATFORM_ADMIN`. Crea la institución, sucursal inicial, usuario responsable, membresía y `TENANT_ADMIN` dentro de la transacción existente; registra en `tenants.created_by_platform_user_id` quién ejecutó el alta.

En este MVP el request incluye `initialPassword` para permitir el alta completa. Para producción, se recomienda cambiarlo por invitación de activación de un solo uso y no transportar contraseñas temporales.

El antiguo `POST /api/v1/onboarding` queda denegado. Las altas nuevas se realizan por el endpoint protegido de plataforma.

## Personal visible por sucursal

`GET /api/v1/branches/{branchId}/staff` permite que `TENANT_ADMIN` o la directora asignada consulte membresías de su tenant asociadas a esa sucursal, incluyendo administradores institucionales. Una directora no consulta otras sucursales.
