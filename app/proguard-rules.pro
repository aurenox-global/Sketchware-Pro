-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class pro.sketchware.** { *; }
-keep class a.a.a.** { *; }
-keep class com.besome.sketch.** { *; }
-keep class mod.** { *; }

-keep class * implements android.os.Parcelable { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.app.Application { *; }

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class * {
    native <methods>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class io.github.rosemoe.sora.** { *; }

-keep class com.google.firebase.** { *; }

-keep class pro.sketchware.plugins.** { *; }
-keep class pro.sketchware.debugger.** { *; }
-keep class pro.sketchware.lsp.** { *; }
-keep class pro.sketchware.kmp.** { *; }
-keep class pro.sketchware.metrics.** { *; }
-keep class pro.sketchware.ai.** { *; }

-keep class kellinwood.** { *; }

# --- Hotfix v7.0.8.1: reflexion rota en release (R8) ---
# com.itsaky.androidide.config.JavacConfigProvider.<clinit> NO referencia los campos del
# enum por bytecode: los busca por reflexion con el nombre en un String
# (javax.lang.model.SourceVersion.class.getDeclaredField("RELEASE_17" / "RELEASE_11" / "RELEASE_8")).
# R8 no puede ver esa dependencia, asi que en la release minificada eliminaba los campos
# static del enum -> getDeclaredField lanza NoSuchFieldException -> IllegalStateException
# (linea 46 del <clinit>) -> el catch(Throwable) la reenvuelve en RuntimeException ->
# ExceptionInInitializerError al inicializar SourceVersion desde FileSystem, y
# ProjectBuilder.compileJavaCode (ECJ) revienta: NINGUN proyecto compila en release.
# En debug no pasa porque minifyEnabled = false.
# Estas reglas conservan el enum completo (nombre + TODOS los campos) solo donde hace falta.
-keep class javax.lang.model.SourceVersion { *; }

# Resto de enums de javax.lang.model (Modifier, ElementKind, TypeKind, NestingKind...):
# el propio compilador y el IDE los consultan por nombre en varios puntos. Conservar
# nombre y miembros evita el mismo tipo de fallo sin llegar a desactivar R8 globalmente.
-keepclassmembers,allowshrinking enum javax.lang.model.** { *; }

# --- Hotfix v7.0.8.1: segundo fallo R8 en el compilador ECJ (mensajes por reflexion) ---
# org.eclipse.jdt.internal.compiler.util.Messages.<clinit> inyecta los textos de
# messages.properties en sus PROPIOS campos static usando reflexion por nombre
# (Messages.initializeMessages -> Class.getDeclaredFields() + Field.set()).
# R8 renombraba esos campos (text_block -> 'a', etc.), la inyeccion no encontraba nada
# y quedaban a null -> JavaFeature.<clinit> acababa llamando a
# MessageFormat.format(null, ...) -> NullPointerException -> ExceptionInInitializerError
# dentro del parser de ECJ -> ProjectBuilder.compileJavaCode vuelve a fallar.
# Hay que conservar la clase, sus campos y (sobre todo) sus NOMBRES sin optimizar.
-keep class org.eclipse.jdt.internal.compiler.util.Messages { *; }

# Red de seguridad para el resto de tablas de mensajes del compilador ECJ
# (batch/Main, EclipseFileManager, DefaultProblemFactory...): son campos
# public static String que se consultan de forma indirecta, asi que no se pueden
# renombrar ni sustituir por su valor. No es una desactivacion global de R8.
-keepclassmembers class org.eclipse.jdt.internal.compiler.** {
    public static java.lang.String *;
}

# --- Hotfix v7.0.8.1: tercer fallo R8 en el firmado del APK (apksig) ---
# com.android.apksig.internal.asn1.Asn1BerParser instancia las clases ASN.1 POR NOMBRE
# usando Class.getConstructor() (p.ej. com.android.apksig.internal.x509.SubjectPublicKeyInfo,
# cuyo constructor vacio solo se invoca por reflexion). R8 lo eliminaba ->
# NoSuchMethodException -> Asn1DecodingException -> ProjectBuilder.signDebugApk fallaba
# al firmar el APK generado (v2/v3 scheme). Se conserva apksig/apksigner completo.
-keep class com.android.apksig.** { *; }
-keep class com.android.apksigner.** { *; }

# --- Hotfix v7.0.10.1: cuarto fallo R8 (vista previa de disenos "no instanciable") ---
# La vista previa crea las vistas del XML POR REFLEXION
# (pro.sketchware.utility.InvokeUtil -> Class.forName + getDeclaredConstructor(Context.class)).
# R8 no ve esa llamada, y los constructores de un argumento de las vistas de AndroidX/Material
# (el recurso "new X(context)" que solo usa la reflexion) tampoco los usa NADIE del bytecode: los
# borra. Los de inflado (Context, AttributeSet) SI sobreviven, porque son los que piden las reglas
# de las propias librerias; por eso el nombre de la clase seguia intacto y el fallo era un
# NoSuchMethodException, no un ClassNotFoundException.
#
# MEDIDO en el APK release (arm64-v8a, v7.0.10.1, minifyEnabled=true): de las 10 clases del aviso
# del usuario, 9 habian perdido <init>(Context) y solo de.hdodenhof...CircleImageView lo conservaba
# (el IDE lo usa en codigo, no solo por reflexion). En debug (minifyEnabled=false) todas lo tienen.
# Consecuencia: "Preview PARCIAL: 10 vistas no instanciables" con el layout dibujado a medias.
#
# Dos arreglos complementarios (cualquiera de los dos basta; se ponen los dos a proposito):
#  1. InvokeUtil tambien prueba (Context, AttributeSet) con AttributeSet null y
#     (Context, AttributeSet, int): asi la preview funciona aunque R8 borre el constructor de 1
#     argumento de una clase que nadie haya previsto aqui.
#  2. Estas reglas conservan el constructor de 1 argumento de las vistas que YA estan en el APK, que
#     es el contrato historico de la reflexion del IDE.
# Acotadas a "extends android.view.View" y a los paquetes de las vistas afectadas: NO es una
# desactivacion global de R8 ni un -keep de clases completas (no impide el shrinking de las clases
# que no se usan). Si en el futuro aparece otra clase de libreria con el mismo sintoma, hay que
# anadir su paquete aqui SOLO si procede (el arreglo 1 ya la cubriria igualmente).
-keepclassmembers class androidx.** extends android.view.View {
    public <init>(android.content.Context);
}
-keepclassmembers class com.google.android.material.** extends android.view.View {
    public <init>(android.content.Context);
}
-keepclassmembers class de.hdodenhof.** extends android.view.View {
    public <init>(android.content.Context);
}

# --- Ronda 10a: quinto fallo R8 de la vista previa (widgets de libreria sin constructor) ---
# La pregunta del usuario era: "en los LinearLayout H y V no se ven los colores; a ti si". La ronda 8
# se verifico con el APK DEBUG (minifyEnabled=false); con la RELEASE (R8) hay miembros que solo se
# alcanzan POR REFLEXION y desaparecen.
#
# MEDIDO en el APK release arm64-v8a (v7.0.10.5, minifyEnabled=true):
#   * 62 clases pierden <init>(android.content.Context) respecto al APK debug
#     (app/build/outputs/mapping/release/usage.txt);
#   * en varias vistas de libreria se borran los TRES constructores
#     (<init>(Context), <init>(Context, AttributeSet) y <init>(Context, AttributeSet, int)):
#     com.google.android.gms.common.SignInButton, com.google.android.flexbox.FlexboxLayout,
#     com.airbnb.lottie.LottieAnimationView, com.bobur.androidsvg.SVGImageView,
#     com.caverock.androidsvg.SVGImageView...
#   * consecuencia EN EL DISPOSITIVO (mismo XML, mismos ajustes): en release la vista previa no puede
#     instanciarlas (NoSuchMethodException en InvokeUtil) y dibuja su contenedor aproximado; todos sus
#     atributos (colores incluidos) se quedan sin aplicar. Reproducido y medido con el caso F de la
#     ronda 8 (SignInButton): en debug se aplican buttonSize="wide"/colorScheme="dark", en release
#     pasaban al aviso ambar como "no se ha encontrado un setter equivalente en FrameLayout".
#
# Las reglas de v7.0.10.1 solo cubrian androidx.**, com.google.android.material.** y de.hdodenhof.**;
# estas son las otras familias de VISTAS medidas como afectadas. Se conservan TODOS sus constructores
# publicos (no solo el de 1 argumento) porque el contrato de InvokeUtil prueba los tres. Sigue siendo
# acotado a "extends android.view.View" de esos paquetes: no es un -keep global ni impide el shrinking
# de las clases que no son vistas. Delta de tamano medido del APK arm64-v8a release: ver
# /Users/zota/.openclaw/workspace/preview-r10a-release.md (apartado de tamano).
-keepclassmembers class com.google.android.gms.** extends android.view.View {
    public <init>(...);
}
-keepclassmembers class com.google.android.flexbox.** extends android.view.View {
    public <init>(...);
}
-keepclassmembers class com.airbnb.lottie.** extends android.view.View {
    public <init>(...);
}
-keepclassmembers class com.bobur.androidsvg.** extends android.view.View {
    public <init>(...);
}
-keepclassmembers class com.caverock.androidsvg.** extends android.view.View {
    public <init>(...);
}

# Atributos de libreria: la vista previa ya NO los aplica por reflexion (ronda 10a). Un setter que
# solo se alcanzaba asi podia renombrarse/borrarse y el atributo se perdia en silencio. Ahora va por
# llamadas directas (LayoutPreviewActivity.applyLibraryAttribute) y lo que no tiene mapeo explicito se
# anota en el aviso ambar con el motivo. No hace falta conservar nombres de setters de libreria.

-dontwarn com.google.errorprone.**
-dontwarn javax.xml.stream.**
-dontwarn org.codehaus.stax2.**

# --- Reglas generadas por R8 (app/build/outputs/mapping/release/missing_rules.txt) ---
# Son referencias a clases que no existen en Android (log4j JMS/mail, tink HTTP,
# intellij, sun.misc, java.awt, javax.mail...). R8 las ve dentro de las librerias
# pero no se usan en el dispositivo. Regenerar con: ./gradlew :app:assembleRelease
-dontwarn a.a.a.IB
-dontwarn a.a.a.kB
-dontwarn a.a.a.lB
-dontwarn a.a.a.tB
-dontwarn aQute.bnd.annotation.spi.ServiceProvider
-dontwarn com.android.tools.r8.keepanno.annotations.KeepForApi
-dontwarn com.conversantmedia.util.concurrent.DisruptorBlockingQueue
-dontwarn com.conversantmedia.util.concurrent.SpinPolicy
-dontwarn com.ctc.wstx.shaded.msv_core.driver.textui.Driver
-dontwarn com.fasterxml.jackson.dataformat.yaml.YAMLFactory
-dontwarn com.fasterxml.jackson.dataformat.yaml.YAMLMapper
-dontwarn com.google.api.client.http.GenericUrl
-dontwarn com.google.api.client.http.HttpHeaders
-dontwarn com.google.api.client.http.HttpRequest
-dontwarn com.google.api.client.http.HttpRequestFactory
-dontwarn com.google.api.client.http.HttpResponse
-dontwarn com.google.api.client.http.HttpTransport
-dontwarn com.google.api.client.http.javanet.NetHttpTransport$Builder
-dontwarn com.google.api.client.http.javanet.NetHttpTransport
-dontwarn com.google.auto.service.AutoService
-dontwarn com.ibm.icu.lang.UCharacter
-dontwarn com.intellij.util.diff.Diff$Change
-dontwarn com.intellij.util.diff.Diff
-dontwarn com.intellij.util.diff.FilesTooBigForDiffException
-dontwarn com.intellij.util.lang.DirectByteBufferPool
-dontwarn com.intellij.util.lang.Hash
-dontwarn com.intellij.util.lang.Xor16
-dontwarn com.intellij.util.lang.Xx3UnencodedString
-dontwarn com.lmax.disruptor.BlockingWaitStrategy
-dontwarn com.lmax.disruptor.BusySpinWaitStrategy
-dontwarn com.lmax.disruptor.EventFactory
-dontwarn com.lmax.disruptor.EventHandler
-dontwarn com.lmax.disruptor.EventTranslator
-dontwarn com.lmax.disruptor.EventTranslatorTwoArg
-dontwarn com.lmax.disruptor.EventTranslatorVararg
-dontwarn com.lmax.disruptor.ExceptionHandler
-dontwarn com.lmax.disruptor.LifecycleAware
-dontwarn com.lmax.disruptor.RingBuffer
-dontwarn com.lmax.disruptor.SequenceReportingEventHandler
-dontwarn com.lmax.disruptor.SleepingWaitStrategy
-dontwarn com.lmax.disruptor.TimeoutBlockingWaitStrategy
-dontwarn com.lmax.disruptor.TimeoutException
-dontwarn com.lmax.disruptor.WaitStrategy
-dontwarn com.lmax.disruptor.YieldingWaitStrategy
-dontwarn com.lmax.disruptor.dsl.Disruptor
-dontwarn com.lmax.disruptor.dsl.EventHandlerGroup
-dontwarn com.lmax.disruptor.dsl.ProducerType
-dontwarn com.sun.jdi.VirtualMachine
-dontwarn com.sun.jdi.event.Event
-dontwarn com.sun.jdi.event.EventIterator
-dontwarn com.sun.jdi.event.EventQueue
-dontwarn com.sun.jdi.event.EventSet
-dontwarn com.sun.jdi.event.MethodEntryEvent
-dontwarn java.awt.AWTEvent
-dontwarn java.awt.BorderLayout
-dontwarn java.awt.Color
-dontwarn java.awt.Component
-dontwarn java.awt.Container
-dontwarn java.awt.Dimension
-dontwarn java.awt.EventQueue
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.LayoutManager
-dontwarn java.awt.Point
-dontwarn java.awt.Rectangle
-dontwarn java.awt.Shape
-dontwarn java.awt.Toolkit
-dontwarn java.awt.Window
-dontwarn java.awt.datatransfer.Transferable
-dontwarn java.awt.dnd.DragGestureListener
-dontwarn java.awt.dnd.DragSourceListener
-dontwarn java.awt.dnd.DragSourceMotionListener
-dontwarn java.awt.dnd.DropTargetListener
-dontwarn java.awt.event.AWTEventListener
-dontwarn java.awt.event.ActionListener
-dontwarn java.awt.event.ComponentListener
-dontwarn java.awt.event.HierarchyListener
-dontwarn java.awt.event.InvocationEvent
-dontwarn java.awt.event.WindowAdapter
-dontwarn java.awt.geom.Area
-dontwarn java.awt.image.DataBuffer
-dontwarn java.awt.image.DataBufferByte
-dontwarn java.awt.image.DataBufferInt
-dontwarn java.awt.image.DirectColorModel
-dontwarn java.awt.image.MultiPixelPackedSampleModel
-dontwarn java.awt.image.Raster
-dontwarn java.awt.image.SampleModel
-dontwarn java.awt.image.SinglePixelPackedSampleModel
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Introspector
-dontwarn java.beans.Transient
-dontwarn java.lang.Module
-dontwarn java.lang.management.CompilationMXBean
-dontwarn java.lang.management.GarbageCollectorMXBean
-dontwarn java.lang.management.LockInfo
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.MemoryMXBean
-dontwarn java.lang.management.MemoryPoolMXBean
-dontwarn java.lang.management.MemoryType
-dontwarn java.lang.management.MemoryUsage
-dontwarn java.lang.management.MonitorInfo
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn java.lang.management.ThreadInfo
-dontwarn java.lang.management.ThreadMXBean
-dontwarn javac.internal.PreviewFeature$Feature
-dontwarn javac.internal.PreviewFeature
-dontwarn javac.internal.jrtfs.JrtFileSystemProvider
-dontwarn javax.activation.DataSource
-dontwarn javax.jms.Connection
-dontwarn javax.jms.ConnectionFactory
-dontwarn javax.jms.Destination
-dontwarn javax.jms.JMSException
-dontwarn javax.jms.MapMessage
-dontwarn javax.jms.Message
-dontwarn javax.jms.MessageProducer
-dontwarn javax.jms.ObjectMessage
-dontwarn javax.jms.Session
-dontwarn javax.jms.TextMessage
-dontwarn javax.mail.Address
-dontwarn javax.mail.Authenticator
-dontwarn javax.mail.BodyPart
-dontwarn javax.mail.Message$RecipientType
-dontwarn javax.mail.Message
-dontwarn javax.mail.MessagingException
-dontwarn javax.mail.Multipart
-dontwarn javax.mail.PasswordAuthentication
-dontwarn javax.mail.Session
-dontwarn javax.mail.Transport
-dontwarn javax.mail.internet.InternetAddress
-dontwarn javax.mail.internet.InternetHeaders
-dontwarn javax.mail.internet.MimeBodyPart
-dontwarn javax.mail.internet.MimeMessage
-dontwarn javax.mail.internet.MimeMultipart
-dontwarn javax.mail.internet.MimeUtility
-dontwarn javax.mail.util.ByteArrayDataSource
-dontwarn javax.management.AttributeNotFoundException
-dontwarn javax.management.InstanceAlreadyExistsException
-dontwarn javax.management.InstanceNotFoundException
-dontwarn javax.management.JMException
-dontwarn javax.management.MBeanException
-dontwarn javax.management.MBeanNotificationInfo
-dontwarn javax.management.MBeanRegistrationException
-dontwarn javax.management.MBeanServer
-dontwarn javax.management.MalformedObjectNameException
-dontwarn javax.management.NotCompliantMBeanException
-dontwarn javax.management.Notification
-dontwarn javax.management.NotificationBroadcasterSupport
-dontwarn javax.management.NotificationEmitter
-dontwarn javax.management.NotificationFilter
-dontwarn javax.management.NotificationListener
-dontwarn javax.management.ObjectInstance
-dontwarn javax.management.ObjectName
-dontwarn javax.management.QueryExp
-dontwarn javax.management.ReflectionException
-dontwarn javax.naming.Context
-dontwarn javax.naming.InitialContext
-dontwarn javax.naming.NamingEnumeration
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.directory.DirContext
-dontwarn javax.naming.directory.InitialDirContext
-dontwarn javax.naming.directory.SearchControls
-dontwarn javax.naming.directory.SearchResult
-dontwarn javax.script.AbstractScriptEngine
-dontwarn javax.script.Bindings
-dontwarn javax.script.Compilable
-dontwarn javax.script.CompiledScript
-dontwarn javax.script.Invocable
-dontwarn javax.script.ScriptEngine
-dontwarn javax.script.ScriptEngineFactory
-dontwarn javax.script.ScriptEngineManager
-dontwarn javax.script.ScriptException
-dontwarn javax.script.SimpleBindings
-dontwarn javax.servlet.ServletContextListener
-dontwarn javax.swing.Icon
-dontwarn javax.swing.JComponent
-dontwarn javax.swing.JLayeredPane
-dontwarn javax.swing.JPanel
-dontwarn javax.swing.JRootPane
-dontwarn javax.swing.RepaintManager
-dontwarn javax.swing.RootPaneContainer
-dontwarn javax.swing.SwingUtilities
-dontwarn javax.swing.text.html.HTMLEditorKit$ParserCallback
-dontwarn javax.xml.bind.JAXBContext
-dontwarn javax.xml.bind.JAXBElement
-dontwarn javax.xml.bind.JAXBException
-dontwarn javax.xml.bind.Marshaller
-dontwarn javax.xml.bind.Unmarshaller
-dontwarn javax.xml.bind.ValidationEventHandler
-dontwarn javax.xml.bind.annotation.XmlAccessType
-dontwarn javax.xml.bind.annotation.XmlAccessorType
-dontwarn javax.xml.bind.annotation.XmlAttribute
-dontwarn javax.xml.bind.annotation.XmlElement
-dontwarn javax.xml.bind.annotation.XmlElementDecl
-dontwarn javax.xml.bind.annotation.XmlElements
-dontwarn javax.xml.bind.annotation.XmlID
-dontwarn javax.xml.bind.annotation.XmlIDREF
-dontwarn javax.xml.bind.annotation.XmlNsForm
-dontwarn javax.xml.bind.annotation.XmlRegistry
-dontwarn javax.xml.bind.annotation.XmlRootElement
-dontwarn javax.xml.bind.annotation.XmlSchema
-dontwarn javax.xml.bind.annotation.XmlSchemaType
-dontwarn javax.xml.bind.annotation.XmlSeeAlso
-dontwarn javax.xml.bind.annotation.XmlTransient
-dontwarn javax.xml.bind.annotation.XmlType
-dontwarn javax.xml.bind.annotation.XmlValue
-dontwarn javax.xml.bind.annotation.adapters.XmlAdapter
-dontwarn javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter
-dontwarn kotlin.Cloneable$DefaultImpls
-dontwarn kotlin.annotations.jvm.Mutable
-dontwarn kotlin.annotations.jvm.ReadOnly
-dontwarn org.apache.avalon.framework.logger.Logger
-dontwarn org.apache.commons.csv.CSVFormat
-dontwarn org.apache.kafka.clients.producer.Callback
-dontwarn org.apache.kafka.clients.producer.KafkaProducer
-dontwarn org.apache.kafka.clients.producer.Producer
-dontwarn org.apache.kafka.clients.producer.ProducerRecord
-dontwarn org.apache.log.Hierarchy
-dontwarn org.apache.log.Logger
-dontwarn org.apache.log4j.Category
-dontwarn org.apache.log4j.Logger
-dontwarn org.apache.tools.ant.Task
-dontwarn org.apache.tools.ant.taskdefs.compilers.DefaultCompilerAdapter
-dontwarn org.eclipse.jdt.internal.compiler.ISourceElementRequestor$FieldInfo
-dontwarn org.eclipse.jdt.internal.compiler.ISourceElementRequestor$MethodInfo
-dontwarn org.eclipse.jdt.internal.compiler.ISourceElementRequestor$ParameterInfo
-dontwarn org.eclipse.jdt.internal.compiler.ISourceElementRequestor$TypeInfo
-dontwarn org.eclipse.jdt.internal.compiler.ISourceElementRequestor$TypeParameterInfo
-dontwarn org.eclipse.jdt.internal.compiler.ISourceElementRequestor
-dontwarn org.jctools.queues.MessagePassingQueue$Consumer
-dontwarn org.jctools.queues.MpscArrayQueue
-dontwarn org.jetbrains.annotations.ApiStatus$Obsolete
-dontwarn org.joda.time.Instant
-dontwarn org.osgi.framework.Bundle
-dontwarn org.osgi.framework.BundleActivator
-dontwarn org.osgi.framework.BundleReference
-dontwarn org.osgi.framework.FrameworkUtil
-dontwarn org.osgi.framework.SynchronousBundleListener
-dontwarn org.osgi.framework.wiring.BundleWiring
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.impl.StaticMDCBinder
-dontwarn org.slf4j.impl.StaticMarkerBinder
-dontwarn org.tukaani.xz.ARMOptions
-dontwarn org.tukaani.xz.ARMThumbOptions
-dontwarn org.tukaani.xz.FilterOptions
-dontwarn org.tukaani.xz.IA64Options
-dontwarn org.tukaani.xz.LZMA2Options
-dontwarn org.tukaani.xz.PowerPCOptions
-dontwarn org.tukaani.xz.SPARCOptions
-dontwarn org.tukaani.xz.X86Options
-dontwarn org.tukaani.xz.XZOutputStream
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry
-dontwarn org.zeromq.ZMQ$Context
-dontwarn org.zeromq.ZMQ$Socket
-dontwarn org.zeromq.ZMQ
-dontwarn res.Hex
-dontwarn sun.misc.BASE64Encoder
-dontwarn sun.reflect.annotation.AnnotationParser
-dontwarn sun.reflect.annotation.AnnotationType
-dontwarn sun.reflect.annotation.EnumConstantNotPresentExceptionProxy
-dontwarn sun.reflect.annotation.ExceptionProxy
-dontwarn sun.security.pkcs.ContentInfo
-dontwarn sun.security.pkcs.PKCS7
-dontwarn sun.security.pkcs.SignerInfo
-dontwarn sun.security.util.DerValue
-dontwarn sun.security.util.ObjectIdentifier
-dontwarn sun.security.x509.AlgorithmId
-dontwarn sun.security.x509.X500Name

# --- Ronda A1 (firma con keystore propio): sexto fallo R8, ahora en spongycastle ---
# org.spongycastle.jce.provider.BouncyCastleProvider carga sus tablas de implementaciones
# POR REFLEXION: instancia cada clase interna "*$Mappings" con Class.newInstance(), o sea
# con su constructor sin argumentos. R8 no ve esa llamada y lo borraba ->
# "InternalError: cannot create instance of org.spongycastle.jcajce.provider.digest.GOST3411$Mappings:
# InstantiationException: has no zero argument constructor" en el <clinit> de
# kellinwood.security.zipsigner.optional.KeyStoreFileManager -> CRASH de la app.
# MEDIDO en el APK release arm64-v8a (v7.0.13.0 + cambios de la ronda A1, minifyEnabled=true):
# al abrir Ajustes -> Keystore manager la app moria con ese InternalError (adjunto en
# preview-evidence/sw-a1/50-crash-spongycastle.txt).
# No es solo la funcion nueva: ese <clinit> lo toca tambien el camino de firma con keystore
# propio del Export (CustomKeySigner -> KeyStoreFileManager), asi que estaba roto en release
# desde antes. Se conserva spongycastle completo, como ya se hace con apksig y kellinwood.
-keep class org.spongycastle.** { *; }
-dontwarn org.spongycastle.**

# --- Ronda A2 (AAB): septimo fallo R8, ahora en los protobuf de bundletool ---
# El "Export AAB" moria siempre en release con:
#   E AppExporter: Failed to build bundle: Generated message class
#   "com.android.bundle.Config$BundleConfig" missing method "getBundletool".
#   at mod.jbk.build.compiler.bundle.AppBundleCompiler.buildBundle
# El runtime de protobuf resuelve los getters de las clases generadas POR NOMBRE
# (reflexion), asi que R8 no ve esas llamadas y renombra/elimina getBundletool().
# MEDIDO sobre el APK release arm64-v8a construido en esta ronda: `getBundletool`
# aparecia 0 veces en classes.dex; en cambio la clase si existe (BundleConfig).
# Se conservan enteros los paquetes de bundletool y protobuf, como ya se hace con
# apksig, kellinwood y spongycastle.
-keep class com.android.bundle.** { *; }
-keep class com.android.tools.build.bundletool.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.android.bundle.**
-dontwarn com.android.tools.build.bundletool.**
-dontwarn com.google.protobuf.**

# --- Ronda 10b (compilar un proyecto EN EL DISPOSITIVO): octavo fallo R8, en el R8/D8 embebido ---
# Sintoma reportado al COMPILAR un proyecto en el movil (etapa de dex):
#   com.android.tools.r8.internal.Qf: Failure creating provider for the threading module
#     at com.android.tools.r8.internal.QJ.c(Unknown Source:55)
#     at mod.jbk.build.compiler.dex.DexCompiler.compileDexFiles
#   Caused by: java.lang.NoSuchMethodException:
#     com.android.tools.r8.threading.providers.blocking.ThreadingModuleBlockingProvider.<init> []
#
# CAUSA (medida, no deducida): la app EMBEBE R8/D8 (libs.bundles.shrinker -> com.android.tools:r8:8.11.18)
# para compilar proyectos, y el R8 de la IDE minifica TAMBIEN esas clases. La eleccion del proveedor de
# threading no se hace por bytecode sino POR REFLEXION con el nombre en un String:
#   com.android.tools.r8.threading.a.b():   (en el APK minificado acabo viviendo en com.android.tools.r8.internal.QJ.c)
#     for (name in ["...threading.providers.blocking.ThreadingModuleBlockingProvider",
#                   "...threading.providers.singlethreaded.ThreadingModuleSingleThreadedProvider"])
#         Class.forName(name).getDeclaredConstructor().newInstance();
# El constructor SIN ARGUMENTOS de esos dos proveedores no lo llama nadie del bytecode, asi que R8 lo
# borro. El NOMBRE de la clase sobrevivio (por eso es NoSuchMethodException y no ClassNotFoundException) y
# el bucle solo tolera ClassNotFoundException: cualquier otro ReflectiveOperationException lo reenvuelve en
# Qf("Failure creating provider for the threading module") y lo lanza. Por eso el fallo es DETERMINISTA en
# release, en cualquier dispositivo: NO depende del numero de nucleos (la hipotesis de los 4 vs 8 nucleos
# del AVD queda descartada por la medicion; ver el probe de la evidencia).
#
# EVIDENCIA (antes del arreglo), APK release arm64-v8a, minifyEnabled=true:
#   * app/build/outputs/mapping/release/usage.txt lo dice literalmente: R8 elimino
#       com.android.tools.r8.threading.providers.blocking.ThreadingModuleBlockingProvider:
#           public void <init>()
#       com.android.tools.r8.threading.providers.singlethreaded.ThreadingModuleSingleThreadedProvider:
#           public void <init>()
#   * dexdump del APK: las dos clases ESTAN pero con "Direct methods -" vacio (sin <init>).
#   * en el dispositivo (emulador arm64, cargando el APK release como classpath, sin instalar nada):
#       NoSuchMethodException: ...ThreadingModuleBlockingProvider.<init> []
#       y la factory -> "Failure creating provider for the threading module".
#   * en debug (minifyEnabled=false) los constructores estan presentes y no falla, como en el resto de la
#     familia de fallos (v7.0.8.1, v7.0.10.1, spongycastle, bundletool).
#
# Alcance: solo hay UNA copia de R8/D8 en el APK (el artefacto de Gradle); los .dex precompilados de
# assets/libs/*.zip no incluyen R8, asi que no hay mas copias que arreglar. Se conserva el paquete
# com.android.tools.r8.threading completo (5 clases minusculas): nombres + miembros de la factory y, sobre
# todo, los <init> de los proveedores, para que la reflexion encuentre lo que busca.
-keep class com.android.tools.r8.threading.** { *; }
