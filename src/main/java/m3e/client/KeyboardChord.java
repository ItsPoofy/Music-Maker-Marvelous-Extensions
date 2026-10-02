package m3e.client;

import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** One physical keyboard key plus the modifiers held with it. */
public record KeyboardChord(int key, int modifiers) {
    public static final int SUPPORTED_MODIFIERS = GLFW.GLFW_MOD_SHIFT
            | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER;

    public KeyboardChord {
        modifiers &= SUPPORTED_MODIFIERS;
    }

    public static KeyboardChord of(int key, int modifiers) {
        return new KeyboardChord(key, modifiers);
    }

    public boolean matches(int pressedKey, int pressedModifiers) {
        return key == pressedKey
                && modifiers == (pressedModifiers & SUPPORTED_MODIFIERS);
    }

    public boolean usesControl() {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    public Component shortComponent() {
        Component label = Component.literal(shortLabel());
        return usesControl() ? label.copy().withStyle(style -> style.withUnderlined(true)) : label;
    }

    public String shortLabel() {
        String label = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0
                ? shiftedLabel(key) : baseLabel(key);
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) label = "A+" + label;
        if ((modifiers & GLFW.GLFW_MOD_SUPER) != 0) label = "M+" + label;
        return label;
    }

    public String displayName() {
        StringBuilder text = new StringBuilder();
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) text.append("Ctrl+");
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) text.append("Alt+");
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) text.append("Shift+");
        if ((modifiers & GLFW.GLFW_MOD_SUPER) != 0) text.append("Super+");
        return text.append(baseLabel(key)).toString();
    }

    public static boolean isModifierKey(int key) {
        return key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
                || key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL
                || key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT
                || key == GLFW.GLFW_KEY_LEFT_SUPER || key == GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    private static String shiftedLabel(int key) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            return Character.toString((char) ('A' + key - GLFW.GLFW_KEY_A));
        }
        return switch (key) {
            case GLFW.GLFW_KEY_1 -> "!";
            case GLFW.GLFW_KEY_2 -> "@";
            case GLFW.GLFW_KEY_3 -> "#";
            case GLFW.GLFW_KEY_4 -> "$";
            case GLFW.GLFW_KEY_5 -> "%";
            case GLFW.GLFW_KEY_6 -> "^";
            case GLFW.GLFW_KEY_7 -> "&";
            case GLFW.GLFW_KEY_8 -> "*";
            case GLFW.GLFW_KEY_9 -> "(";
            case GLFW.GLFW_KEY_0 -> ")";
            case GLFW.GLFW_KEY_MINUS -> "_";
            case GLFW.GLFW_KEY_EQUAL -> "+";
            case GLFW.GLFW_KEY_LEFT_BRACKET -> "{";
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> "}";
            case GLFW.GLFW_KEY_BACKSLASH -> "|";
            case GLFW.GLFW_KEY_SEMICOLON -> ":";
            case GLFW.GLFW_KEY_APOSTROPHE -> "\"";
            case GLFW.GLFW_KEY_COMMA -> "<";
            case GLFW.GLFW_KEY_PERIOD -> ">";
            case GLFW.GLFW_KEY_SLASH -> "?";
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> "~";
            default -> baseLabel(key);
        };
    }

    private static String baseLabel(int key) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            return Character.toString((char) ('a' + key - GLFW.GLFW_KEY_A));
        }
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            return Character.toString((char) ('0' + key - GLFW.GLFW_KEY_0));
        }
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) {
            return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        }
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> "Sp";
            case GLFW.GLFW_KEY_MINUS -> "-";
            case GLFW.GLFW_KEY_EQUAL -> "=";
            case GLFW.GLFW_KEY_LEFT_BRACKET -> "[";
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> "]";
            case GLFW.GLFW_KEY_BACKSLASH -> "\\";
            case GLFW.GLFW_KEY_SEMICOLON -> ";";
            case GLFW.GLFW_KEY_APOSTROPHE -> "'";
            case GLFW.GLFW_KEY_COMMA -> ",";
            case GLFW.GLFW_KEY_PERIOD -> ".";
            case GLFW.GLFW_KEY_SLASH -> "/";
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> "`";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> "Ent";
            case GLFW.GLFW_KEY_BACKSPACE -> "Bk";
            case GLFW.GLFW_KEY_UP -> "Up";
            case GLFW.GLFW_KEY_DOWN -> "Dn";
            case GLFW.GLFW_KEY_LEFT -> "Lt";
            case GLFW.GLFW_KEY_RIGHT -> "Rt";
            default -> "K" + key;
        };
    }
}
