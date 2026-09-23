package pro.sketchware.flutter

/**
 * Plantillas de texto del scaffold Flutter (carril B2).
 *
 * Todas las plantillas son deterministas y sin interpolacion de `$` del shell: se generan
 * con Kotlin puro para que el resultado sea reproducible byte a byte.
 */
object FlutterScaffoldTemplates {

    @JvmStatic
    fun pubspecYaml(projectName: String): String {
        val packageName = FlutterProjectDefaults.pubspecPackageName(projectName)
        val displayName = FlutterProjectDefaults.normalizeProjectName(projectName)

        return """
            # Pubspec generado por Sketchware-Pro (soporte Flutter experimental).
            #
            # Las dependencias que anadas aqui se resuelven EN EL DISPOSITIVO con `dart pub get`
            # (carril P). La PRIMERA resolucion necesita conexion a internet: los paquetes se
            # descargan a la cache privada de la app (<filesDir>/flutter-toolchain/pub-cache) y a
            # partir de ahi el proyecto compila sin red. Si no hay red, pub lo dice y el editor lo
            # avisa; el proyecto sigue compilando mientras no anadas dependencias nuevas.
            name: $packageName
            description: $displayName
            publish_to: "none"
            version: 1.0.0+1

            environment:
              sdk: '${FlutterProjectDefaults.DEFAULT_DART_SDK_CONSTRAINT}'
              flutter: '${FlutterProjectDefaults.DEFAULT_FLUTTER_SDK_CONSTRAINT}'

            dependencies:
              flutter:
                sdk: flutter

            dev_dependencies:
              flutter_test:
                sdk: flutter
              flutter_lints: ^4.0.0

            flutter:
              uses-material-design: true
              # Los assets declarados aqui entran en el manifiesto del bundle
              # (AssetManifest.json) y se copian a flutter_assets/ automaticamente.
              assets:
                - assets/
        """.trimIndent() + "\n"
    }

    @JvmStatic
    fun mainDart(): String {
        return """
            // Aplicacion Flutter minima generada por Sketchware-Pro.
            // Material 3 + contador. Todo el texto esta en espanol.
            import 'package:flutter/material.dart';

            void main() {
              runApp(const AplicacionSketchware());
            }

            class AplicacionSketchware extends StatelessWidget {
              const AplicacionSketchware({super.key});

              @override
              Widget build(BuildContext context) {
                return MaterialApp(
                  title: 'Aplicacion Flutter',
                  theme: ThemeData(
                    colorScheme: ColorScheme.fromSeed(seedColor: Colors.teal),
                    useMaterial3: true,
                  ),
                  home: const PantallaInicio(),
                );
              }
            }

            class PantallaInicio extends StatefulWidget {
              const PantallaInicio({super.key});

              @override
              State<PantallaInicio> createState() => _EstadoPantallaInicio();
            }

            class _EstadoPantallaInicio extends State<PantallaInicio> {
              int contador = 0;

              void incrementar() {
                setState(() {
                  contador = contador + 1;
                });
              }

              @override
              Widget build(BuildContext context) {
                return Scaffold(
                  appBar: AppBar(
                    title: const Text('Inicio'),
                    backgroundColor: Theme.of(context).colorScheme.inversePrimary,
                  ),
                  body: Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: <Widget>[
                        const Text('Has pulsado el boton esta cantidad de veces:'),
                        Text(
                          contador.toString(),
                          style: Theme.of(context).textTheme.headlineMedium,
                        ),
                      ],
                    ),
                  ),
                  floatingActionButton: FloatingActionButton(
                    onPressed: incrementar,
                    tooltip: 'Incrementar',
                    child: const Icon(Icons.add),
                  ),
                );
              }
            }
        """.trimIndent() + "\n"
    }

    @JvmStatic
    fun gitignore(): String {
        return """
            # Ficheros generados por Flutter/Dart
            .dart_tool/
            .flutter-plugins
            .flutter-plugins-dependencies
            .packages
            .pub-cache/
            .pub/
            build/
            /android/.gradle/
            /android/local.properties
            *.iml
            .idea/
        """.trimIndent() + "\n"
    }

    /** Placeholder para que `assets/` exista en el scaffold y `flutter` no falle al empaquetar. */
    @JvmStatic
    fun assetsReadme(): String {
        return """
            # assets

            Carpeta de recursos de la aplicacion Flutter (imagenes, fuentes, JSON...).

            Coloca aqui tus ficheros y declaralos en `pubspec.yaml` dentro de la seccion
            `flutter: assets:`. El scaffold ya declara la carpeta completa `assets/`.
        """.trimIndent() + "\n"
    }

    @JvmStatic
    fun androidManifest(packageName: String, projectName: String): String {
        val appLabel = FlutterProjectDefaults.normalizeProjectName(projectName)

        return """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- Plantilla de referencia generada por Sketchware-Pro (no se compila por si sola). -->
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application android:label="$appLabel">
                    <activity
                        android:name="io.flutter.embedding.android.FlutterActivity"
                        android:exported="true"
                        android:theme="@style/LaunchTheme"
                        android:configChanges="orientation|keyboardHidden|keyboard|screenSize|locale|layoutDirection|fontScale|screenLayout|density|uiMode"
                        android:hardwareAccelerated="true"
                        android:windowSoftInputMode="adjustResize">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                    <meta-data
                        android:name="flutterEmbedding"
                        android:value="2" />
                </application>
            </manifest>
        """.trimIndent() + "\n"
    }

    @JvmStatic
    fun stylesXml(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- Plantilla de referencia: temas usados por FlutterActivity. -->
            <resources>
                <style name="LaunchTheme" parent="@android:style/Theme.Light.NoTitleBar">
                    <item name="android:windowBackground">@android:color/white</item>
                </style>

                <style name="NormalTheme" parent="@android:style/Theme.Light.NoTitleBar">
                    <item name="android:windowBackground">?android:colorBackground</item>
                </style>
            </resources>
        """.trimIndent() + "\n"
    }

    @JvmStatic
    fun gradleProperties(): String {
        return """
            # Plantilla de referencia generada por Sketchware-Pro (soporte Flutter experimental).
            org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
            android.useAndroidX=true
            android.enableJetifier=true
            android.nonTransitiveRClass=true
        """.trimIndent() + "\n"
    }

    @JvmStatic
    fun androidReadme(packageName: String): String {
        val applicationId = FlutterProjectDefaults.normalizePackageName(packageName)

        return """
            # android/ (referencia)

            Plantillas minimas de referencia del lado Android del proyecto Flutter.
            No forman un proyecto Gradle completo: el fork inyecta el embedding de Flutter
            y compila el `libapp.so`/`kernel_blob` por su cuenta.

            - `app/src/main/AndroidManifest.xml`: manifest con `FlutterActivity`.
            - `app/src/main/res/values/styles.xml`: temas `LaunchTheme` / `NormalTheme`.
            - `gradle.properties`: propiedades base.

            applicationId de referencia: $applicationId
        """.trimIndent() + "\n"
    }
}
