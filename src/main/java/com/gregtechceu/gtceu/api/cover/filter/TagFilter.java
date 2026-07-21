package com.gregtechceu.gtceu.api.cover.filter;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTTextFieldElement;
import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.utils.TagExprFilter;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.network.chat.Component;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.regex.Pattern;

import static com.gregtechceu.gtceu.api.gui.UITemplate.setLDLib2Bounds;

public abstract class TagFilter<T, S extends Filter<T, S>> implements Filter<T, S> {

    private static final int MAX_FILTER_LENGTH = 64;
    private static final Pattern DOUBLE_WILDCARD = Pattern.compile("\\*{2,}");
    private static final Pattern DOUBLE_AND = Pattern.compile("&{2,}");
    private static final Pattern DOUBLE_OR = Pattern.compile("\\|{2,}");
    private static final Pattern DOUBLE_NOT = Pattern.compile("!{2,}");
    private static final Pattern DOUBLE_XOR = Pattern.compile("\\^{2,}");
    private static final Pattern DOUBLE_SPACE = Pattern.compile(" {2,}");

    @Getter
    protected String tagFilterExpression = "";

    protected Consumer<S> itemWriter = filter -> {};
    protected Consumer<S> onUpdated = filter -> itemWriter.accept(filter);

    @Nullable
    protected TagExprFilter.TagExprParser.MatchExpr matchExpr = null;

    protected TagFilter() {}

    @Override
    public boolean isBlank() {
        return tagFilterExpression.isBlank();
    }

    public void setFilterExpr(String filterExpr) {
        this.tagFilterExpression = filterExpr;
        matchExpr = TagExprFilter.parseExpression(tagFilterExpression);
        // noinspection unchecked
        onUpdated.accept((S) this);
    }

    @Override
    public UIElement openLDLib2Configurator(int x, int y) {
        UIElement group = new UIElement();
        setLDLib2Bounds(group, x, y, 18 * 3 + 25, 18 * 3);
        group.addChild(createLDLib2InfoIcon());
        group.addChild(createLDLib2TextField());
        return group;
    }

    private UIElement createLDLib2InfoIcon() {
        UIElement icon = new UIElement();
        icon.style(style -> style
                .backgroundTexture(GuiTextures.INFO_ICON)
                .tooltips(LangHandler.getMultiLang("cover.tag_filter.info").toArray(Component[]::new)));
        setLDLib2Bounds(icon, 0, 0, 20, 20);
        return icon;
    }

    private GTTextFieldElement createLDLib2TextField() {
        GTTextFieldElement textField = new GTTextFieldElement(0, 29, 18 * 3 + 25, 12);
        textField.setAnyString();
        textField.setText(tagFilterExpression, false);
        textField.setTextResponder(input -> updateLDLib2FilterExpr(textField, input));
        return textField;
    }

    private void updateLDLib2FilterExpr(GTTextFieldElement textField, String input) {
        String normalized = normalizeInput(input);
        if (!normalized.equals(input)) {
            textField.setText(normalized, false);
        }
        setFilterExpr(normalized);
    }

    private static String normalizeInput(String input) {
        if (input.length() > MAX_FILTER_LENGTH) {
            input = input.substring(0, MAX_FILTER_LENGTH);
        }
        // remove all operators that are double
        input = DOUBLE_WILDCARD.matcher(input).replaceAll("*");
        input = DOUBLE_AND.matcher(input).replaceAll("&");
        input = DOUBLE_OR.matcher(input).replaceAll("|");
        input = DOUBLE_NOT.matcher(input).replaceAll("!");
        input = DOUBLE_XOR.matcher(input).replaceAll("^");
        input = DOUBLE_SPACE.matcher(input).replaceAll(" ");
        // move ( and ) so it doesn't create invalid expressions f.e. xxx (& yyy) => xxx & (yyy)
        // append or prepend ( and ) if the amount is not equal
        StringBuilder builder = new StringBuilder();
        int unclosed = 0;
        char last = ' ';
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == ' ') {
                if (last != '(')
                    builder.append(" ");
                continue;
            }
            if (c == '(')
                unclosed++;
            else if (c == ')') {
                unclosed--;
                if (last == '&' || last == '|' || last == '^') {
                    int l = builder.lastIndexOf(" " + last);
                    int l2 = builder.lastIndexOf(String.valueOf(last));
                    int insertionIndex = l >= 0 && l == l2 - 1 ? l : l2;
                    builder.insert(insertionIndex, ")");
                    continue;
                }
                if (i > 0 && builder.charAt(builder.length() - 1) == ' ') {
                    builder.deleteCharAt(builder.length() - 1);
                }
            } else if ((c == '&' || c == '|' || c == '^') && last == '(') {
                builder.deleteCharAt(builder.lastIndexOf("("));
                builder.append(c).append(" (");
                continue;
            }

            builder.append(c);
            last = c;
        }
        if (unclosed > 0) {
            builder.append(")".repeat(unclosed));
        } else if (unclosed < 0) {
            unclosed = -unclosed;
            for (int i = 0; i < unclosed; i++) {
                builder.insert(0, "(");
            }
        }
        input = builder.toString();
        return input.replaceAll(" {2,}", " ");
    }

    @Override
    public void setOnUpdated(Consumer<S> onUpdated) {
        this.onUpdated = filter -> {
            this.itemWriter.accept(filter);
            onUpdated.accept(filter);
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TagFilter<?, ?> tagFilter = (TagFilter<?, ?>) o;
        return tagFilterExpression.equals(tagFilter.tagFilterExpression);
    }

    @Override
    public int hashCode() {
        return tagFilterExpression.hashCode();
    }
}
