# Cobranza semanal y errores HTTP

## Reglas implementadas

- Semana calendario de lunes a domingo; `weekStart` debe ser lunes.
- Se calcula una cuenta para cada alumno activo con asignación de paquete vigente al lunes de esa semana. El paquete cobra su tarifa semanal completa aunque el alumno no asista, no tenga entradas/salidas o la asignación comience después del lunes. Esta regla evita prorrateo implícito; confirmar antes de producción.
- El paquete requiere precio semanal mayor que cero para generar cuenta. Migración V9 da valor inicial 0 a paquetes históricos, por lo que se debe crear una nueva versión con precio antes de calcularles cobros.
- Tiempo extra se calcula por día con el OvertimeCalculator existente. Minutos hasta la tolerancia no se cobran; al excederla se cobra cada hora empezada completa.
- Una asistencia incompleta (falta entrada o salida, o salida no posterior a entrada) no genera extra; debe corregirse antes de cerrar la semana.
- `calculate` es repetible en cuentas OPEN: reemplaza únicamente conceptos automáticos base/extra y no toca cuentas FINALIZED. No duplica cargos. Pagos requieren cuenta FINALIZED y no aceptan sobrepago.
- Cargos conservan instantánea de precio/condiciones. Pagos son inmutables desde la API de este alcance; no hay reverso/refund aún.

## Rutas

- `POST /api/v1/branches/{branchId}/billing/weeks/{weekStart}/calculate`: genera/recalcula cuentas para alumnos elegibles; respuesta `accountsConsidered`.
- `GET /api/v1/branches/{branchId}/billing/summary?weekStart=YYYY-MM-DD`: total de cuentas, cargos, cobrado y saldo de la sucursal.
- `GET /api/v1/branches/{branchId}/billing/students/{studentId}/weeks/{weekStart}`: detalle de cuenta, desglose y saldo.
- `POST /api/v1/branches/{branchId}/billing/students/{studentId}/weeks/{weekStart}/finalize`: congela cargos.
- `POST /api/v1/branches/{branchId}/billing/students/{studentId}/weeks/{weekStart}/payments`: registra pago parcial o total; JSON: `{"amount":500.00,"paymentMethod":"CASH","reference":"REC-001"}`.

Se requiere JWT con TENANT_ADMIN o BRANCH_DIRECTOR asignado a esa sucursal.

## Problem Details

Los errores del MVC se serializan como `application/problem+json` (Problem Details, RFC 7807 / RFC 9457), con `type`, `title`, `status`, `detail`, `instance`, `timestamp` y `code`; validaciones agregan `errors` con campo y mensaje. Errores de seguridad que ocurren antes de entrar al MVC pueden ser generados por Spring Security y deben probarse por separado.
