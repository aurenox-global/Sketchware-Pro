package pro.sketchware.utility;

import android.graphics.Color;
import android.text.TextUtils;
import android.widget.ImageView;

import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Aplicadores COMPARTIDOS de los atributos "inject" (app:x / android:x) de las familias de widget.
 *
 * <p>Contexto (ronda 8). La vista previa ({@code LayoutPreviewActivity}) aplicaba los atributos
 * "extra" con una lista blanca de ~19 nombres y descartaba en silencio el resto: los colores y
 * tamanos que el usuario configura en las familias de TabLayout, CircleImageView, CardView y
 * MaterialButton (tabIndicatorColor, civ_border_color, strokeColor, cornerRadius...) nunca llegaban
 * a la vista. El editor de diseno ({@code ViewPane}) SI los aplicaba, con su propio codigo. En vez
 * de duplicar esa logica en la vista previa, se extrae aqui y la usan los DOS motores.
 *
 * <p>Quien llama decide como se resuelven los valores ({@link ValueResolver}): la vista previa
 * resuelve {@code @color}/{@code @dimen}/{@code ?attr} del proyecto, el editor de diseno usa
 * {@code ColorEditorManager}/{@code PropertiesUtil}. Los valores por defecto (cuando el atributo no
 * esta) son los mismos en los dos motores, para que el editor y la vista previa no se contradigan.
 *
 * <p>Los appliers solo se aplican si el bean TIENE algun atributo de esa familia; si el usuario no
 * ha configurado nada, la vista conserva los valores por defecto del propio widget (evita cambiar
 * el aspecto de disenos existentes).
 */
public final class WidgetInjectApplier {

    private WidgetInjectApplier() {
    }

    /** Colores por defecto del editor de diseno (ViewPane). */
    public static final int DEFAULT_CARD_ELEVATION = 4;
    public static final int DEFAULT_CARD_RADIUS = 8;
    public static final int DEFAULT_TAB_INDICATOR_HEIGHT = 3;
    public static final int DEFAULT_TAB_INDICATOR_COLOR = 0xffffc107;
    public static final int DEFAULT_TAB_TEXT_COLOR = 0xff57beee;
    public static final int DEFAULT_CIV_BORDER_WIDTH = 3;
    public static final int DEFAULT_CIV_COLOR = 0xff008dcd;
    public static final int DEFAULT_MATERIAL_BUTTON_RADIUS = 8;

    /** Forma de resolver colores y medidas de cada motor (vista previa / editor de diseno). */
    public interface ValueResolver {
        /** Color de "#RRGGBB[AA]", "@color/x" o "?attr/x"; {@code fallback} si no se puede. */
        int color(String value, int fallback);

        /** Medida de "8dp", "8px", "@dimen/x"; {@code fallback} si no se puede. */
        int dimension(String value, int fallback);
    }

    // ---------------------------------------------------------------- familias

    /**
     * TabLayout: indicador (color y alto), color de texto normal y seleccionado, gravity y modo.
     * Es el bloque que el editor de diseno aplicaba en {@code ViewPane.updateTabLayout}.
     */
    public static void applyTabLayout(TabLayout tabLayout, InjectAttributeHandler handler, ValueResolver resolver) {
        if (!hasAny(handler, "tabGravity", "tabMode", "tabIndicatorHeight", "tabIndicatorColor",
                "tabTextColor", "tabSelectedTextColor", "tabIndicator")) {
            return;
        }
        String gravity = handler.getAttributeValueOf("tabGravity");
        String mode = handler.getAttributeValueOf("tabMode");
        String indicatorHeight = handler.getAttributeValueOf("tabIndicatorHeight");
        String indicatorColor = handler.getAttributeValueOf("tabIndicatorColor");
        String textColor = handler.getAttributeValueOf("tabTextColor");
        String selectedTextColor = handler.getAttributeValueOf("tabSelectedTextColor");

        tabLayout.setTabGravity(switch (gravity) {
            case "center" -> TabLayout.GRAVITY_CENTER;
            case "start" -> TabLayout.GRAVITY_START;
            default -> TabLayout.GRAVITY_FILL;
        });
        tabLayout.setTabMode(switch (mode) {
            case "auto" -> TabLayout.MODE_AUTO;
            case "scrollable" -> TabLayout.MODE_SCROLLABLE;
            default -> TabLayout.MODE_FIXED;
        });
        tabLayout.setSelectedTabIndicatorHeight(
                resolver.dimension(indicatorHeight, DEFAULT_TAB_INDICATOR_HEIGHT));
        tabLayout.setSelectedTabIndicatorColor(
                resolver.color(indicatorColor, DEFAULT_TAB_INDICATOR_COLOR));
        tabLayout.setTabTextColors(
                resolver.color(textColor, DEFAULT_TAB_TEXT_COLOR),
                resolver.color(selectedTextColor, Color.WHITE));
    }

    /**
     * CircleImageView (de.hdodenhof): color y grosor del borde y color del circulo de fondo.
     * Es el bloque que el editor de diseno aplicaba en {@code ViewPane.updateCircleImageView}.
     */
    public static void applyCircleImageView(CircleImageView imageView, InjectAttributeHandler handler, ValueResolver resolver) {
        if (!hasAny(handler, "civ_border_color", "civ_circle_background_color", "civ_border_width",
                "civ_border_overlay", "civ_fill_color")) {
            return;
        }
        imageView.setBorderColor(resolver.color(handler.getAttributeValueOf("civ_border_color"), DEFAULT_CIV_COLOR));
        imageView.setCircleBackgroundColor(resolver.color(
                handler.getAttributeValueOf("civ_circle_background_color"), DEFAULT_CIV_COLOR));
        imageView.setBorderWidth(resolver.dimension(
                handler.getAttributeValueOf("civ_border_width"), DEFAULT_CIV_BORDER_WIDTH));
        imageView.setBorderOverlay(Boolean.parseBoolean(
                emptyTo(handler.getAttributeValueOf("civ_border_overlay"), "false")));
        // Ojo: la libreria 3.1.0 ya no tiene setFillColor (civ_fill_color de versiones viejas), asi
        // que ese atributo cae al aviso ambar en vez de aplicarse en silencio a otro color.
    }

    /**
     * MaterialButton: radio de esquina, grosor y color del borde y tinte del icono.
     * Es el bloque que el editor de diseno aplicaba en {@code ViewPane.updateMaterialButton}
     * (mas el color del borde, que el editor no aplicaba).
     */
    public static void applyMaterialButton(MaterialButton materialButton, InjectAttributeHandler handler, ValueResolver resolver) {
        if (!hasAny(handler, "cornerRadius", "strokeWidth", "strokeColor", "iconTint", "insetTop",
                "insetBottom", "iconSize")) {
            return;
        }
        materialButton.setStrokeWidth(resolver.dimension(handler.getAttributeValueOf("strokeWidth"), 0));
        materialButton.setCornerRadius(resolver.dimension(
                handler.getAttributeValueOf("cornerRadius"), DEFAULT_MATERIAL_BUTTON_RADIUS));
        String strokeColor = handler.getAttributeValueOf("strokeColor");
        if (!TextUtils.isEmpty(strokeColor)) {
            int color = resolver.color(strokeColor, 0);
            if (color != 0) {
                materialButton.setStrokeColor(android.content.res.ColorStateList.valueOf(color));
            }
        }
        String iconTint = handler.getAttributeValueOf("iconTint");
        if (!TextUtils.isEmpty(iconTint)) {
            int color = resolver.color(iconTint, 0);
            if (color != 0) {
                materialButton.setIconTint(android.content.res.ColorStateList.valueOf(color));
            }
        }
        String iconSize = handler.getAttributeValueOf("iconSize");
        if (!TextUtils.isEmpty(iconSize)) {
            materialButton.setIconSize(resolver.dimension(iconSize, 0));
        }
        String insetTop = handler.getAttributeValueOf("insetTop");
        if (!TextUtils.isEmpty(insetTop)) {
            materialButton.setInsetTop(resolver.dimension(insetTop, 0));
        }
        String insetBottom = handler.getAttributeValueOf("insetBottom");
        if (!TextUtils.isEmpty(insetBottom)) {
            materialButton.setInsetBottom(resolver.dimension(insetBottom, 0));
        }
    }

    /**
     * CardView / MaterialCardView: color de fondo de la tarjeta, radio, elevacion, borde
     * (strokeColor/strokeWidth) y padding compatible. Es el bloque que el editor de diseno aplicaba
     * en {@code ViewPane.updateCardView}.
     *
     * @param fallbackBackground color de fondo del bean ya resuelto por el motor que llama; se usa
     *                           cuando el XML trae el fondo como {@code android:background} en vez
     *                           de {@code app:cardBackgroundColor}. 0 = no tocar.
     */
    public static void applyCardView(CardView cardView, InjectAttributeHandler handler,
                                     ValueResolver resolver, int fallbackBackground) {
        if (!hasAny(handler, "cardBackgroundColor", "cardElevation", "cardCornerRadius",
                "cardUseCompatPadding", "strokeColor", "strokeWidth", "cardMaxElevation",
                "cardPreventCornerOverlap")) {
            return;
        }
        String cardBackgroundColor = handler.getAttributeValueOf("cardBackgroundColor");
        if (!TextUtils.isEmpty(cardBackgroundColor)) {
            int color = resolver.color(cardBackgroundColor, 0);
            if (color != 0) {
                cardView.setCardBackgroundColor(color);
            }
        } else if (fallbackBackground != 0) {
            cardView.setCardBackgroundColor(fallbackBackground);
        }

        String cardElevation = handler.getAttributeValueOf("cardElevation");
        if (!TextUtils.isEmpty(cardElevation)) {
            cardView.setCardElevation(resolver.dimension(cardElevation, DEFAULT_CARD_ELEVATION));
        }
        String cardCornerRadius = handler.getAttributeValueOf("cardCornerRadius");
        if (!TextUtils.isEmpty(cardCornerRadius)) {
            cardView.setRadius(resolver.dimension(cardCornerRadius, DEFAULT_CARD_RADIUS));
        }
        String cardMaxElevation = handler.getAttributeValueOf("cardMaxElevation");
        if (!TextUtils.isEmpty(cardMaxElevation)) {
            cardView.setMaxCardElevation(resolver.dimension(cardMaxElevation, DEFAULT_CARD_ELEVATION));
        }
        String compatPadding = handler.getAttributeValueOf("cardUseCompatPadding");
        if (!TextUtils.isEmpty(compatPadding)) {
            cardView.setUseCompatPadding(Boolean.parseBoolean(compatPadding));
        }
        String preventOverlap = handler.getAttributeValueOf("cardPreventCornerOverlap");
        if (!TextUtils.isEmpty(preventOverlap)) {
            cardView.setPreventCornerOverlap(Boolean.parseBoolean(preventOverlap));
        }
        String strokeWidth = handler.getAttributeValueOf("strokeWidth");
        if (!TextUtils.isEmpty(strokeWidth) && cardView instanceof MaterialCardView materialCardView) {
            materialCardView.setStrokeWidth(resolver.dimension(strokeWidth, 0));
        }
        String strokeColor = handler.getAttributeValueOf("strokeColor");
        if (!TextUtils.isEmpty(strokeColor) && cardView instanceof MaterialCardView materialCardView) {
            int color = resolver.color(strokeColor, 0);
            if (color != 0) {
                materialCardView.setStrokeColor(color);
            }
        }
    }

    // ---------------------------------------------------------------- utilidades

    /** ¿El bean define alguno de estos atributos? (evita tocar la vista si no hay nada que aplicar). */
    public static boolean hasAny(InjectAttributeHandler handler, String... names) {
        for (String name : names) {
            if (handler.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private static String emptyTo(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    /**
     * Nombre "local" de un atributo: quita el prefijo de espacio de nombres
     * ("android:textColor" -> "textColor"). El handler devuelve los nombres ya locales, pero el
     * atributo puede llegar con prefijo desde otras fuentes.
     */
    public static String localName(String attribute) {
        if (attribute == null) {
            return "";
        }
        int index = attribute.indexOf(':');
        return index >= 0 ? attribute.substring(index + 1) : attribute;
    }

    /** Ajusta un ImageView al valor de scaleType que soporta (CircleImageView solo CENTER_CROP). */
    public static boolean isCircleImageView(ImageView imageView) {
        return imageView instanceof CircleImageView;
    }
}
