# Android Studio Leadership Issues

Fecha: 2026-05-10
Objetivo: abrir y mantener issues tecnicos accionables para los 6 frentes estrategicos.

## EPIC L1 - LSP real + default-on

Titulo sugerido:
- EPIC-L1 Real LSP backend and default-on rollout

Checklist:
- [ ] Reemplazar fabricacion por defecto de cliente NoOp en capa LSP.
- [ ] Integrar backend LSP real para Java/Kotlin y estrategia de reconexion.
- [ ] Mantener fallback no-op solo en fallo de inicializacion.
- [ ] Activar default-on y mantener kill-switch via feature flag.
- [ ] Medir p50/p95 en completion/definition/references/diagnostics.

Archivos semilla:
- app/src/main/java/pro/sketchware/lsp/LspClientFactory.java
- app/src/main/java/pro/sketchware/lsp/NoOpLspClient.java
- app/src/main/java/pro/sketchware/featureflags/FeatureFlags.java

## EPIC D1 - JDWP real + profiler usable

Titulo sugerido:
- EPIC-D1 Production JDWP debug bridge and profiling runtime

Checklist:
- [x] Implementar bridge JDWP real y transporte de sesiones.
- [ ] Unificar breakpoints con runtime activo (linea/condicional/hit count).
- [x] Conectar inspector de variables y threads.
- [x] Conectar symbolication runtime con parseo de stacktrace y resultados persistidos.
- [x] Integrar runtime profiler real-first (CPU/Mem/Network) y muestreo por sesion adjunta.
- [x] Completar export/persistencia base de reportes de profiler (JSON por sesion).
- [x] Validar debug end-to-end con test automatizado.

Archivos semilla:
- app/src/main/java/pro/sketchware/debugger/jdwp/JdwpDebugSessionManager.java
- app/src/main/java/pro/sketchware/debugger/jdwp/NoOpJdwpBridge.java
- app/src/main/java/pro/sketchware/debugger/variables/NoOpJdwpVariableInspectorTransport.java
- app/src/main/java/pro/sketchware/debugger/symbolication/NoOpCrashSymbolicationWorkflow.java

## EPIC A1 - targetSdk moderno + compat

Titulo sugerido:
- EPIC-A1 targetSdk modernization and behavior-compat closure

Checklist:
- [ ] Subir targetSdk del app principal.
- [ ] Ejecutar matriz de compatibilidad (API minima, media, alta).
- [ ] Corregir permisos/comportamientos rotos por cambios de plataforma.
- [ ] Cerrar regresiones criticas de UI y flujos de build.

Archivos semilla:
- app/build.gradle
- app/src/main/AndroidManifest.xml
- app/src/main/res/values-v35/styles.xml (si aplica)

## EPIC S1 - firma de plugins + CVE feed

Titulo sugerido:
- EPIC-S1 Plugin signature enforcement and CVE-backed vulnerability pipeline

Checklist:
- [ ] Implementar validador de firma real (rechazo de firma invalida).
- [ ] Conectar fuente CVE real para escaneo de dependencias.
- [ ] Integrar politica de severidad para bloquear riesgos criticos.
- [ ] Exponer reporte de seguridad util en UI/logs.

Archivos semilla:
- app/src/main/java/pro/sketchware/plugins/manifest/PluginManifestVerifier.java
- app/src/main/java/pro/sketchware/plugins/security/NoOpPluginSignatureValidator.java
- app/src/main/java/pro/sketchware/plugins/security/vulnerability/NoOpPluginDependencyVulnerabilityScanAdapter.java

## EPIC Q1 - instrumented tests + quality gates

Titulo sugerido:
- EPIC-Q1 Device/UI test baseline and stronger CI quality gates

Checklist:
- [x] Crear baseline androidTest.
- [x] Publicar workflow dedicado de pruebas instrumentadas.
- [x] Definir minimo de suites y politicas anti-flaky.
- [x] Integrar gate obligatorio para rutas criticas (workflow instrumentado).
- [x] Agregar smoke UI scenario sobre actividad principal.
- [x] Agregar smoke de estabilidad de lifecycle y navegacion base (BottomNavigation).

Archivos semilla:
- app/src/androidTest/java/pro/sketchware/AndroidDeviceSmokeTest.java
- .github/workflows/android-instrumented.yml
- .github/workflows/verification.yml

## EPIC M1 - migracion Android-only -> KMP + iOS asistido

Titulo sugerido:
- EPIC-M1 Android-only to KMP migration completion and iOS assisted handoff

Checklist:
- [ ] Completar wizard de migracion Android-only -> KMP.
- [ ] Exponer reporte final con riesgos/pendientes por target.
- [ ] Implementar flujo iOS asistido con handoff reproducible a Mac/CI.
- [ ] Validar un proyecto legacy convertido de punta a punta.

Archivos semilla:
- app/src/main/java/pro/sketchware/kmp/KmpMigrationAssistantWorkflow.java
- app/src/main/java/pro/sketchware/kmp/KmpMigrationAnalyzer.java
- docs/kmp_functional_plan.md

## Convencion de estados y prioridad

Estados:
- TODO
- IN_PROGRESS
- BLOCKED
- DONE

Prioridad inicial recomendada:
1. L1
2. D1
3. A1
4. S1
5. Q1
6. M1
