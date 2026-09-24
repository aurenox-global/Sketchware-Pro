package pro.sketchware.utility;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class InvokeUtil {

    private static final String LOG_TAG = "SketchwarePro";

    public static final String[] ANDROID_CLASS_PREFIX = {
            "android.widget.", "android.view.", "android.webkit."
    };

    /**
     * Resultado de intentar crear una vista por reflexion: o la vista, o el motivo exacto por el que
     * no se ha podido. La vista previa necesita el motivo para explicar el aviso en vez de dejar un
     * hueco mudo (antes solo tenia un {@code null} sin explicacion).
     */
    public static final class CreateResult {
        public final View view;
        public final String failureReason;

        private CreateResult(View view, String failureReason) {
            this.view = view;
            this.failureReason = failureReason;
        }

        static CreateResult ok(View view) {
            return new CreateResult(view, null);
        }

        static CreateResult failed(String reason) {
            return new CreateResult(null, reason);
        }

        public boolean isSuccess() {
            return view != null;
        }
    }

    /**
     * Constructores que probamos, en orden. El de un argumento es el "natural" para una vista creada
     * por codigo, pero en la build RELEASE minificada (R8) puede NO existir:
     *
     * <p>R8 elimina los miembros que nadie del bytecode usa. Ningun punto del IDE construye estas
     * vistas con {@code new X(context)}, solo lo hacia esta reflexion (que R8 no puede ver): asi que
     * borraba el constructor {@code (Context)} y dejaba los de inflado XML, que SI se conservan
     * porque las reglas de las propias librerias (appcompat/material) los piden.
     *
     * <p>Consecuencia medida en la release v7.0.10.1 (APK del IDE, dex de arm64-v8a): de estas 10
     * clases AndroidX/Material/terceros, 9 habian perdido {@code <init>(Context)} mientras el nombre
     * de la clase seguia intacto. {@code Class.forName} funcionaba y
     * {@code getDeclaredConstructor(Context.class)} lanzaba NoSuchMethodException, asi que la vista
     * previa informaba de "vistas no instanciables" aunque la clase estuviera en el APK.
     *
     * <p>Por eso, si falta el constructor de 1 argumento, probamos los de inflado con
     * {@code AttributeSet = null} (son equivalentes: es lo que hace Android cuando el tag no lleva
     * atributos). Asi la vista previa deja de depender de que R8 conserve un constructor concreto.
     */
    private static final Class<?>[][] CONSTRUCTOR_SIGNATURES = {
            {Context.class},
            {Context.class, AttributeSet.class},
            {Context.class, AttributeSet.class, int.class},
    };

    public static View createView(Context context, @NonNull String name) {
        return createViewDetailed(context, name).view;
    }

    /**
     * Igual que {@link #createView(Context, String)} pero explicando el fallo. Si el nombre no lleva
     * paquete se prueban los prefijos habituales de Android (como hacia el metodo original).
     */
    public static CreateResult createViewDetailed(Context context, @NonNull String name) {
        if (name.contains(".")) {
            return create(context, name);
        }
        CreateResult last = null;
        for (String prefix : ANDROID_CLASS_PREFIX) {
            String qualified = prefix + name;
            CreateResult result = create(context, qualified);
            if (result.isSuccess()) {
                return result;
            }
            if (last == null) {
                last = result;
            }
        }
        return last != null ? last : CreateResult.failed("clase " + name + " no encontrada");
    }

    private static CreateResult create(Context context, @NonNull String name) {
        final Class<?> clazz;
        try {
            clazz = Class.forName(name);
        } catch (ClassNotFoundException notFound) {
            return CreateResult.failed("la clase no esta en el APK del editor (ClassNotFoundException)");
        } catch (Throwable throwable) {
            android.util.Log.d(LOG_TAG, "InvokeUtil: no se pudo cargar " + name, throwable);
            return CreateResult.failed("no se pudo cargar la clase: " + throwable);
        }

        Throwable invocationFailure = null;
        String invocationSignature = null;
        for (Class<?>[] signature : CONSTRUCTOR_SIGNATURES) {
            Constructor<?> constructor;
            try {
                constructor = clazz.getDeclaredConstructor(signature);
            } catch (NoSuchMethodException missing) {
                continue;
            } catch (Throwable throwable) {
                android.util.Log.d(LOG_TAG, "InvokeUtil: fallo buscando el constructor de " + name, throwable);
                continue;
            }
            try {
                constructor.setAccessible(true);
                View view = (View) constructor.newInstance(constructorArguments(context, signature));
                if (view != null) {
                    if (signature.length > 1) {
                        // Trazable: en la release el constructor de 1 argumento lo borra R8 y esto es
                        // justo lo que evita el "no disponible en el editor".
                        android.util.Log.i(LOG_TAG, "InvokeUtil: " + name
                                + " creada con <init>" + signatureText(signature)
                                + " (el de 1 argumento no esta en el APK)");
                    }
                    return CreateResult.ok(view);
                }
            } catch (Throwable throwable) {
                android.util.Log.d(LOG_TAG, "InvokeUtil: Exception ignored", throwable);
                if (invocationFailure == null) {
                    invocationFailure = throwable;
                    invocationSignature = signatureText(signature);
                }
            }
        }

        if (invocationFailure != null) {
            // La clase y el constructor existen, pero construirla ha fallado (tema, recursos...).
            return CreateResult.failed("el constructor <init>" + invocationSignature
                    + " fallo: " + describe(invocationFailure));
        }
        return CreateResult.failed("no hay constructor usable con Context: faltan <init>(Context), "
                + "<init>(Context, AttributeSet) y <init>(Context, AttributeSet, int)");
    }

    private static Object[] constructorArguments(Context context, Class<?>[] signature) {
        Object[] arguments = new Object[signature.length];
        arguments[0] = context;
        for (int i = 1; i < signature.length; i++) {
            // AttributeSet null: es exactamente lo que Android pasa cuando el tag no trae atributos;
            // los constructores de inflado lo admiten (obtainStyledAttributes(null, ...) es valido).
            arguments[i] = signature[i] == int.class ? 0 : null;
        }
        return arguments;
    }

    private static String signatureText(Class<?>[] signature) {
        StringBuilder text = new StringBuilder("(");
        for (int i = 0; i < signature.length; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(signature[i] == int.class ? "int" : signature[i].getSimpleName());
        }
        return text.append(')').toString();
    }

    /** Motivo corto y legible (para el dialogo de la vista previa): sin paquetes ni stacktrace. */
    private static String describe(Throwable throwable) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        String message = cause.getMessage();
        String type = cause.getClass().getSimpleName();
        return message == null || message.isEmpty() ? type : type + ": " + message;
    }

    public static Object invoke(Object v, String name, Class<?>[] types, Object... params) {
        try {
            Class<?> clazz = v.getClass();
            Method method = getMethod(clazz, name, types);
            if (method == null) return null;
            method.setAccessible(true);
            return method.invoke(v, params);

        } catch (Exception e) {

        }
        return null;
    }

    private static Method getMethod(Class<?> clazz, String name, Class<?>... types) {
        for (Class<?> superClass = clazz;
             superClass != Object.class;
             superClass = superClass.getSuperclass()) {
            try {
                return superClass.getDeclaredMethod(name, types);
            } catch (Exception e) {

            }
        }
        return null;
    }
}
