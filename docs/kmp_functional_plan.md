# Plan Funcional: Kotlin Multiplatform en Sketchware Pro

Version: 1.0  
Fecha: Mayo 2026  
Alcance: Integracion completa de KMP como target de compilacion y paradigma de proyecto  
Premisa: El usuario de Sketchware Pro puede crear apps Android, iOS, Desktop y Web desde un unico proyecto visual/codigo, sin salir del IDE movil.

## Indice

1. Vision general y alcance real
2. Encaje de KMP en Sketchware
3. Arquitectura del sistema KMP en Sketchware Pro
4. Motor de compilacion multiplataforma
5. Estructura de proyecto KMP generada
6. Sistema de bloques adaptado a KMP
7. Gestion de dependencias multiplataforma
8. Targets de compilacion soportados
9. UI multiplataforma con Compose Multiplatform
10. Capa expect/actual
11. Preview de plataformas en el IDE
12. Pipeline de build y empaquetado
13. Migracion de proyectos existentes
14. Roadmap funcional por fases
15. Limitaciones honestas y workarounds

## 1) Vision general y alcance real

### Scope realista

- Si: commonMain para logica de negocio compartida.
- Si: androidMain con Compose Android.
- Si: desktopMain con Compose Desktop sobre JVM.
- Si: wasmJs para web (idealmente compilado en servidor/CI si el host movil es limitado).
- Si: generacion automatica de expect/actual desde bloques.
- Si: ViewModels y casos de uso compartidos entre plataformas.
- Si: pruebas compartidas en commonTest.
- Parcial: iosMain (escritura y validacion de codigo KMP, generacion de klib).
- No: generacion de IPA final en Android (requiere macOS + Xcode).

### Propuesta de valor

- Sin KMP: codigo Android only.
- Con KMP: commonMain reutilizable para Android, Desktop, Web e iOS.
- Menos duplicacion de logica por plataforma.
- Salidas de build multi-artefacto: APK, JAR desktop, WASM web y KLIB para iOS.
- Transicion de generacion Java-only a Kotlin idiomatico + expect/actual.

## 2) Encaje de KMP en Sketchware

Arquitectura conceptual:

- commonMain: modelos, repositorios, casos de uso, ViewModels, contratos expect.
- androidMain: actual + Compose Android + SDK Android.
- desktopMain: actual + Compose Desktop + APIs JVM.
- wasmJsMain: UI y runtime web.
- iosMain: actual iOS y bridge con toolchain de Apple (compilacion final fuera de Android).

Experiencia en Sketchware:

- El usuario sigue trabajando con bloques y/o codigo.
- El generador produce source sets KMP, contratos expect y actuals por target habilitado.
- El IDE muestra compatibilidad por target y recomendaciones de migracion.

## 3) Arquitectura del sistema KMP en Sketchware Pro

Nuevos componentes propuestos:

- kmp-engine
  - KmpProjectModel
  - KmpCompilerOrchestrator
  - ExpectActualGenerator
  - KmpGradleScriptBuilder
- kmp-blocks
  - PlatformAwareBlock
  - CommonBlock
  - PlatformSpecificBlock
  - ExpectActualBlockPair
- kmp-compiler
  - KotlinNativeCompiler
  - WasmJsCompiler
  - JvmCompiler
  - CompilationCache
- kmp-ui
  - TargetSelectorPanel
  - PlatformPreviewPanel
  - ExpectActualInspector
  - DependencyCompatChecker

Modelo KMP base:

- Proyecto con targets habilitados, versiones Kotlin/Compose MP, grafo de dependencias y jerarquia de source sets.
- Soporte inicial de targets: Android, Desktop (JVM), WasmJs, iOS (Arm64/Simulator/X64) y LinuxX64 experimental.

## 4) Motor de compilacion multiplataforma

### Orquestacion

- compileCommon primero.
- Si common falla, cortar build multi-target.
- Compilar targets en paralelo cuando aplique.

### Flujos

- Android: Kotlin -> class -> dex -> APK.
- Desktop: Kotlin -> class -> JAR ejecutable.
- Web: Kotlin/Wasm -> wasm/js + bundle web.
- iOS: generar KLIB y script de completado para Mac/Xcode.

### Tiempos estimados (orientativos)

- Android incremental: 1-3s.
- Android full: 5-10s.
- Desktop: 8-15s.
- Web wasm: 20-40s.
- iOS KLIB: 30-60s.
- iOS IPA: fuera de host Android.

## 5) Estructura de proyecto KMP generada

Estructura objetivo:

- Raiz con build.gradle.kts, settings.gradle.kts, gradle.properties.
- Modulo shared con commonMain/commonTest y source sets por plataforma.
- Modulos por app contenedora:
  - androidApp
  - desktopApp
  - wasmJsApp
  - iosApp

Recomendacion de generacion:

- Mantener plantilla gradle parametrizable por placeholders SKETCHWARE_*_DEPS.
- Aplicar version catalog para controlar Kotlin/Compose/Ktor/coroutines/serialization y variantes por target.

## 6) Sistema de bloques adaptado a KMP

### Alcances de bloque

Se propone scope por bloque:

- COMMON
- ANDROID_ONLY
- DESKTOP_ONLY
- IOS_ONLY
- JVM
- NATIVE
- ALL_EXCEPT_IOS

### Comportamiento del editor

- Selector de target activo en el editor visual.
- Bloques con color/insignia por alcance.
- Avisos de incompatibilidad en tiempo real.
- Sugerencias de alternativa cuando una API no existe en un target.

### Generacion expect/actual

- Si un bloque comun usa API de plataforma, generar expect en commonMain.
- Generar actual por target habilitado.
- Si no hay implementacion definida, stub con TODO y diagnostico claro.

## 7) Gestion de dependencias multiplataforma

### Resolver KMP-aware

- Verificar soporte KMP real de coordenadas Maven.
- Si es multiplataforma, enrutar a commonMain.
- Si es platform-only, acotar por source set y ofrecer alternativa.

### Ejemplos de reemplazo sugerido

- Retrofit -> Ktor
- Room -> SQLDelight
- Gson -> kotlinx.serialization
- RxJava -> Coroutines/Flow
- Firebase oficial -> wrappers KMP (por ejemplo GitLive donde aplique)

### UX de dependencias

- Panel con matriz de compatibilidad por target.
- Alertas de librerias Android-only en proyectos KMP.
- Boton de migracion guiada a alternativa.

## 8) Targets de compilacion soportados

Matriz funcional resumida:

- Android: build y ejecutable final desde host Android.
- Desktop: build y JAR final desde host Android.
- Web WASM: build y artefactos web desde host Android (o CI para estabilidad).
- iOS: generar KLIB desde host Android; IPA final requiere Mac.

## 9) UI multiplataforma con Compose Multiplatform

Estrategia:

- Generar pantallas Compose desde bloques para commonMain cuando sea posible.
- Mantener componentes esperados por plataforma via expect/actual composable.
- Permitir variaciones por target sin duplicar logica de estado.

Objetivo:

- Compartir estado, validaciones y casos de uso.
- Personalizar integraciones de plataforma (share, notificaciones, etc.) con actuals.

## 10) Capa expect/actual

Motor propuesto:

- Detector de codigo no portable en commonMain (patrones Android-only, etc.).
- Catalogo de templates reutilizables (KeyValueStore, Logger, Clock, networking wrappers).
- Autofix para extraer interfaz expect y repartir actuals por source set.

## 11) Preview de plataformas en el IDE

Capacidades:

- Panel de preview multi-target en paralelo.
- Vista simulada por tamano/plataforma.
- Inspector de incompatibilidades en tiempo real.

Nota tecnica:

- Donde no haya renderer nativo disponible en host Android, usar simulacion o snapshot remoto.

## 12) Pipeline de build y empaquetado

Task graph base:

1. compileCommonMain metadata.
2. build Android y package APK.
3. build Desktop y package JAR.
4. build wasmJs y bundle web.
5. compile shared para iOS KLIB.

Build manager:

- Sincronizar dependencias.
- Generar codigo (expect/actual/resources).
- Compilar por target en paralelo.
- Empaquetar artefactos.
- Emitir reporte con duracion, exito y errores por target.

## 13) Migracion de proyectos existentes

### Analizador de compatibilidad

- Detectar bloques Android-only y tasa de automigracion.
- Evaluar dependencias incompatibles y sugerir reemplazos.
- Estimar esfuerzo manual remanente.

### Asistente por pasos

1. Analisis del proyecto.
2. Seleccion de targets.
3. Migracion de dependencias.
4. Reestructuracion de codigo/source sets.
5. Build de verificacion + tests compartidos.

## 14) Roadmap funcional por fases

### Fase 1 (Semanas 1-8): Infraestructura KMP

- Modelo KmpProject y serializacion.
- Builder de scripts Gradle KMP.
- Compilacion JVM para Android/Desktop.
- Cache de compilacion y runner de commonTest.

### Fase 2 (Semanas 9-16): Editor KMP-aware

- Selector de target.
- Bloques con scope y validaciones.
- Generador expect/actual.
- Resolver de dependencias KMP con sugerencias.

### Fase 3 (Semanas 17-24): Web y Desktop completos

- Integracion Kotlin/WASM.
- Bundling web.
- Packaging desktop.
- Generador Compose desde bloques.

### Fase 4 (Semanas 25-32): iOS y migracion

- KLIB iOS + export para Mac.
- Generador de proyecto Xcode base.
- Asistente de migracion automatizada.
- Pulido de UX, errores y performance.

## 15) Limitaciones honestas y workarounds

- IPA requiere Mac/Xcode: generar KLIB en Sketchware y completar en Mac/CI.
- Kotlin/Native es costoso: usar CI para builds pesados.
- Librerias Android-only sin alternativa: aislar en androidMain con expect/actual.
- Previews completos de escritorio en Android: usar simulacion/snapshots.
- Dependencias Google/Firebase no siempre KMP-first: usar wrappers cuando exista soporte real.

Checklist de viabilidad:

- Verde: logica separada de UI, stack estandar (HTTP/JSON/DB), bajo acoplamiento Android-only.
- Amarillo: uso de Room/Firebase/Maps o mezcla Java-Kotlin legacy.
- Rojo: dependencia profunda de APIs Android exclusivas, JNI/NDK critico, accesibilidad Android-only.

---

Este plan esta disenado para implementacion incremental. La Fase 1 ya entrega valor practico al habilitar una base KMP funcional para Android y Desktop, con ruta clara a Web e iOS.
