# Guia de liderazgo frente a Android Studio

Fecha base: 2026-05-10
Estado global: Iniciado
Objetivo: llevar Sketchware Pro de paridad parcial a liderazgo funcional en flujos reales de desarrollo.

## Alcance de esta guia

Esta guia traduce los 6 objetivos estrategicos en entregables medibles, criterios de cierre y seguimiento semanal.

Documentos operativos:
- docs/android_studio_leadership_issues.md
- docs/android_studio_leadership_iteration1.md

Objetivos incluidos:
- Conectar LSP real (sin NoOp como ruta principal) y activarlo por defecto.
- Implementar JDWP real (breakpoints, variables, threads, profiler usable).
- Subir targetSdk del app principal y cerrar compatibilidad moderna.
- Completar seguridad de plugins (firma real + feed CVE real).
- Anadir pruebas instrumentadas y gates de calidad mas fuertes (UI/device).
- Completar migracion Android-only -> KMP e iOS asistido de punta a punta.

## Tablero de ejecucion

| ID | Frente | Estado | Owner | Inicio | ETA | Dependencias |
|---|---|---|---|---|---|---|
| L1 | LSP real + default-on | IN_PROGRESS | TDB | 2026-05-10 | TDB | Feature flags, editor runtime |
| D1 | JDWP real + profiler usable | IN_PROGRESS | TDB | 2026-05-10 | TDB | Debug runtime, transport |
| A1 | targetSdk moderno + compat | IN_PROGRESS | TDB | 2026-05-10 | TDB | Material/AppCompat, manifest |
| S1 | Firma plugins + CVE feed | TODO | TDB | TDB | TDB | Plugin security pipeline |
| Q1 | Instrumented tests + CI gates | IN_PROGRESS | TDB | 2026-05-10 | TDB | CI runners, device matrix |
| M1 | Migracion Android->KMP + iOS asistido | TODO | TDB | TDB | TDB | KMP workflow, export tooling |

Estados validos:
- TODO
- IN_PROGRESS
- BLOCKED
- DONE

## Entregables y Definition of Done

### L1 - LSP real + default-on
Entregables:
- Reemplazar ruta principal NoOp por cliente LSP real.
- Mantener fallback controlado solo ante fallo de inicializacion.
- Activar el flujo por defecto para usuarios nuevos.
- Medir latencia p50/p95 de completion, definition y diagnostics.

Definition of Done:
- Completion, definition y references funcionan sobre proyectos reales.
- La bandera de feature deja de ser bloqueo para uso normal.
- No hay regresion de rendimiento fuera de umbrales acordados.

### D1 - JDWP real + profiler usable
Entregables:
- Bridge JDWP conectado a proceso debug real.
- Breakpoints de linea y condicion funcionales.
- Variable inspector y thread inspect en sesion activa.
- Profiler CPU/Mem/Network visible y util para diagnostico.
- Export/persistencia de reportes de profiler por sesion (JSON) para analisis offline.

Definition of Done:
- Un escenario de debug end-to-end validado en proyecto de ejemplo.
- Al menos 1 prueba automatizada por flujo critico de debug.
- Reportes de profiler exportables o persistidos.

### A1 - targetSdk moderno + compat
Entregables:
- Subir targetSdk a baseline moderna del ecosistema.
- Ajustar permisos, behavior changes y compat UI.
- Validar build + runtime en APIs minimas y actuales.

Definition of Done:
- Build release y debug verdes en CI.
- Zero bloqueos criticos de comportamiento en smoke UI principal.

### S1 - Firma plugins + CVE feed
Entregables:
- Implementar validador real de firma de plugins.
- Integrar feed/fuente CVE para dependencias.
- Emitir score de riesgo accionable en reportes.

Definition of Done:
- Plugins sin firma valida se rechazan de forma explicita.
- Dependencias con CVE severas se reportan y bloquean segun politica.

### Q1 - Instrumented tests + CI quality gates
Entregables:
- Crear suite base de pruebas androidTest (UI/device).
- Ejecutar pruebas instrumentadas en CI.
- Agregar gates de calidad (minimo de tests, cero fallos, smoke UI).
- Aplicar politica anti-flaky explicita para suites instrumentadas criticas.

Definition of Done:
- PRs relevantes no pueden mergear con gates rojos.
- Flaky tests monitorizados con politica de cuarentena.
- Gate instrumentado valida suite smoke requerida, piso minimo de tests, y cero failures/errors.

### M1 - Migracion Android-only -> KMP + iOS asistido
Entregables:
- Completar asistente de migracion Android-only a KMP.
- Export/flujo asistido para iOS (hasta handoff en Mac/CI).
- Diagnosticos de compatibilidad y acciones sugeridas desde UI.

Definition of Done:
- Migracion guiada en proyecto legacy con reporte final.
- Flujo iOS asistido documentado y validado de punta a punta.

## Seguimiento semanal

Checklist semanal:
- [ ] Actualizar estado de L1, D1, A1, S1, Q1, M1.
- [ ] Registrar bloqueos nuevos y decision tomada.
- [ ] Registrar metricas clave (latencia, fallos CI, cobertura instrumentada).
- [ ] Revisar riesgos y ajustar ETA.

Plantilla de update:

```
Semana: YYYY-MM-DD
Resumen: <3-5 lineas>
Cambios por frente:
- L1:
- D1:
- A1:
- S1:
- Q1:
- M1:
Bloqueos:
- <bloqueo> / owner / fecha objetivo
Metricas:
- completion p95:
- debug session success rate:
- instrumented pass rate:
Decisiones:
- <decision y razon>
```

## Riesgos principales

- Rendimiento degradado al reemplazar NoOp por implementaciones reales.
- Inestabilidad CI por instrumented tests sin control de flaky.
- Complejidad de compatibilidad al subir targetSdk.
- Dependencias externas para CVE feed y pipeline de firma.
- Friccion de toolchain para handoff iOS en entorno movil.

## Politica de bloqueo

Si cualquier frente entra en BLOCKED mas de 7 dias:
- abrir issue de desbloqueo con owner y plan de salida,
- definir workaround temporal,
- escalar en la siguiente revision semanal.
