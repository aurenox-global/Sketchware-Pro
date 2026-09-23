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
