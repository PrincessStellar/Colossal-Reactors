package net.unfamily.colossal_reactors.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.unfamily.colossal_reactors.ColossalReactors;

/**
 * Shared layout and copy for the builder Open Link submenu (same screen view mode, not a nested Screen).
 */
public final class BuilderWebsiteLinkGui {

    public static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
            ColossalReactors.MODID, "textures/gui/builder_link.png");
    public static final int WIDTH = 250;
    public static final int HEIGHT = 250;

    /** Matches other builder / controller text insets. */
    public static final int TEXT_INSET = 12;
    public static final int TITLE_Y = 6;
    public static final int BODY_Y = 36;
    public static final int LINE_HEIGHT = 10;
    public static final int BODY_GAP_AFTER_DISCLAIMER = 8;

    public static final int BUTTON_H = 20;
    public static final int BUTTON_GAP = 3;
    public static final int BUTTON_BOTTOM_INSET = 13;
    public static final int BUTTON_HORIZONTAL_INSET = 12;

    private BuilderWebsiteLinkGui() {}

    public static Component title() {
        return Component.translatable("gui.colossal_reactors.builder.open_link");
    }

    /**
     * Disclaimer with third-party site name and non-responsibility clause highlighted in red.
     */
    public static MutableComponent disclaimer() {
        return Component.translatable("gui.colossal_reactors.builder.website_disclaimer.prefix")
                .append(Component.translatable("gui.colossal_reactors.builder.website_disclaimer.third_party")
                        .withStyle(ChatFormatting.RED))
                .append(Component.translatable("gui.colossal_reactors.builder.website_disclaimer.middle"))
                .append(Component.translatable("gui.colossal_reactors.builder.website_disclaimer.not_responsible")
                        .withStyle(ChatFormatting.RED))
                .append(Component.translatable("gui.colossal_reactors.builder.website_disclaimer.suffix"));
    }

    /**
     * Alternative line: use the Website pattern button for built-in offline designs; "Website" in red.
     */
    public static MutableComponent alternativeHint() {
        return Component.translatable("gui.colossal_reactors.builder.website_alternative.prefix")
                .append(Component.translatable("gui.colossal_reactors.builder.website_alternative.website")
                        .withStyle(ChatFormatting.RED))
                .append(Component.translatable("gui.colossal_reactors.builder.website_alternative.suffix"));
    }

    public static int buttonY(int topPos) {
        return topPos + HEIGHT - BUTTON_H - BUTTON_BOTTOM_INSET;
    }

    public static int buttonWidth() {
        return (WIDTH - BUTTON_HORIZONTAL_INSET * 2 - BUTTON_GAP * 2) / 3;
    }

    public static int buttonX(int leftPos, int index) {
        int w = buttonWidth();
        return leftPos + BUTTON_HORIZONTAL_INSET + index * (w + BUTTON_GAP);
    }

    /** Draws title + disclaimer + alternative hint (GUI-local coords for extractLabels). */
    public static int drawBodyLocal(GuiGraphicsExtractor graphics, Font font) {
        Component title = title();
        int titleX = (WIDTH - font.width(title)) / 2;
        graphics.text(font, title, titleX, TITLE_Y, GuiTextColors.TITLE, false);

        int textWidth = WIDTH - TEXT_INSET * 2;
        int textY = BODY_Y;
        for (FormattedCharSequence line : font.split(disclaimer(), textWidth)) {
            graphics.text(font, line, TEXT_INSET, textY, GuiTextColors.BODY, false);
            textY += LINE_HEIGHT;
        }
        textY += BODY_GAP_AFTER_DISCLAIMER;
        for (FormattedCharSequence line : font.split(alternativeHint(), textWidth)) {
            graphics.text(font, line, TEXT_INSET, textY, GuiTextColors.BODY, false);
            textY += LINE_HEIGHT;
        }
        return textY;
    }
}
