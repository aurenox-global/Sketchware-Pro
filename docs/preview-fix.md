# La vista previa de diseños vuelve a pintar (v7.0.8.2)

Fecha: 2026-09-23 · Versión: **v7.0.8.2** (versionCode 164) · Repo: `Sketchware-Pro-main`

Release: <https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.8.2>

Arreglo del **centinela `0xffffff`** en la vista previa de diseños (layouts) hechos con elementos View: el IDE
aplicaba como color real el valor con el que su propio modelo de datos dice "aquí no hay color elegido", así que la
zona del diseño salía vacía (en blanco o en negro según el tema) mientras los widgets que se pintan solos y el HTML
del WebView seguían viéndose.

## 1. El síntoma

En un mismo layout (`LinearLayout` raíz + `Button` + `WebView`), la vista previa mostraba:

- la zona del diseño **vacía**: blanca en tema claro, negra en tema oscuro;
- **sí** se veían los widgets que se pintan por sí mismos (`SeekBar`, `Switch`, iconos) y el **HTML del WebView**
  (que nunca falló), en claro y en oscuro.

Eso es exactamente lo que reportaba el usuario: *"si el diseño tiene icono o un Switch, esos sí se ven, pero el resto
de la zona del diseño no"*. La vista previa de HTML/WebView era el caso que siempre funcionó.

| Antes (v7.0.8.1) | Después (v7.0.8.2) |
| --- | --- |
| ![antes](assets/preview-before.png) | ![después](assets/preview-after.png) |

Antes, la zona del `Button` (`[0,406][242,538]`) era **255,255,255 al 100 %** — 31.944 píxeles del mismo blanco, sin
un solo glifo —, aunque `uiautomator dump` sí encontraba el nodo con sus bounds reales. No era un fallo de inflado ni
de tamaño: era un fallo de **pintado** (un rectángulo invisible). Después se ve el botón con su fondo gris y su texto,
y el HTML del WebView sigue renderizado.

## 2. La causa raíz: el centinela `0xffffff`

El modelo de datos del IDE usa `0xffffff` para decir "el usuario no ha elegido color":

- `com/besome/sketch/beans/TextBean.java`: `textColor = 0xffffff;` y `hintColor = 0xffffff;`
- `com/besome/sketch/beans/LayoutBean.java`: `backgroundColor = 0xffffff;`
- `a/a/a/Ox.java` (el generador de XML del proyecto) **solo escribe** `android:textColor`, `android:textColorHint` y
  `android:background` cuando el valor es **distinto** de `0xffffff`; es decir, un layout sin colores propios llega al
  parser con el centinela intacto.

`LayoutPreviewActivity.applyBeanAppearance()` comprobaba `!= 0` y aplicaba el centinela como color real. Como `int`,
`0xffffff` es **`0x00FFFFFF`**: **alfa 0**.

1. `setTextColor(0x00FFFFFF)` → texto **totalmente transparente**.
2. `setBackgroundColor(0x00FFFFFF)` → fondo transparente que además **borra el fondo que el tema daría al widget** (un
   `Button` pinta su `colorPrimary`; con esto pasa a pintar nada).

Un `LinearLayout` + `Button` quedaba como un rectángulo invisible. Y el agravante del "blanco o negro según el tema":
`ViewPane.initialize()` forzaba el lienzo a **blanco fijo** para proyectos sin Material3, mientras los widgets se inflan
con el contexto de la Activity, que **sí** sigue el tema claro/oscuro del IDE → contraste roto e invisibilidad para
cualquier texto que venga del tema.

Bugs secundarios encontrados en la misma ruta (todos producían pantallas mudas):

- `createRealView()` devolvía `null` si una clase no se podía instanciar → la vista desaparecía **en silencio**;
- la raíz inflada se colgaba siempre con alto `WRAP_CONTENT` aunque el XML dijera `match_parent` → un layout con hijos
  con peso podía colapsar a 0 px y parecer vacío;
- el único aviso de fallo era el panel `debug_status`, que no distinguía éxito de degradación parcial.

## 3. El arreglo, fichero por fichero (3 ficheros, +143/−15)

**`LayoutPreviewActivity.java`**

- Regla central, la misma que usa el generador de XML (`Ox`):
  `isColorNotSet(color)` → `true` si el color es `0`, `0xffffff` o tiene **alfa 0**. Entonces **no se aplica** y decide
  el tema del widget.
- `applyBeanAppearance()`: `textColor`, `hintColor` y `backgroundColor` pasan por `isColorNotSet()` (antes `!= 0`).
- `applyInjectAttributes()`: igual para `android:background`, `android:backgroundTint` y `android:textColor`.
- `createRealView()`: si el IDE no puede instanciar una clase, en vez de un hueco mudo se coloca un **marcador rojo
  visible** (`⚠ NombreClase: no disponible en el editor`) y se avisa con `Preview PARCIAL · … · no disponibles: …` en
  barra roja + `Log.w`.
- `debug()` / `debugWarning()`: barra siempre visible con el motivo (INFO negro / WARN rojo) y `Log.i` / `Log.w` con el
  tag `LayoutPreview`, además de toast si fallan los dos caminos.

**`ViewPane.java`**

- En modo vista previa (`isPreviewMode = true`) el lienzo usa el `colorSurface` del **mismo tema** con el que se inflan
  las vistas, en vez del blanco fijo. El editor de diseño (`isPreviewMode = false`) mantiene exactamente su aspecto
  anterior.
- La raíz inflada usa las dimensiones declaradas en el XML (`rootLayoutParams()`), no un `WRAP_CONTENT` forzado.

**`res/layout/activity_layout_preview.xml`**

- El contenedor de la vista previa pasa a `match_parent` (antes `wrap_content`), coherente con las dimensiones reales
  del layout.

## 4. Verificación (recompilar, instalar y medir la captura)

- Build: `./gradlew assembleDebug` → **BUILD SUCCESSFUL**; instalado en un emulador **arm64 API 34** (`adb root`).
- Mismos pasos en el IDE (editor de diseño → *Live preview*) y el mismo layout, en tema claro y oscuro.
- Medición de píxeles sobre las capturas, en la zona del botón:
  - **antes** (claro y oscuro): `(255,255,255) × 31.944 px`, **0 glifos**;
  - **después** (claro): fondo `(214,215,215)` + glifos oscuros `(27,27,31)`; **después** (oscuro): fondo `(90,89,91)`
    + glifos claros.
- Regresión de HTML/WebView: sigue funcionando end-to-end. Log crudo con el arreglo:
  `I LayoutPreview: info: Preview OK · vistas: 3 · WebViews: 1 [linear1/LinearLayout/] [button1/Button/Button]`.

## 5. Pendientes honestos (lo que NO está verificado)

- **Componentes Material en pantalla**: `MaterialButton` / `CardView` no se pudieron colocar por automatización
  (`adb input draganddrop`), así que la ruta está cubierta con la degradación visible (marcador rojo + aviso) pero
  **sin captura**. La clase sí está en el classpath del IDE.
- **Proyectos con Material3 activo**: no probado (el proyecto de prueba no usa M3).
- **Temas y recursos personalizados del proyecto**: no probados; los `?attr/…` se resuelven con los atributos del
  **IDE**, no del proyecto.
- **Móvil físico**: nada probado fuera del emulador arm64 API 34.

---

## 6. Ronda 2 (v7.0.8.3)

Fecha: 2026-09-23 · Versión: **v7.0.8.3** (versionCode 165) · Release:
<https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.8.3>

Los defectos y el arreglo de la ronda 1 están descritos arriba (secciones 1–5) y **no se repiten aquí**. Esta ronda
cierra tres cabos que seguían abiertos en la misma ruta de la vista previa. Todo medido **píxel a píxel** sobre
capturas de un emulador arm64 API 34, en tema claro y oscuro.

### 6.1 Los recursos de color del proyecto no se resolvían

Para un fondo escrito como `@color/…` o `?attr/…`, `ViewBeanFactory.applyBackground()` guarda el **nombre real** en
`backgroundResColor` y deja `backgroundColor = 0xFFFFFFFF` (blanco opaco) como marcador de «pendiente de resolver»;
la vista previa solo miraba `backgroundColor` e **ignoraba** `backgroundResColor`. Resultado: un fondo definido en el
`colors.xml` del proyecto se veía **blanco**. Ahora se resuelven los `@color/…` del proyecto y los `?attr/…`
referenciados por el layout.

**Evidencia:** caja con `@color/color_proyecto` (`#0000FF`) → **azul puro `(0,0,255)`** (antes: `(255,255,255)`).

### 6.2 Cuelgue al abrir la vista previa en frío

En arranque en frío (sin pasar por el editor de diseño) la Activity moría con un `NullPointerException` y dejaba una
**pantalla muerta sin ningún mensaje**: `ColorsEditorManager` usa `import static
com.besome.sketch.design.DesignActivity.sc_id;` (el proyecto «en curso» global) y con el proceso recién arrancado ese
campo es `null` → `jC.c(null)` → NPE, antes de que hubiera ninguna salvaguarda. Arreglado **fijando el proyecto
(`sc_id`) antes de inicializar** la preview y con un `try/catch` que muestra un **error visible** en pantalla en vez de
una pantalla muda.

### 6.3 La vista previa honra el extra `xml`

Si la vista previa se abre desde el **editor de XML de vistas** del IDE, ahora previsualiza **exactamente lo que estás
editando**, sin necesidad de guardar. (Es además la vía con la que se inyecta XML exacto por
`am start --es xml "<layout…>"` para estas pruebas.)

### 6.4 Extras verificados en la misma ronda

- `com.google.android.material.button.MaterialButton` **sí está en el classpath** del IDE y **se ve**: índigo
  `(68,94,145)` en claro, azul claro `(173,198,255)` en oscuro.
- `TextView`/`Button` **sin atributos** ya son **legibles en ambos temas**: `(68,71,79)` sobre `(250,249,253)` en
  claro; `(196,198,208)` sobre `(18,19,22)` en oscuro.
- **HTML/WebView intacto**: `WebView webview1 fallback html -> …/files/assets/index.html`, zona verde `(0,187,85)`.
- Las **vistas no instanciables** dan marcador rojo + aviso rojo «Preview PARCIAL · … · no disponibles: …».

### 6.5 Evidencia gráfica

![Vista previa en claro: raíz magenta, caja azul del color del proyecto, MaterialButton, HTML/WebView y texto por defecto legible](assets/preview-colors.png)

Medidas sobre esta captura (`44_test_light_B_magenta.png`, en `preview-evidence/`):

| Comprobación | Medida |
| --- | --- |
| Raíz `android:background="#FF00FF"` | magenta puro `(255,0,255)` |
| Fondo `@color/color_proyecto` (del proyecto) | azul puro `(0,0,255)` |
| `MaterialButton` | visible (índigo en claro) |
| Texto por defecto, claro / oscuro | `(68,71,79)` sobre `(250,249,253)` / `(196,198,208)` sobre `(18,19,22)` |
| HTML/WebView | verde `(0,187,85)`, intacto |

### 6.6 Pendientes honestos de la ronda 2

- Material **más allá de `MaterialButton`**: `CardView`, `TextInputLayout`, `BottomNavigationView`, `TabLayout`…
  **sin verificar en pantalla**.
- Proyecto con **Material3 activo**: no probado.
- **Temas y estilos personalizados** del proyecto: los `@color/…` del proyecto sí se resuelven (verificado con el
  azul); la fidelidad de estilos propios (`styles.xml`, `my_sc_theme`) **no** está verificada.
- **Móvil físico**: no probado (todo en emulador arm64 API 34).
- **Regresión formal del editor de diseño**: solo comprobado que su camino (`isPreviewMode=false`) queda intacto.

---

## 7. Ronda 3 (v7.0.9.0)

Fecha: 2026-09-23 · Versión: **v7.0.9.0** (versionCode 166) · Release:
<https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.9.0>

Cuatro síntomas distintos, **cuatro causas raíz distintas**, todas reproducidas y medidas **píxel a píxel** sobre un
emulador arm64 API 34. «Antes» es el build de la ronda 2 (v7.0.8.2/164).

### 7.1 Color de texto desde la paleta Material (`?colorPrimary`)

El selector de color del editor guarda el valor como `"?" + attr` (p. ej. `?colorPrimary`) y escribe
`android:textColor="?colorPrimary"`; la vista previa solo entendía la forma `?attr/...`, así que **no resolvía nada y
el texto caía al color por defecto del tema**. Ahora `resolveColor()` acepta cualquier expresión que empiece por `?`
(`?colorPrimary`, `?attr/colorPrimary`, `?android:attr/colorPrimary`, `?android:colorPrimary`) y la resuelve contra el
tema del IDE. Medido con un texto `PALETA` = `?colorPrimary` y un texto control `CONTROL` = `#FF0000`:

| | texto `?colorPrimary` | texto control `#FF0000` |
| --- | --- | --- |
| Antes | `(68,71,79)` — color por defecto del tema | `(255,0,0)` |
| Después | **`(68,94,145)` — `colorPrimary` del tema** | `(255,0,0)`, intacto |

### 7.2 Layouts / lineales que se pintaban blancos

Mismo fallo de resolución, pero por el lado del fondo: `ViewBeanFactory.applyBackground()` guarda el valor de respaldo
`0xFFFFFFFF` (el marcador «pendiente de resolver» del propio IDE) y, al no resolverlo, la preview pintaba el lineal
**blanco opaco**. Sobre un lienzo claro, el lineal desaparecía. Medido en tres bandas: un
`android:background="?colorPrimary"` pasó de **`(255,255,255)`** a **`(68,94,145)`**, mientras un
`@color/...` del proyecto (`(0,0,255)`) y un `@drawable/...` con forma y borde no cambiaron. Ahora
`applyBeanBackground()` no cae al color literal cuando el recurso no se pudo resolver: deja el fondo del tema y lo
reporta.

### 7.3 Estilos de texto: cursiva, negrita+cursiva, monoespaciada y fuentes del proyecto

Solo se aplicaba `textType == 1` (negrita); cursiva (`2`) y negrita+cursiva (`3`) se ignoraban, y no existía rama para
`android:fontFamily`. Ahora `applyTextTypeface()` aplica los tres estilos, las familias del sistema (`serif`,
`monospace`, …) y las **fuentes TTF del proyecto** (`@font/nombre` → `files/resource/font/…`, `.ttf/.otf/.ttc`). Si la
fuente no existe, se pinta el texto por defecto **con aviso** (`no disponibles: @font/no_existe`). Medido a 30 sp:

| texto | Antes | Después |
| --- | --- | --- |
| `textStyle="italic"` | recto | **cursiva** |
| `textStyle="bold\|italic"` | recto, sin negrita | **negrita + cursiva** |
| `fontFamily="monospace"` | proporcional | **monoespaciado** |
| `fontFamily="@font/fuente_proyecto"` (TTF real) | ignorada | **fuente del proyecto aplicada** |

### 7.4 Imágenes

El resolvedor solo miraba `files/resource/drawable/<nombre>.{xml,png,jpg}` y, si no estaba, devolvía `null` **sin
marcador** (hueco mudo). Ahora busca en **todos** los directorios `drawable*` (recursivo, con carpetas de densidad y
subcarpetas), en `files/assets`, en el resto de `files/` y, como último recurso, en los drawables del propio IDE
(`default_image`, `ic_mtrl_*`). Extensiones: `.xml .png .jpg .jpeg .webp .gif .bmp`. Se añaden `app:srcCompat`, las
rutas `file://…`, `assets/…`, `@asset/…`, `@android:drawable/…` y los **vectores** `.xml`, que se dibujan con un
`PathParser` propio (`VectorPathDrawable`; `VectorDrawableCompat` no puede inflar desde una cadena). Lo que no se
puede resolver muestra un **marcador rojo con borde** y el motivo en la barra de estado.

Medido con ocho `ImageView` de 50 dp, cada uno con una variante distinta (bandas de 137 px): antes solo se veían las
dos primeras (`drawable/v_png.png` y `V_MAYUS.PNG`); después se ven también `.webp`, `.jpeg`, `drawable-xhdpi/`,
subcarpeta, fichero de `files/assets`, `app:srcCompat`, vector, `.gif` y el `default_image` del IDE, y `@drawable/noexiste`
da marcador rojo `(176,0,32)` + aviso. Este es el caso que ilustran las capturas:

| Antes (v7.0.8.2) | Después (v7.0.9.0) |
| --- | --- |
| ![antes](assets/preview-r3-before.png) | ![después](assets/preview-r3-after.png) |

### 7.5 Extra encontrado por el camino

`applyInjectAttributes()` comparaba los nombres de atributo **con prefijo** (`"android:textColor"`) mientras
`InjectAttributeHandler` devuelve **nombres locales** (`"textColor"`): ninguna rama del `switch` coincidía nunca, así
que *todos* los atributos «extra» (`fontFamily`, `srcCompat`, `alpha`, `gravity`, paddings, `maxLines`…) se
descartaban en silencio. Ahora se normalizan, así que funcionan. Regresión comprobada: layout real del proyecto
(claro/oscuro), HTML/WebView, `@color/` del proyecto, hex explícitos y `MaterialButton` siguen bien.

### 7.6 Pendientes honestos de la ronda 3

- `<selector>`, `<ripple>`, `<layer-list>`, `<inset>`, `<clip>`: se aproximan dibujando su **última forma** (la preview
  no tiene estados pressed/disabled que reproducir).
- **Vectores complejos**: solo `<path>` con `pathData` + `fillColor`/`strokeColor`; un vector con `<group>`,
  `translateX/Y`, `scale`, `clip-path` o degradados puede salir desplazado.
- Los **`.9.png`** se leen como bitmap normal, **sin parches**.
- Los `?atributo` se resuelven con el tema del **IDE**, no con el del proyecto; **M3 night variants no probado**.
- Un `@color/` que no esté en `files/resource/values/colors.xml` da aviso y no se aplica.
- **Nada probado en móvil físico**: todo en emulador arm64 API 34.

El detalle completo (método, XML de los casos, evidencia cruda) está en `preview-r3.md` y en
`preview-evidence/r3/`.

---

## 8. Ronda 4 (v7.0.10.0)

Fecha: 2026-09-24 · Versión: **v7.0.10.0** (versionCode 167) · Release:
<https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.10.0>

Las rondas 1-3 están arriba (secciones 1-7) y **no se repiten aquí**. Esta ronda arregla un fallo real de resolución
de recursos, hace útil el aviso de «vista previa parcial» y corrige una lectura equivocada del mensaje. Todo medido
**píxel a píxel** sobre capturas de un emulador arm64 API 34.

### 8.1 El «32» no eran 32 vistas rotas

El reporte era `Preview PARCIAL · vistas: 32 · no disponibles: @drawable/ic_tune_white`. Son **tres cosas distintas**
y solo una era un fallo de resolución de recursos:

- el **32** son las vistas que **SÍ se dibujaron**, no las que fallaron. El mensaje juntaba `vistas: 32` con
  `no disponibles: …` y se leía como «32 vistas no disponibles»; en la reproducción exacta (33 nodos en el XML = 32
  vistas creadas) el único problema real era **1 drawable**.
- la lista mezclaba en el mismo saco **clases no instanciables** y **recursos no resueltos**, sin motivo ni recuento.
- **`@drawable/ic_tune_white` no existe en ninguna fuente de este IDE** (ver 8.3).

Ahora el aviso es un **resumen corto y agrupado** —`Preview PARCIAL: <N> recursos no encontrados · <M> vistas no
instanciables` (singular/plural correcto)— y **pulsando la barra** se abre un **diálogo de detalle** agrupado por causa
(colores, atributos de tema, drawables/imágenes, fuentes, vistas no instanciables), con recuento por grupo, el motivo
de cada elemento, **dónde se buscó** (`buscado en: N carpetas drawable* del proyecto, assets del proyecto, M recursos
de librerías, recursos del IDE`) y un **tope de 12 líneas por grupo** (`…y N mas (ver log LayoutPreview)`). El detalle
completo sigue en logcat (tag `LayoutPreview`). Medido con un XML mixto (color, atributo de tema, drawable y fuente
rotos + 1 vista no instanciable):

| | Barra de estado |
| --- | --- |
| Antes | `Preview PARCIAL · vistas: 6 · no disponibles: @font/no_existe_font, com.example.noexiste.OtroWidget, @color/no_existe_color (no esta en …), @drawable/no_existe_img, ?attr/noExisteEsteAttr (…)` (una línea larguísima) |
| Después | `Preview PARCIAL: 4 recursos no encontrados · 1 vista no instanciable` |

### 8.2 El fallo real: la vista previa no miraba las librerías del proyecto

`ResourceCompiler` enlaza los recursos de las librerías con `aapt2` (`-R`): los AAR locales que
`DependencyResolver` descomprime en `.sketchware/libs/local_libs/<lib>/` (paths `resPath` del JSON
`files/local_library`, gestionados por `ManageLocalLibrary`) y las librerías integradas del IDE ya extraídas
(`BuiltInLibraries.getLibraryResourcesPath()` → `<filesDir>/libs/libs/<lib>/res`). **La vista previa no miraba
ninguna de las dos.**

`ProjectResourceResolver` ahora indexa y busca (recursivo, solo `drawable*`/`mipmap*` dentro de `res/`) en el JSON
`files/local_library` (`resPath`/`assetsPath` + `.sketchware/libs/local_libs/<name>/{res,assets}`), en la ruta heredada
`<proyecto>/files/library/res` (+ `library/assets`) y en las librerías integradas ya extraídas.

Probado con un **fixture de librería local** (`testlib` con
`.sketchware/libs/local_libs/testlib/res/drawable/ic_tune_white.xml`, registrada en el JSON del proyecto):

| caso | Antes | Después |
| --- | --- | --- |
| `@drawable/ic_tune_white` **de la librería** | marcador rojo `(176,0,32)` — 2.076 px | **icono dibujado** `(0,200,83)` — **6.174 px** |
| `@drawable/ic_tune_white_24dp` (librería, `drawable-xhdpi`) | marcador rojo | **icono dibujado** (rojo del propio vector) |
| `@drawable/ic_tune_24` (recurso del IDE) | visible | visible |
| `@android:drawable/ic_menu_preferences` (framework) | visible | visible |
| `@mipmap/ic_launcher` | marcador rojo | marcador rojo (no resoluble aquí, ver 8.6) |
| `@drawable/noexiste_zzz` (no está) | marcador rojo | marcador rojo + motivo |

Con el fixture, **el caso exacto del usuario pasa de «PARCIAL» a `Preview OK · vistas: 32`**:

```
I LayoutPreview: info: recursos de librerias indexados: 1
I LayoutPreview: info: Preview OK · vistas: 32 · WebViews: 0 [linear1/LinearLayout/] [img1/ImageView/]
```

| Antes (v7.0.9.0) | Después (v7.0.10.0) |
| --- | --- |
| ![antes](assets/preview-r4-before.png) | ![después](assets/preview-r4-after.png) |

En el «antes» (izquierda) los dos drawables de la librería salen con **marcador rojo** y la barra enumera todos los
nombres en una línea; en el «después» (derecha) el drawable de la librería ya **se dibuja** y la barra es un resumen
corto. `@mipmap/ic_launcher` y el nombre inexistente siguen en rojo, como debe ser.

### 8.3 `@drawable/ic_tune_white`: de dónde sale y qué se puede arreglar

| Fuente | ¿Está `ic_tune_white`? | Evidencia |
| --- | --- | --- |
| `res/drawable*` del proyecto | No | `ls` del proyecto en el dispositivo |
| Recursos del IDE (APK debug) | **No** | `unzip -l …apk \| grep -i tune` → solo `ic_mtrl_tune.xml` y `ic_tune_24.xml` |
| AAR de Material (`material-1.13.0`) | No | 84 entradas `res/drawable*`, ninguna `*tune*` |
| `OldResourceIdMapper` (ids viejos de Sketchware) | El id viejo `2131166544` → `R.drawable.ic_mtrl_tune` | `app/src/main/java/mod/jbk/util/OldResourceIdMapper.java:1297` |

**Conclusión honesta:** `ic_tune_white` es un **nombre heredado** (la vieja Sketchware usaba `ic_*_white`; el id
viejo `2131166544` se mapea hoy a `ic_mtrl_tune`), **no un drawable de este IDE**, así que ese nombre concreto **no se
puede dibujar**. Ahora la barra lo dice claro y se marca **solo el recurso**, sin marcar la vista entera. Lo que **sí**
era un fallo real y queda arreglado es 8.2: si el nombre existe en una **librería del proyecto**, ahora se resuelve.

### 8.4 Extras de la ronda

- Las **fuentes rotas** (`@font/…`) pasan a la categoría «recurso»: ya **no cuentan como vistas** no instanciables.
- La **barra de aviso respeta el inset de la barra de navegación**: antes el texto quedaba cortado por abajo (parte
del «aviso ilegible»).

### 8.5 Regresión (las rondas 1-3 siguen bien)

| Comprobación | Resultado medido | Captura |
| --- | --- | --- |
| Color de texto de la paleta (`?colorPrimary`) | `(68,94,145)` 4.665 px + control rojo `(255,0,0)` 6.025 px | `reg_R4_B1_color_attr.png` |
| Fondos: `?colorPrimary` / `@color/proyecto` / shape con borde | `(68,94,145)` 356.400 px · `(0,0,255)` 356.400 px · `(255,152,0)` + borde negro | `reg_R4_B3_fondos.png` |
| Estilos de texto (cursiva, negrita+cursiva, monoespaciada) | los tres correctos | `reg_R4_B2_estilo.png` |
| Imágenes (png/webp/jpeg/`drawable-xhdpi`/subcarpeta/assets/vector/gif) | 10 vistas dibujadas, todas las bandas de color visibles | `reg_R4_B6b_img_variantes.png` |
| `MaterialButton` + `WebView` (HTML) | HTML `(0,187,85)` 698.570 px + `MaterialButton` índigo | `reg_R4_C_material_regresion.png` |
| Layout real del proyecto (sin `--es xml`, con WebView) | `Preview OK · vistas: 3 · WebViews: 1` | `reg_R4_proyecto_real.png` |

Todas salen con **`Preview OK`**, sin avisos.

### 8.6 Pendientes honestos de la ronda 4

- **`@drawable/ic_tune_white` sigue sin poder dibujarse** en este entorno: no existe en ninguna fuente disponible
  (proyecto, librerías declaradas, IDE, Material). No se ha «inventado»: si el usuario lo tiene en una **librería
  local de su proyecto**, ahora **sí** se resuelve (probado con fixture).
- **Librerías integradas no extraídas**: si el IDE nunca ha compilado en ese dispositivo, `<filesDir>/libs/libs/` no
  existe y sus recursos (material, firebase…) tampoco se pueden leer. **No** se ha añadido una ruta que lea los zips
  de `assets/libs/libs.zip` del propio APK (copiar 26 MB a caché en cada preview no compensa); tras la primera
  compilación del proyecto esos `res/` se extraen y entonces sí se resuelven.
- **`@mipmap/ic_launcher`**: vive en el proyecto de compilación generado
  (`.sketchware/mysc/<scId>/app/src/main/res/mipmap-*`), que no es una fuente de recursos de diseño (podría mostrar
  recursos obsoletos de la última compilación); no se ha añadido.
- **Nombres heredados** de Sketchware que el IDE ya no incluye: **no hay renombrado automático** en la preview (existe
  `OldResourceIdMapper`, pero solo se usa para el selector de icono de app; un alias por nombre sería adivinar).
- Siguen en pie los pendientes de las rondas 1-3 (selector/ripple/layer-list aproximados, vectores con `<group>`/
  degradados, `.9.png` sin parches, `?atributo` con el tema del IDE, Material3 sin probar, nada en móvil físico).

El detalle completo (método, XML de los casos, evidencia cruda) está en `preview-r4.md` y en `preview-evidence/r4/`.

## 9. Ronda 5 (v7.0.10.1)

### 9.1 Nombres heredados de iconos (`ic_tune_white` y familia)

El diseño real usa `@drawable/ic_tune_white`, un nombre heredado de una Sketchware vieja, y la vista previa lo marcaba
en rojo. El mensaje ya era correcto y se mantiene; lo nuevo es una **red de último recurso**: si el nombre heredado **se
puede mapear sin ambigüedad** a un icono que el IDE **sí** tiene, se dibuja ese icono y **el aviso lo explica** (nunca
se sustituye en silencio).

**Qué nombres usa de verdad el IDE (dato, no suposición).** Se volcaron los recursos del APK del IDE con
`aapt2 dump resources`: **2.382 nombres de drawable únicos**. `drawable/ic_tune_white` **no existe**; los equivalentes
actuales son **`ic_tune_24`** (convención del IDE) e **`ic_mtrl_tune`** (familia Material, 272 iconos `ic_mtrl_*`).

**Reglas implementadas** (en `ProjectResourceResolver.java`, más el aviso en `LayoutPreviewActivity.java`):

- **Normalización (solo para buscar, nunca para renombrar lo que pide el XML):** se quitan los prefijos `ic_`, `img_`,
  `icon_` y, repetidamente, los sufijos de color/estilo (`_white`, `_black`, `_dark`, `_light`, `_primary`, `_accent`,
  `_grey600`, `_holo_light`…) y de tamaño (`_24`, `_24dp`, `_48dp`, `_96dp`…). Guardas: base vacía, de 1 carácter o con
  caracteres raros → no se mapea nada.
- **Candidatos en dos pasos deterministas:** primero variantes estructurales por orden fijo
  (`ic_<base>`, `ic_<base>_24`, `ic_mtrl_<base>`, `ic_<base>_24dp`, `ic_<base>_48dp`…); después variantes de
  color/tamaño, que **solo** se usan si existe **exactamente una** — si hay dos o más, es ambiguo y **no se resuelve**.
- **Ultimo recurso:** el mapa solo se consulta después de que fallen proyecto, assets, librerías e IDE (los 44
  drawables `*_white`/`*_black` que el IDE **sí** conserva se resuelven por la vía normal).
- **Nada en silencio:** el nombre heredado resuelto no cuenta como "recurso no encontrado"; la barra añade
  `… · N nombre(s) heredado(s) resuelto(s)` (ámbar si no hay fallos reales), el diálogo de detalle tiene un **grupo
  propio** (`Nombres heredados resueltos -> icono actual` con pares `heredado -> real`) y queda en el log
  (`I LayoutPreview: info: nombre heredado resuelto: @drawable/ic_tune_white -> @drawable/ic_tune_24 [base: tune, variante estructural]`).

**Caso real medido** (`ic_tune_white` + un nombre inexistente):

| | Barra | Marcador rojo |
| --- | --- | --- |
| **ANTES** | `⚠ Preview PARCIAL: 2 recursos no encontrados` | **2 bloques** (2.080 px) |
| **DESPUÉS** | `⚠ Preview PARCIAL: 1 recurso no encontrado · 1 nombre heredado resuelto` | **1 bloque** (1.040 px) |

![Antes: dos marcadores rojos y la barra con 2 recursos no encontrados](assets/preview-r5-before.png)
![Después: ic_tune_white dibuja ic_tune_24 y solo queda un marcador rojo](assets/preview-r5-after.png)

En la captura *después*, la fila **A** (`ic_tune_white`, heredado) dibuja el icono *tune* del IDE, la fila **B**
(`ic_no_existe_xyz`) sigue con el marcador rojo, y las filas **C/D/E** (`ic_tune_24` exacto del IDE, `foto` del proyecto
y vector del proyecto) se dibujan igual que antes: la resolución normal queda intacta.

**Regresión**: `reg_R5_variants` mantiene el icono **de la librería** (verde, 3.240 px) y **no** lo sustituye; el
detalle agrupado de la ronda 4 (`4 recursos no encontrados · 1 vista no instanciable`) es idéntico; y los casos de
color/estilos/fondos/imágenes/`MaterialButton`+WebView/layout real siguen en `Preview OK`.

### 9.2 Pendientes honestos de la ronda 5

- Cubre los nombres heredados cuyo **concepto sigue existiendo** en el IDE con una base compatible: en la simulación
  contra los 2.382 drawables, si desaparecieran los 44 nombres `*_white`/`*_black` actuales las reglas reconstruirían
  **19** (≈43 %). El resto (`footprint_96_white`, `spades_96_white`, `bg_rectangle_black`…) **se queda en rojo**, que es
  lo correcto.
- **Typos y nombres inventados** (`ic_tune_whit`, `ic_hom_white`, `ic_no_existe_xyz`) siguen en **rojo**: no se adivina.
- Nombres con **más de una** variante de color/tamaño posible: ambiguo → **no se resuelve** (anotado en el log).
- `@mipmap/ic_launcher` y recursos generados por el compilador del proyecto: sin cambios (limitación de la ronda 4).
- El mapa es **por nombre**, no por id (`OldResourceIdMapper` sigue siendo solo del selector de icono de app).
- Todo probado en **emulador** arm64 API 34; nada en móvil físico ni con Material3 activado.
