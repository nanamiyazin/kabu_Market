package dev.kabumarket;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.text.NumberFormat;
import java.util.Locale;

public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<gold>[カブ]</gold> ";

    private Msg() {}

    /** チャット用（接頭辞付き） */
    public static Component chat(String mini) {
        return MM.deserialize(PREFIX + mini);
    }

    /** 接頭辞なし */
    public static Component mm(String mini) {
        return MM.deserialize(mini);
    }

    /** アイテム名・説明用（斜体を外す） */
    public static Component item(String mini) {
        return MM.deserialize(mini).decoration(TextDecoration.ITALIC, false);
    }

    public static String num(long v) {
        return NumberFormat.getIntegerInstance(Locale.JAPAN).format(v);
    }

    /** 整数ならそのまま、小数があれば小数2桁まで */
    public static String num(double v) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.JAPAN);
        f.setMaximumFractionDigits(2);
        return f.format(v);
    }
}
