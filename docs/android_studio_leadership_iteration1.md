# Iteracion 1 - Leadership Track

Ventana sugerida: 2 semanas
Fecha inicio: 2026-05-10
Objetivo de iteracion: dejar un baseline ejecutable para Q1 y un plan tecnico por archivo para L1/D1/A1/S1/M1.

## Resultado esperado al cierre

- Baseline de pruebas instrumentadas funcionando en CI.
- Gating inicial para device tests disponible para PR/push.
- Backlog tecnico trazable a issues por frente.
- Lista concreta de archivos de implementacion para arrancar L1 y A1 en la siguiente iteracion.

## Entregables de esta iteracion

### Q1 baseline tecnico
- [x] Dependencias androidTest y runner configurados.
- [x] Test instrumentado smoke creado.
- [x] Workflow de emulador y connectedDebugAndroidTest publicado.
- [x] Smoke UI instrumentado de arranque de MainActivity con ActivityScenario.

### Trazabilidad y ejecucion
- [x] Guia principal con estado Q1 en progreso.
- [x] Catalogo de issues por frente (L1/D1/A1/S1/Q1/M1).
- [x] Plan de iteracion documentado.

## Plan tecnico por frente (arranque proxima iteracion)

### L1 - LSP real
Archivos de arranque:
- app/src/main/java/pro/sketchware/lsp/LspClientFactory.java
- app/src/main/java/pro/sketchware/lsp/NoOpLspClient.java
- app/src/main/java/pro/sketchware/featureflags/FeatureFlags.java

Tareas iniciales:
- Implementar factory con proveedor real y fallback.
- Activar default-on con kill-switch.
- Agregar metrica de disponibilidad de backend real.

### D1 - JDWP real
Archivos de arranque:
- app/src/main/java/pro/sketchware/debugger/jdwp/JdwpDebugSessionManager.java
- app/src/main/java/pro/sketchware/debugger/jdwp/NoOpJdwpBridge.java
- app/src/main/java/pro/sketchware/debugger/variables/NoOpJdwpVariableInspectorTransport.java

Tareas iniciales:
- Definir contrato de conexion/sesion real.
- Sustituir transportes no-op con implementaciones activas.
- Crear smoke test de sesion debug.

### A1 - targetSdk moderno
Archivos de arranque:
- app/build.gradle
- app/src/main/AndroidManifest.xml

Tareas iniciales:
- Preparar branch de migracion targetSdk.
- Ejecutar lista de compatibilidad por API.
- Registrar breaking behaviors por modulo.

### S1 - seguridad plugins
Archivos de arranque:
- app/src/main/java/pro/sketchware/plugins/manifest/PluginManifestVerifier.java
- app/src/main/java/pro/sketchware/plugins/security/NoOpPluginSignatureValidator.java
- app/src/main/java/pro/sketchware/plugins/security/vulnerability/NoOpPluginDependencyVulnerabilityScanAdapter.java

Tareas iniciales:
- Definir backend de firma y formato soportado.
- Definir fuente CVE y estrategia de cache.
- Definir politica de bloqueo por severidad.

### M1 - migracion KMP + iOS asistido
Archivos de arranque:
- app/src/main/java/pro/sketchware/kmp/KmpMigrationAssistantWorkflow.java
- docs/kmp_functional_plan.md

Tareas iniciales:
- Cerrar rutas pendientes del asistente Android-only -> KMP.
- Definir plantilla de handoff iOS para Mac/CI.
- Validar flujo completo con proyecto legacy.

## Criterios de salida

- Workflow instrumentado ejecuta androidTest sin errores de configuracion.
- Documentacion de ejecucion y issues disponible para el equipo.
- Se aprueba scope de la siguiente iteracion (L1 + A1 como foco).
