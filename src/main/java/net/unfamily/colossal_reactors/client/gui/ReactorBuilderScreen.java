package net.unfamily.colossal_reactors.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.util.Util;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;
import net.unfamily.colossal_reactors.ColossalReactors;
import net.unfamily.colossal_reactors.menu.ReactorBuilderMenu;
import net.unfamily.colossal_reactors.heatsink.HeatSinkLoader;
import net.unfamily.colossal_reactors.network.ReactorBuilderBuildPayload;
import net.unfamily.colossal_reactors.compat.jei.ghost.IColossalGhostTarget;
import net.unfamily.colossal_reactors.network.BuilderMarkFilterSetPayload;
import net.unfamily.colossal_reactors.network.ReactorBuilderMarkInputPayload;
import net.minecraft.client.renderer.Rect2i;
import net.unfamily.colossal_reactors.network.ReactorBuilderHeatSinkPayload;
import net.unfamily.colossal_reactors.network.ReactorBuilderOptionPayload;
import net.unfamily.colossal_reactors.network.ReactorBuilderSimSettingsPayload;
import net.unfamily.colossal_reactors.network.ReactorBuilderSizePayload;
import net.unfamily.colossal_reactors.network.ReactorBuilderWebsiteDesignPayload;
import net.unfamily.colossal_reactors.builder.web.BuilderWebsiteDesigns;
import net.unfamily.colossal_reactors.network.FluidTankDumpPayload;
import net.unfamily.colossal_reactors.client.BuilderPreviewTracker;
import net.unfamily.colossal_reactors.network.BuilderPreviewTogglePayload;
import net.unfamily.colossal_reactors.network.ReactorPreviewPayload;
import net.unfamily.colossal_reactors.Config;
import net.unfamily.colossal_reactors.blockentity.ReactorRodBlockEntity;
import net.unfamily.colossal_reactors.coolant.CoolantDefinition;
import net.unfamily.colossal_reactors.coolant.CoolantLoader;
import net.unfamily.colossal_reactors.fuel.FuelLoader;
import net.unfamily.colossal_reactors.reactor.ReactorBuildMaterialCounter;
import net.unfamily.colossal_reactors.reactor.ReactorSimulation;
import net.unfamily.colossal_reactors.reactor.RodPatternLogic;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * Reactor Builder GUI. Background reactor_builder.png 250x250; simulation uses reactor_controller.png 230x240.
 * Like Deep Drawer Extractor: Simulation is a view mode (isSimulationView) on the same screen, not a separate Screen.
 */
public class ReactorBuilderScreen extends AbstractContainerScreen<ReactorBuilderMenu>
        implements IColossalGhostTarget {

    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
            ColossalReactors.MODID, "textures/gui/reactor_builder.png");
    private static final int BUILDER_GUI_WIDTH = 250;
    private static final int GUI_WIDTH = BUILDER_GUI_WIDTH;
    /** Builder inventory GUI ({@code reactor_builder.png}). */
    private static final int BUILDER_GUI_HEIGHT = 250;
    /** Reactor panel in simulation view ({@code reactor_controller.png}). */
    private static final int REACTOR_GUI_HEIGHT = ReactorControllerGui.HEIGHT;
    private static final String WEBSITE_URL =
            "https://moddecoded.com/tools/colossal-reactors/reactor-calculator/";

    /** Close button (X): top right */
    private static final int CLOSE_BUTTON_Y = 5;
    private static final int CLOSE_BUTTON_SIZE = 12;
    private static final int CLOSE_BUTTON_X = GUI_WIDTH - CLOSE_BUTTON_SIZE - 5;

    /**
     * Tank at (12, 26), same dimensions as Resource Port: 12x54 fill with 1px inset
     */
    private static final int FLUID_BAR_X = 12;
    private static final int FLUID_BAR_Y = 28;
    private static final int FLUID_FILL_WIDTH = 12;
    private static final int FLUID_FILL_HEIGHT = 54;
    private static final int FLUID_FILL_INSET = 1;

    /** Dump (D): same size as build arrows; centered under the fluid tank. */
    private static final int DUMP_BUTTON_W = 14;
    private static final int DUMP_BUTTON_H = 12;
    private static final int DUMP_BUTTON_GAP_BELOW = 3;
    private static final int TANK_OUTER_W = FLUID_FILL_WIDTH + 2 * FLUID_FILL_INSET;
    private static final int TANK_OUTER_H = FLUID_FILL_HEIGHT + 2 * FLUID_FILL_INSET;
    private static final int FLUID_DUMP_X = FLUID_BAR_X + (TANK_OUTER_W - DUMP_BUTTON_W) / 2;
    private static final int FLUID_DUMP_Y = FLUID_BAR_Y + TANK_OUTER_H + DUMP_BUTTON_GAP_BELOW;

    /**
     * Size above buttons (centered). Buffer/inventory: left 35, right 35+9*18=197.
     */
    private static final int SIZE_LABEL_Y = 18;
    /**
     * Area buttons: ^ on row1; < V > on row2. Arrow block aligned with inventory left edge (35).
     */
    private static final int BUTTON_W = 14;
    private static final int BUTTON_H = 12;
    private static final int ROW1_Y = 28;
    private static final int ROW2_Y = 42;
    private static final int GAP = 3;
    private static final int INVENTORY_LEFT_X = 45;
    private static final int ARROW_GROUP_WIDTH = 3 * BUTTON_W + 2 * GAP;  // 48
    /**
     * Arrow block left edge = inventory left edge (35).
     */
    /** Arrow / Center / Preview sit just right of the tank (inventory stays at {@link #INVENTORY_LEFT_X}). */
    private static final int GROUP_LEFT_X = FLUID_BAR_X + TANK_OUTER_W + GAP;
    private static final int BUTTON_UP_X = GROUP_LEFT_X + ARROW_GROUP_WIDTH / 2 - BUTTON_W / 2;
    private static final int BUTTON_OFFSET_L_X = BUTTON_UP_X - BUTTON_W - GAP;
    private static final int BUTTON_OFFSET_R_X = BUTTON_UP_X + BUTTON_W + GAP;
    private static final int BUTTON_LEFT_X = GROUP_LEFT_X;
    private static final int BUTTON_DOWN_X = GROUP_LEFT_X + BUTTON_W + GAP;
    private static final int BUTTON_RIGHT_X = GROUP_LEFT_X + 2 * (BUTTON_W + GAP);
    private static final int CENTER_BUTTON_Y = ROW2_Y + BUTTON_H + GAP;
    private static final int CENTER_BUTTON_W = ARROW_GROUP_WIDTH;
    private static final int CENTER_BUTTON_X = GROUP_LEFT_X;
    /** Must match {@link net.unfamily.colossal_reactors.menu.ReactorBuilderMenu} buffer Y. */
    private static final int BUFFER_INV_Y = 102;
    /** Mark Input + right row 1 sit above the buffer with a clear gap. */
    private static final int MARK_INPUT_BUTTON_Y = BUFFER_INV_Y - BUTTON_H - GAP;
    /**
     * Warnings sit beside Center (same row), not under it.
     */
    private static final int STATUS_MESSAGE_X = CENTER_BUTTON_X + CENTER_BUTTON_W + GAP;
    private static final int STATUS_MESSAGE_Y = CENTER_BUTTON_Y;

    /**
     * 6 buttons: 3 cols x 2 rows
     * (Pattern, PatternMode, HeatSink, OpenTop, Simulation, Build/Stop).
     * On Website: PatternMode is replaced by Open Link; HeatSink stays visible blank/inactive.
     */
    private static final int RIGHT_EDGE_INSET = 12;
    /** Sit immediately after the arrow cluster (same gap as on 230), then fill to the right inset. */
    private static final int RIGHT_BLOCK_X = GROUP_LEFT_X + ARROW_GROUP_WIDTH + GAP;
    private static final int RIGHT_BUTTON_W =
            (GUI_WIDTH - RIGHT_EDGE_INSET - RIGHT_BLOCK_X - 2 * GAP) / 3; // 45 on 250-wide
    private static final int RIGHT_BUTTON_H = BUTTON_H;
    private static final int RIGHT_COL0_X = RIGHT_BLOCK_X;
    private static final int RIGHT_COL1_X = RIGHT_BLOCK_X + RIGHT_BUTTON_W + GAP;
    private static final int RIGHT_COL2_X = RIGHT_BLOCK_X + 2 * (RIGHT_BUTTON_W + GAP);
    private static final int RIGHT_ROW0_Y = ROW1_Y;
    private static final int RIGHT_ROW1_Y = MARK_INPUT_BUTTON_Y;
    private static final int BUILD_BUTTON_COL_X = RIGHT_COL2_X;

    /** Preview under Center, same width as Center. */
    private static final int PREVIEW_BUTTON_Y = CENTER_BUTTON_Y + BUTTON_H + GAP;
    private static final int PREVIEW_BUTTON_W = CENTER_BUTTON_W;
    private static final int PREVIEW_BUTTON_X = CENTER_BUTTON_X;
    private static final int OPEN_LINK_BUTTON_X = RIGHT_COL1_X;
    private static final int OPEN_LINK_BUTTON_Y = RIGHT_ROW0_Y;
    /** Seed EditBox after ←/−/→ (row 2); same width as the 3 right-button columns (not past their edge). */
    private static final int DESIGN_EDIT_X = RIGHT_COL0_X;
    private static final int DESIGN_EDIT_Y = ROW2_Y;
    private static final int DESIGN_EDIT_W = 3 * RIGHT_BUTTON_W + 2 * GAP;
    private static final int DESIGN_EDIT_H = BUTTON_H;

    private int buildPercentTextX(Component percentText) {
        return BUILD_BUTTON_COL_X + (RIGHT_BUTTON_W - font.width(percentText)) / 2;
    }

    private static int buildPercentTextY(int lineHeight) {
        return RIGHT_ROW1_Y - lineHeight - GAP;
    }

    private static int buildInvalidBlocksTextY(int lineHeight) {
        return STATUS_MESSAGE_Y + (BUTTON_H - lineHeight) / 2;
    }

    /** Simulation view panel (same layout as reactor controller). */
    private static final int SIM_PANEL_X = 16;
    private static final int SIM_PANEL_Y = GuiPanelScrollbar.TEXT_TOP;
    private static final int SIM_LINE_HEIGHT = 12;
    /** Stats lines on simulation panel (same as reactor controller GUI). */
    private static final int SIM_STATS_COLOR = GuiTextColors.PANEL_WHITE;

    /** Coolant/Fuel cycle buttons: same horizontal inset each side (12px), reduced gap, width split equally. */
    private static final int SIM_BUTTONS_HORIZONTAL_INSET = 12;
    private static final int SIM_BUTTONS_GAP = 2;
    private static final int COOLANT_BUTTON_W = (GUI_WIDTH - SIM_BUTTONS_HORIZONTAL_INSET * 2 - SIM_BUTTONS_GAP) / 2;
    private static final int FUEL_BUTTON_W = COOLANT_BUTTON_W;
    private static final int COOLANT_BUTTON_H = 20;
    private static final int COOLANT_BUTTON_RIGHT_INSET = SIM_BUTTONS_HORIZONTAL_INSET;
    private static final int COOLANT_BUTTON_BOTTOM_INSET = 13;

    private enum ViewMode { BUILDER, SIMULATION, WEBSITE_LINK }

    /** Builder / simulation view on the same screen (like Deep Drawer sub-views). */
    private ViewMode viewMode = ViewMode.BUILDER;

    private static final String TOOLTIP_LEFT_CLICK = "gui.colossal_reactors.reactor_builder.tooltip.left_click";
    private static final String TOOLTIP_RIGHT_CLICK = "gui.colossal_reactors.reactor_builder.tooltip.right_click";
    private static final String TOOLTIP_SHIFT_10 = "gui.colossal_reactors.reactor_builder.tooltip.shift_10";
    private static final String TOOLTIP_ALT_CTRL_5 = "gui.colossal_reactors.reactor_builder.tooltip.alt_ctrl_5";

    private static Tooltip tooltipWithValue(String valueKey, int value) {
        Component full = Component.translatable(valueKey, value)
                .append(Component.literal("\n"))
                .append(Component.translatable(TOOLTIP_LEFT_CLICK))
                .append(Component.literal("\n"))
                .append(Component.translatable(TOOLTIP_RIGHT_CLICK))
                .append(Component.literal("\n"))
                .append(Component.translatable(TOOLTIP_SHIFT_10))
                .append(Component.literal("\n"))
                .append(Component.translatable(TOOLTIP_ALT_CTRL_5));
        return Tooltip.create(full);
    }

    private Button closeButton;
    private Button buttonUp;
    private Button buttonOffsetLeft;
    private Button buttonOffsetRight;
    private Button buttonCenter;
    private Button buttonLeft;
    private Button buttonRight;
    private Button buttonDown;
    private Button buttonPreview;
    private boolean previewButtonShowsHide;
    private int lastPreviewSettingsHash;
    private Button buttonMarkInput;
    private Button buttonDumpFluid;
    private Button buttonOpenLink;
    private EditBox designEditBox;
    private Button linkOpenButton;
    private Button linkCopyButton;
    private Button linkBackButton;
    private Component websiteStatusMessage = Component.empty();
    private int lastMarkClickSlotIndex = -1;
    private long lastMarkClickTime;
    /** Right block: 0=Pattern, 1=PatternMode, 2=HeatSink, 3=OpenTop, 4=Simulation, 5=Build/Stop. */
    private final Button[] rightBlockButtons = new Button[6];
    /** Shown only in simulation view: cycles coolant type (same position as Reboot in controller). */
    private Button coolantCycleButton;
    /** Shown only in simulation view: cycles fuel type (next to coolant). */
    private Button fuelCycleButton;
    private final GuiPanelScrollbar simulationScrollbar = new GuiPanelScrollbar();

    public ReactorBuilderScreen(ReactorBuilderMenu menu, Inventory playerInventory, Component title) {
        // Builder texture 250x250; simulation still blits the 240-tall controller background at the top.
        super(menu, playerInventory, title, GUI_WIDTH, BUILDER_GUI_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        closeButton = Button.builder(Component.literal("\u2715"), b -> onCloseButtonClicked())
                .bounds(leftPos + CLOSE_BUTTON_X, topPos + CLOSE_BUTTON_Y, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE)
                .build();
        addRenderableWidget(closeButton);
        buttonDumpFluid = Button.builder(Component.literal("D"), b -> onFluidDumpPressed())
                .bounds(leftPos + FLUID_DUMP_X, topPos + FLUID_DUMP_Y, DUMP_BUTTON_W, DUMP_BUTTON_H)
                .build();
        buttonDumpFluid.setTooltip(Tooltip.create(Component.translatable("gui.colossal_reactors.fluid_dump.tooltip")));
        addRenderableWidget(buttonDumpFluid);
        buttonUp = Button.builder(Component.literal("\u2191"), b -> sendSize(0, true))  // ↑
                .bounds(leftPos + BUTTON_UP_X, topPos + ROW1_Y, BUTTON_W, BUTTON_H)
                .build();
        buttonOffsetLeft = Button.builder(Component.literal("\u00AB"), b -> sendSize(4, true))
                .bounds(leftPos + BUTTON_OFFSET_L_X, topPos + ROW1_Y, BUTTON_W, BUTTON_H)
                .build();
        buttonOffsetRight = Button.builder(Component.literal("\u00BB"), b -> sendSize(5, true))
                .bounds(leftPos + BUTTON_OFFSET_R_X, topPos + ROW1_Y, BUTTON_W, BUTTON_H)
                .build();
        buttonLeft = Button.builder(Component.literal("\u2190"), b -> sendSize(1, true))  // ← changes first value (L = sizeRight)
                .bounds(leftPos + BUTTON_LEFT_X, topPos + ROW2_Y, BUTTON_W, BUTTON_H)
                .build();
        buttonDown = Button.builder(Component.literal("-"), b -> sendSize(3, true))  // Z/depth
                .bounds(leftPos + BUTTON_DOWN_X, topPos + ROW2_Y, BUTTON_W, BUTTON_H)
                .build();
        buttonRight = Button.builder(Component.literal("\u2192"), b -> sendSize(2, true))  // → changes second value (R = sizeLeft)
                .bounds(leftPos + BUTTON_RIGHT_X, topPos + ROW2_Y, BUTTON_W, BUTTON_H)
                .build();
        addRenderableWidget(buttonUp);
        addRenderableWidget(buttonOffsetLeft);
        addRenderableWidget(buttonOffsetRight);
        addRenderableWidget(buttonLeft);
        addRenderableWidget(buttonDown);
        addRenderableWidget(buttonRight);

        buttonCenter = Button.builder(Component.translatable("gui.colossal_reactors.builder.center"), b -> sendSize(6, true))
                .bounds(leftPos + CENTER_BUTTON_X, topPos + CENTER_BUTTON_Y, CENTER_BUTTON_W, BUTTON_H)
                .build();
        addRenderableWidget(buttonCenter);

        buttonPreview = Button.builder(Component.translatable("gui.colossal_reactors.reactor_builder.preview"), b -> togglePreview())
                .bounds(leftPos + PREVIEW_BUTTON_X, topPos + PREVIEW_BUTTON_Y, PREVIEW_BUTTON_W, BUTTON_H)
                .build();
        buttonPreview.setTooltip(Tooltip.create(
                Component.translatable("gui.colossal_reactors.reactor_builder.preview.tooltip.line1")
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.reactor_builder.preview.tooltip.line2"))
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.reactor_builder.preview.tooltip.line3"))
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.reactor_builder.preview.tooltip.line4"))
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.reactor_builder.preview.tooltip.line5"))));
        addRenderableWidget(buttonPreview);

        buttonOpenLink = Button.builder(Component.translatable("gui.colossal_reactors.builder.open_link"), b -> switchToWebsiteLinkView())
                .bounds(leftPos + OPEN_LINK_BUTTON_X, topPos + OPEN_LINK_BUTTON_Y, RIGHT_BUTTON_W, BUTTON_H)
                .build();
        addRenderableWidget(buttonOpenLink);

        designEditBox = new EditBox(font, leftPos + DESIGN_EDIT_X, topPos + DESIGN_EDIT_Y, DESIGN_EDIT_W, DESIGN_EDIT_H,
                Component.translatable("gui.colossal_reactors.builder.website_design"));
        designEditBox.setMaxLength(512);
        designEditBox.setTooltip(Tooltip.create(Component.translatable("gui.colossal_reactors.builder.website_design.tooltip")));
        designEditBox.setResponder(this::onDesignEdited);
        if (menu.getBlockEntity() != null) {
            String code = menu.getBlockEntity().getWebsiteDesign();
            if (code != null && !code.isEmpty()) {
                designEditBox.setValue(code);
            }
        }
        addRenderableWidget(designEditBox);

        int linkBtnY = BuilderWebsiteLinkGui.buttonY(topPos);
        int linkBtnW = BuilderWebsiteLinkGui.buttonWidth();
        linkOpenButton = Button.builder(Component.translatable("gui.colossal_reactors.builder.open_link"),
                        b -> Util.getPlatform().openUri(WEBSITE_URL))
                .bounds(BuilderWebsiteLinkGui.buttonX(leftPos, 0), linkBtnY, linkBtnW, BuilderWebsiteLinkGui.BUTTON_H).build();
        linkCopyButton = Button.builder(Component.translatable("gui.colossal_reactors.builder.copy_link"),
                        b -> {
                            if (minecraft != null) {
                                minecraft.keyboardHandler.setClipboard(WEBSITE_URL);
                            }
                        })
                .bounds(BuilderWebsiteLinkGui.buttonX(leftPos, 1), linkBtnY, linkBtnW, BuilderWebsiteLinkGui.BUTTON_H).build();
        linkBackButton = Button.builder(Component.translatable("gui.colossal_reactors.builder.back"),
                        b -> switchToBuilderView())
                .bounds(BuilderWebsiteLinkGui.buttonX(leftPos, 2), linkBtnY, linkBtnW, BuilderWebsiteLinkGui.BUTTON_H).build();
        addRenderableWidget(linkOpenButton);
        addRenderableWidget(linkCopyButton);
        addRenderableWidget(linkBackButton);

        buttonMarkInput = Button.builder(Component.translatable("gui.colossal_reactors.reactor_builder.mark_input"), b -> onMarkInputPressed())
                .bounds(leftPos + CENTER_BUTTON_X, topPos + MARK_INPUT_BUTTON_Y, CENTER_BUTTON_W, BUTTON_H)
                .build();
        buttonMarkInput.setTooltip(Tooltip.create(
                Component.translatable("gui.colossal_reactors.reactor_builder.mark_input.tooltip.line1")
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.reactor_builder.mark_input.tooltip.line2"))
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.reactor_builder.mark_input.tooltip.line3"))));
        addRenderableWidget(buttonMarkInput);

        // Right block: 0=Pattern, 1=PatternMode, 2=HeatSink, 3=OpenTop, 4=Simulation, 5=Build/Stop.
        int[] rowY = {RIGHT_ROW0_Y, RIGHT_ROW1_Y};
        int[] colX = {RIGHT_COL0_X, RIGHT_COL1_X, RIGHT_COL2_X};
        for (int i = 0; i < 6; i++) {
            int row = i / 3;
            int col = i % 3;
            Component label = getRightButtonLabel(i);
            final int buttonIndex = i;
            rightBlockButtons[i] = Button.builder(label, b -> onRightBlockClick(buttonIndex))
                    .bounds(leftPos + colX[col], topPos + rowY[row], RIGHT_BUTTON_W, RIGHT_BUTTON_H)
                    .build();
            addRenderableWidget(rightBlockButtons[i]);
        }
        int coolantY = topPos + REACTOR_GUI_HEIGHT - COOLANT_BUTTON_H - COOLANT_BUTTON_BOTTOM_INSET;
        int fuelX = leftPos + SIM_BUTTONS_HORIZONTAL_INSET;
        int coolantX = fuelX + FUEL_BUTTON_W + SIM_BUTTONS_GAP;
        coolantCycleButton = Button.builder(Component.translatable("gui.colossal_reactors.reactor_builder.simulation.coolant_none"), b -> cycleSimulationCoolant(true))
                .bounds(coolantX, coolantY, COOLANT_BUTTON_W, COOLANT_BUTTON_H)
                .build();
        addRenderableWidget(coolantCycleButton);
        fuelCycleButton = Button.builder(Component.translatable("gui.colossal_reactors.reactor_builder.simulation.fuel_uranium"), b -> cycleSimulationFuel(true))
                .bounds(fuelX, coolantY, FUEL_BUTTON_W, COOLANT_BUTTON_H)
                .build();
        addRenderableWidget(fuelCycleButton);
        simulationScrollbar.createButtons(leftPos, topPos, this::addRenderableWidget, () -> {});
        updateWidgetVisibility();
        previewButtonShowsHide = BuilderPreviewTracker.isPreviewActive(menu.getBlockPos());
        updatePreviewButtonLabel();
        lastPreviewSettingsHash = computePreviewSettingsHash();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        // Keep EditBox / Open Link visibility in sync when pattern changes (not only on view switch).
        updateWidgetVisibility();
        // Match StructureSaverMachineScreen / DeepDrawers: refresh labels in tick, not every extractLabels frame.
        if (viewMode == ViewMode.BUILDER) {
            updateButtonTooltips();
            BlockPos builderPos = menu.getBlockPos();
            if (BuilderPreviewTracker.isPreviewActive(builderPos)) {
                int hash = computePreviewSettingsHash();
                if (hash != lastPreviewSettingsHash) {
                    lastPreviewSettingsHash = hash;
                    BuilderPreviewTracker.noteRefreshRequested(builderPos);
                    ClientPacketDistributor.sendToServer(new ReactorPreviewPayload(builderPos));
                }
            } else {
                lastPreviewSettingsHash = computePreviewSettingsHash();
            }
        } else {
            updateCoolantButtonLabel();
            updateFuelButtonLabel();
        }
    }

    private int computePreviewSettingsHash() {
        return java.util.Objects.hash(
                menu.getSizeLeft(),
                menu.getSizeRight(),
                menu.getSizeH(),
                menu.getSizeD(),
                menu.getHeatSinkIndex(),
                menu.isOpenTop(),
                menu.getRodPattern(),
                menu.getPatternMode());
    }

    private static List<Identifier> getOrderedCoolantIds() {
        return new ArrayList<>(CoolantLoader.getVisibleCoolantIds());
    }

    private static List<Identifier> getOrderedFuelIds() {
        return new ArrayList<>(FuelLoader.getVisibleFuelIds());
    }

    /** Number of options: 0 = None, then one per coolant. */
    private static int getCoolantOptionCount() {
        return 1 + getOrderedCoolantIds().size();
    }

    private void cycleSimulationCoolant(boolean next) {
        if (menu.getBlockEntity() == null || getCoolantOptionCount() <= 1) return;
        ClientPacketDistributor.sendToServer(new ReactorBuilderSimSettingsPayload(
                menu.getBlockPos(), ReactorBuilderSimSettingsPayload.SETTING_COOLANT, next));
    }

    private void updateCoolantButtonLabel() {
        if (coolantCycleButton == null) return;
        List<Identifier> ids = getOrderedCoolantIds();
        Component clickHint = Component.translatable("gui.colossal_reactors.reactor_builder.simulation.click_hint");
        // Index 0 = None (no coolant)
        MutableComponent coolantTitle = Component.translatable("gui.colossal_reactors.reactor_builder.simulation.coolant_label");
        int simulationCoolantIndex = menu.getSimCoolantIndex();
        if (simulationCoolantIndex == 0) {
            coolantCycleButton.setMessage(Component.translatable("gui.colossal_reactors.reactor_builder.simulation.coolant_none"));
            coolantCycleButton.setTooltip(Tooltip.create(coolantTitle.append(Component.literal("\n")).append(Component.translatable("gui.colossal_reactors.reactor_builder.simulation.coolant_tooltip", "—", "—")).append(Component.literal("\n")).append(clickHint)));
            return;
        }
        int idx = simulationCoolantIndex - 1;
        if (idx >= ids.size()) {
            coolantCycleButton.setMessage(Component.translatable("gui.colossal_reactors.reactor_builder.simulation.coolant_none"));
            coolantCycleButton.setTooltip(Tooltip.create(coolantTitle.append(Component.literal("\n")).append(Component.translatable("gui.colossal_reactors.reactor_builder.simulation.coolant_tooltip", "—", "—")).append(Component.literal("\n")).append(clickHint)));
            return;
        }
        Identifier id = ids.get(idx);
        CoolantDefinition def = CoolantLoader.get(id);
        if (def == null) {
            coolantCycleButton.setMessage(Component.literal(id.toString()));
            coolantCycleButton.setTooltip(Tooltip.create(coolantTitle.append(Component.literal("\n")).append(clickHint)));
            return;
        }
        var level = minecraft != null ? minecraft.level : null;
        var ra = level != null ? level.registryAccess() : null;
        Component label = getCoolantDisplayName(def, ra);
        MutableComponent tooltip = coolantTitle.copy().append(Component.literal("\n")).append(getCoolantTooltip(def, ra)).append(Component.literal("\n")).append(clickHint);
        coolantCycleButton.setMessage(label);
        coolantCycleButton.setTooltip(Tooltip.create(tooltip));
    }

    private static Component getCoolantDisplayName(CoolantDefinition def, net.minecraft.core.RegistryAccess ra) {
        return CoolantLoader.getPrimaryInputDisplayName(def, ra);
    }

    private static Component getCoolantTooltip(CoolantDefinition def, net.minecraft.core.RegistryAccess ra) {
        Component consume = CoolantLoader.getPrimaryInputDisplayName(def, ra);
        Component produce = ra != null ? CoolantLoader.getPrimaryOutputDisplayName(def, ra) : null;
        if (produce == null) {
            produce = Component.literal("—");
        }
        return Component.translatable("gui.colossal_reactors.reactor_builder.simulation.coolant_tooltip", consume, produce);
    }

    private void cycleSimulationFuel(boolean next) {
        if (menu.getBlockEntity() == null || getOrderedFuelIds().isEmpty()) return;
        ClientPacketDistributor.sendToServer(new ReactorBuilderSimSettingsPayload(
                menu.getBlockPos(), ReactorBuilderSimSettingsPayload.SETTING_FUEL, next));
    }

    private void updateFuelButtonLabel() {
        if (fuelCycleButton == null) return;
        List<Identifier> ids = getOrderedFuelIds();
        Component clickHint = Component.translatable("gui.colossal_reactors.reactor_builder.simulation.click_hint");
        MutableComponent fuelTitle = Component.translatable("gui.colossal_reactors.reactor_builder.simulation.fuel_label");
        if (ids.isEmpty()) {
            fuelCycleButton.setMessage(Component.translatable("gui.colossal_reactors.reactor_builder.simulation.fuel_uranium"));
            fuelCycleButton.setTooltip(Tooltip.create(fuelTitle.append(Component.literal("\n")).append(Component.translatable("gui.colossal_reactors.reactor_builder.simulation.fuel_tooltip", "—", "—")).append(Component.literal("\n")).append(clickHint)));
            return;
        }
        int simulationFuelIndex = Math.min(menu.getSimFuelIndex(), ids.size() - 1);
        Identifier id = ids.get(simulationFuelIndex);
        var ra = minecraft != null && minecraft.level != null ? minecraft.level.registryAccess() : null;
        Component label = getFuelDisplayName(id, ra);
        MutableComponent tooltip = fuelTitle.copy().append(Component.literal("\n")).append(getFuelTooltip(id, ra)).append(Component.literal("\n")).append(clickHint);
        fuelCycleButton.setMessage(label);
        fuelCycleButton.setTooltip(Tooltip.create(tooltip));
    }

    /** Tooltip line: Consumes: X, Produces: X (input item and waste item names). */
    private static Component getFuelTooltip(Identifier fuelId, net.minecraft.core.RegistryAccess ra) {
        Component consume = Component.literal("—");
        Component produce = Component.literal("—");
        if (ra != null) {
            ItemStack in = FuelLoader.getFirstInputStack(fuelId, ra);
            if (!in.isEmpty()) consume = in.getHoverName();
            ItemStack out = FuelLoader.getFirstOutputStack(fuelId, ra);
            if (!out.isEmpty()) produce = out.getHoverName();
        }
        return Component.translatable("gui.colossal_reactors.reactor_builder.simulation.fuel_tooltip", consume, produce);
    }

    /** Display name: first input item from tag or fixed id (like coolant). */
    private static Component getFuelDisplayName(Identifier fuelId, net.minecraft.core.RegistryAccess ra) {
        if (ra != null) {
            ItemStack stack = FuelLoader.getFirstInputStack(fuelId, ra);
            if (!stack.isEmpty()) return stack.getHoverName();
        }
        if (ReactorRodBlockEntity.URANIUM_FUEL_ID.equals(fuelId))
            return Component.translatable("gui.colossal_reactors.reactor_builder.simulation.fuel_uranium");
        return Component.literal(fuelId.getPath());
    }

    private void onCloseButtonClicked() {
        if (viewMode != ViewMode.BUILDER) {
            switchToBuilderView();
        } else {
            if (minecraft != null && minecraft.player != null) minecraft.player.closeContainer();
        }
    }

    private boolean isWebsitePattern() {
        return menu.getRodPattern() == RodPatternLogic.PATTERN_WEBSITE;
    }

    private boolean hasValidWebsiteDesign() {
        return menu.getBlockEntity() != null && menu.getBlockEntity().hasValidWebsiteDesign();
    }

    private void onDesignEdited(String value) {
        if (menu.getBlockEntity() == null) return;
        ClientPacketDistributor.sendToServer(new ReactorBuilderWebsiteDesignPayload(menu.getBlockPos(), value == null ? "" : value));
        if (value != null && !value.isBlank() && BuilderWebsiteDesigns.parseReactor(value).isEmpty()) {
            websiteStatusMessage = Component.translatable("gui.colossal_reactors.builder.invalid_website_design");
        } else {
            websiteStatusMessage = Component.empty();
        }
    }

    private void pasteDesignFromClipboard() {
        if (minecraft == null || designEditBox == null) {
            return;
        }
        String clip = minecraft.keyboardHandler.getClipboard();
        if (clip == null) {
            return;
        }
        clip = clip.trim();
        if (clip.isEmpty()) {
            return;
        }
        designEditBox.setValue(clip);
    }

    private void switchToWebsiteLinkView() {
        viewMode = ViewMode.WEBSITE_LINK;
        menu.setHideAllSlotsForSimulationView(true);
        updateWidgetVisibility();
    }

    private void switchToSimulationView() {
        if (isWebsitePattern() && !hasValidWebsiteDesign()) {
            websiteStatusMessage = Component.translatable("gui.colossal_reactors.builder.set_website_design");
            return;
        }
        viewMode = ViewMode.SIMULATION;
        simulationScrollbar.resetScroll();
        menu.setHideAllSlotsForSimulationView(true);
        simulationScrollbar.ensureButtons(leftPos, topPos, this::addRenderableWidget, () -> {});
        updateWidgetVisibility();
        menu.broadcastChanges();
    }

    private void switchToBuilderView() {
        playUiClickSound();
        viewMode = ViewMode.BUILDER;
        simulationScrollbar.disposeButtons(this::removeWidget);
        menu.setHideAllSlotsForSimulationView(false);
        updateWidgetVisibility();
        menu.broadcastChanges();
    }

    private void playUiClickSound() {
        if (minecraft != null && minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private void updateWidgetVisibility() {
        boolean showBuilder = viewMode == ViewMode.BUILDER;
        boolean showLink = viewMode == ViewMode.WEBSITE_LINK;
        boolean website = showBuilder && isWebsitePattern();
        if (closeButton != null) closeButton.visible = true;
        if (buttonUp != null) buttonUp.visible = showBuilder;
        if (buttonOffsetLeft != null) buttonOffsetLeft.visible = showBuilder;
        if (buttonOffsetRight != null) buttonOffsetRight.visible = showBuilder;
        if (buttonCenter != null) buttonCenter.visible = showBuilder;
        // Keep ←/−/→ visible in Website mode but inactive (size comes from the seed).
        if (buttonLeft != null) {
            buttonLeft.visible = showBuilder;
            buttonLeft.active = showBuilder && !website;
        }
        if (buttonRight != null) {
            buttonRight.visible = showBuilder;
            buttonRight.active = showBuilder && !website;
        }
        if (buttonDown != null) {
            buttonDown.visible = showBuilder;
            buttonDown.active = showBuilder && !website;
        }
        if (buttonPreview != null) buttonPreview.visible = showBuilder;
        if (buttonMarkInput != null) buttonMarkInput.visible = showBuilder;
        if (buttonDumpFluid != null) buttonDumpFluid.visible = showBuilder;
        if (buttonOpenLink != null) buttonOpenLink.visible = website;
        if (designEditBox != null) {
            designEditBox.visible = website;
            designEditBox.active = website;
            if (!website) {
                designEditBox.setFocused(false);
            }
        }
        if (linkOpenButton != null) linkOpenButton.visible = showLink;
        if (linkCopyButton != null) linkCopyButton.visible = showLink;
        if (linkBackButton != null) linkBackButton.visible = showLink;
        for (int i = 0; i < rightBlockButtons.length; i++) {
            Button b = rightBlockButtons[i];
            if (b == null) continue;
            // Hide PatternMode (i=1) when website — Open Link occupies that column; HeatSink (i=2) stays blank/inactive.
            if (showLink) {
                b.visible = false;
            } else if (website && i == 1) {
                b.visible = false;
            } else {
                b.visible = showBuilder;
            }
            if (i == 2) {
                b.active = showBuilder && !website;
            }
        }
        if (coolantCycleButton != null) {
            coolantCycleButton.visible = viewMode == ViewMode.SIMULATION;
            if (viewMode == ViewMode.SIMULATION) updateCoolantButtonLabel();
        }
        if (fuelCycleButton != null) {
            fuelCycleButton.visible = viewMode == ViewMode.SIMULATION;
            if (viewMode == ViewMode.SIMULATION) updateFuelButtonLabel();
        }
    }

    private Component getRightButtonLabel(int index) {
        return switch (index) {
            case 0 -> Component.translatable("gui.colossal_reactors.reactor_builder.pattern." + menu.getRodPattern());
            case 1 -> Component.translatable("gui.colossal_reactors.reactor_builder.pattern_mode." + menu.getPatternMode());
            case 2 -> isWebsitePattern() ? Component.empty() : getHeatSinkButtonLabel();
            case 3 -> menu.isOpenTop()
                    ? Component.translatable("gui.colossal_reactors.reactor_builder.open_top.open")
                    : Component.translatable("gui.colossal_reactors.reactor_builder.open_top.closed");
            case 4 -> Component.translatable("gui.colossal_reactors.reactor_builder.simulation");
            case 5 -> menu.isBuilding()
                    ? Component.translatable("gui.colossal_reactors.reactor_builder.stop")
                    : Component.translatable("gui.colossal_reactors.reactor_builder.build");
            default -> Component.literal("?");
        };
    }

    private Component getHeatSinkButtonLabel() {
        if (minecraft != null && minecraft.level != null) {
            return HeatSinkLoader.getOptionDisplayName(minecraft.level.registryAccess(), menu.getHeatSinkIndex());
        }
        return Component.translatable("block.minecraft.air");
    }

    private void onRightBlockClick(int index) {
        if (index == 4) {
            switchToSimulationView();
            return;
        }
        if (menu.getBlockEntity() == null) return;
        BlockPos pos = menu.getBlockPos();
        if (index == 0) ClientPacketDistributor.sendToServer(new ReactorBuilderOptionPayload(pos, 1, true));  // Pattern next
        if (index == 1) {
            if (isWebsitePattern()) return;  // replaced by Open Link
            ClientPacketDistributor.sendToServer(new ReactorBuilderOptionPayload(pos, 2, true));  // PatternMode
        }
        if (index == 2) {
            if (isWebsitePattern()) return;  // blank / inactive on Website
            ClientPacketDistributor.sendToServer(new ReactorBuilderHeatSinkPayload(pos, true));
            return;
        }
        if (index == 3) ClientPacketDistributor.sendToServer(new ReactorBuilderOptionPayload(pos, 0, true));  // OpenTop
        if (index == 5) {
            if (isWebsitePattern() && !hasValidWebsiteDesign() && !menu.isBuilding()) {
                websiteStatusMessage = Component.translatable("gui.colossal_reactors.builder.set_website_design");
                return;
            }
            ClientPacketDistributor.sendToServer(new ReactorBuilderBuildPayload(pos));  // Build/Stop
        }
    }

    private void onFluidDumpPressed() {
        if (menu.getBlockEntity() == null) return;
        ClientPacketDistributor.sendToServer(new FluidTankDumpPayload(menu.getBlockPos()));
    }

    private void onMarkInputPressed() {
        if (menu.getBlockEntity() == null) return;
        int mode = ReactorBuilderMarkInputPayload.MODE_NORMAL;
        if (minecraft != null && minecraft.getWindow() != null) {
            Window w = minecraft.getWindow();
            if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
                mode = ReactorBuilderMarkInputPayload.MODE_SHIFT;
            } else if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_CONTROL)
                    || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_ALT) || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_ALT)) {
                mode = ReactorBuilderMarkInputPayload.MODE_CTRL;
            }
        }
        ClientPacketDistributor.sendToServer(new ReactorBuilderMarkInputPayload(menu.getBlockEntity().getBlockPos(), mode));
    }

    private void updateButtonTooltips() {
        buttonUp.setTooltip(tooltipWithValue("gui.colossal_reactors.reactor_builder.tooltip.up_value", menu.getSizeH() + 1));
        buttonLeft.setTooltip(tooltipWithValue("gui.colossal_reactors.reactor_builder.tooltip.left_value", menu.getSizeLeft()));
        buttonRight.setTooltip(tooltipWithValue("gui.colossal_reactors.reactor_builder.tooltip.right_value", menu.getSizeRight()));
        buttonDown.setTooltip(tooltipWithValue("gui.colossal_reactors.reactor_builder.tooltip.behind_value", menu.getSizeD() + 1));
        // Button 0: Pattern
        rightBlockButtons[0].setTooltip(Tooltip.create(
                Component.translatable("gui.colossal_reactors.reactor_builder.tooltip.pattern." + menu.getRodPattern())
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.reactor_builder.tooltip.heat_sink_left"))
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.reactor_builder.tooltip.heat_sink_right"))));
        rightBlockButtons[0].setMessage(getRightButtonLabel(0));
        // Button 1: Pattern mode (hidden on Website — Open Link uses that column)
        if (!isWebsitePattern()) {
            rightBlockButtons[1].setTooltip(Tooltip.create(
                    Component.translatable("gui.colossal_reactors.reactor_builder.tooltip.pattern_mode." + menu.getPatternMode())
                            .append(Component.literal("\n"))
                            .append(Component.translatable("gui.colossal_reactors.reactor_builder.tooltip.heat_sink_left"))
                            .append(Component.literal("\n"))
                            .append(Component.translatable("gui.colossal_reactors.reactor_builder.tooltip.heat_sink_right"))));
            rightBlockButtons[1].setMessage(getRightButtonLabel(1));
        }
        // Button 2: Heat sink (last of row 0; blank + inactive on Website)
        if (isWebsitePattern()) {
            rightBlockButtons[2].setMessage(Component.empty());
            rightBlockButtons[2].setTooltip(null);
        } else {
            Component heatSinkName = getHeatSinkButtonLabel();
            rightBlockButtons[2].setMessage(heatSinkName);
            rightBlockButtons[2].setTooltip(Tooltip.create(
                    Component.translatable("gui.colossal_reactors.reactor_builder.tooltip.heat_sink_value", heatSinkName)
                            .append(Component.literal("\n"))
                            .append(Component.translatable("gui.colossal_reactors.reactor_builder.tooltip.heat_sink_left"))
                            .append(Component.literal("\n"))
                            .append(Component.translatable("gui.colossal_reactors.reactor_builder.tooltip.heat_sink_right"))));
        }
        // Button 3: Open top (click cycles open/closed)
        rightBlockButtons[3].setTooltip(Tooltip.create(
                Component.translatable(menu.isOpenTop() ? "gui.colossal_reactors.reactor_builder.tooltip.open_top.open" : "gui.colossal_reactors.reactor_builder.tooltip.open_top.closed")));
        rightBlockButtons[3].setMessage(getRightButtonLabel(3));
        // Button 4: Simulation
        rightBlockButtons[4].setMessage(getRightButtonLabel(4));
        rightBlockButtons[4].setTooltip(Tooltip.create(Component.translatable("gui.colossal_reactors.reactor_builder.simulation.tooltip")));
        // Button 5: Build/Stop
        rightBlockButtons[5].setMessage(getRightButtonLabel(5));
        rightBlockButtons[5].setTooltip(Tooltip.create(Component.translatable(
                menu.isBuilding() ? "gui.colossal_reactors.reactor_builder.stop.tooltip" : "gui.colossal_reactors.reactor_builder.build.tooltip")));
    }

    private int modifierStepAmount() {
        if (minecraft == null || minecraft.getWindow() == null) return 1;
        Window w = minecraft.getWindow();
        if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            return 10;
        }
        if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_CONTROL)
                || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_ALT) || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_ALT)) {
            return 5;
        }
        return 1;
    }

    private void sendSize(int direction, boolean increment) {
        if (menu.getBlockEntity() == null) return;
        BlockPos pos = menu.getBlockPos();
        int amount = modifierStepAmount();
        ClientPacketDistributor.sendToServer(new ReactorBuilderSizePayload(pos, direction, increment, amount));
    }

    /** Click feedback for interactions that do not go through {@link Button} (e.g. right-click on arrows). */
    private void playGuiClickSound() {
        if (minecraft != null && minecraft.getSoundManager() != null) {
            AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
        }
    }

    @Override
    public List<GhostDropTarget> getGhostDropTargets() {
        if (viewMode != ViewMode.BUILDER || menu.getBlockEntity() == null) {
            return List.of();
        }
        var pos = menu.getBlockEntity().getBlockPos();
        List<GhostDropTarget> targets = new ArrayList<>();
        for (int i = 0; i < ReactorBuilderMenu.BUFFER_SLOTS; i++) {
            Slot slot = menu.getSlot(i);
            if (!slot.isActive()) continue;
            int slotIndex = i;
            targets.add(new GhostDropTarget(
                    new Rect2i(leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18),
                    stack -> ClientPacketDistributor.sendToServer(
                            new BuilderMarkFilterSetPayload(pos, slotIndex, stack.copyWithCount(1)))));
        }
        return targets;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (viewMode == ViewMode.SIMULATION && event.button() == 0 && simulationScrollbar.mouseClicked(event.x(), event.y(), leftPos, topPos)) {
            return true;
        }
        int x = (int) event.x();
        int y = (int) event.y();
        if (event.button() == 2 && designEditBox != null && designEditBox.visible && isInWidget(x, y, designEditBox)) {
            pasteDesignFromClipboard();
            return true;
        }
        if (event.button() == 0 && viewMode == ViewMode.BUILDER && hoveredSlot != null
                && hoveredSlot.index >= 0 && hoveredSlot.index < ReactorBuilderMenu.BUFFER_SLOTS
                && menu.getBlockEntity() != null
                && minecraft != null && minecraft.getWindow() != null) {
            Window w = minecraft.getWindow();
            boolean shift = InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_SHIFT)
                    || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_SHIFT);
            if (!shift) {
                long now = System.currentTimeMillis();
                boolean isDouble = hoveredSlot.index == lastMarkClickSlotIndex && now - lastMarkClickTime <= 250L;
                lastMarkClickSlotIndex = hoveredSlot.index;
                lastMarkClickTime = now;
                if (isDouble && hoveredSlot.getItem().isEmpty() && menu.hasMarkInputFilter(hoveredSlot.index)) {
                    ClientPacketDistributor.sendToServer(new BuilderMarkFilterSetPayload(
                            menu.getBlockEntity().getBlockPos(), hoveredSlot.index, ItemStack.EMPTY));
                    return true;
                }
            }
        }
        if (event.button() == 1) {
            if (designEditBox != null && designEditBox.visible && isInWidget(x, y, designEditBox)) {
                designEditBox.setValue("");
                onDesignEdited("");
                playGuiClickSound();
                return true;
            }
            if (viewMode == ViewMode.SIMULATION && coolantCycleButton != null && coolantCycleButton.visible && isInWidget(x, y, coolantCycleButton)) {
                playGuiClickSound();
                cycleSimulationCoolant(false);
                return true;
            }
            if (viewMode == ViewMode.SIMULATION && fuelCycleButton != null && fuelCycleButton.visible && isInWidget(x, y, fuelCycleButton)) {
                playGuiClickSound();
                cycleSimulationFuel(false);
                return true;
            }
            if (buttonOffsetLeft != null && buttonOffsetLeft.visible && isInWidget(x, y, buttonOffsetLeft)) {
                playGuiClickSound();
                sendSize(4, false);
                return true;
            }
            if (buttonOffsetRight != null && buttonOffsetRight.visible && isInWidget(x, y, buttonOffsetRight)) {
                playGuiClickSound();
                sendSize(5, false);
                return true;
            }
            if (isInBounds(x, y, leftPos + BUTTON_UP_X, topPos + ROW1_Y)) {
                playGuiClickSound();
                sendSize(0, false);
                return true;
            }
            if (buttonLeft != null && buttonLeft.active && isInBounds(x, y, leftPos + BUTTON_LEFT_X, topPos + ROW2_Y)) {
                playGuiClickSound();
                sendSize(1, false);
                return true;
            }
            if (buttonDown != null && buttonDown.active && isInBounds(x, y, leftPos + BUTTON_DOWN_X, topPos + ROW2_Y)) {
                playGuiClickSound();
                sendSize(3, false);
                return true;
            }
            if (buttonRight != null && buttonRight.active && isInBounds(x, y, leftPos + BUTTON_RIGHT_X, topPos + ROW2_Y)) {
                playGuiClickSound();
                sendSize(2, false);
                return true;
            }
            if (isInRightBlockButton(x, y, 0)) {
                playGuiClickSound();
                if (menu.getBlockEntity() != null)
                    ClientPacketDistributor.sendToServer(new ReactorBuilderOptionPayload(menu.getBlockPos(), 1, false));  // Pattern previous
                return true;
            }
            if (isInRightBlockButton(x, y, 1) && !isWebsitePattern()) {
                playGuiClickSound();
                if (menu.getBlockEntity() != null)
                    ClientPacketDistributor.sendToServer(new ReactorBuilderOptionPayload(menu.getBlockPos(), 2, false));  // PatternMode
                return true;
            }
            if (isInRightBlockButton(x, y, 2)) {
                playGuiClickSound();
                if (menu.getBlockEntity() != null && !isWebsitePattern())
                    ClientPacketDistributor.sendToServer(new ReactorBuilderHeatSinkPayload(menu.getBlockPos(), false));  // HeatSink
                return true;
            }
            if (isInRightBlockButton(x, y, 3)) {
                playGuiClickSound();
                if (menu.getBlockEntity() != null)
                    ClientPacketDistributor.sendToServer(new ReactorBuilderOptionPayload(menu.getBlockPos(), 0, true));  // toggle OpenTop
                return true;
            }
        }
        // Vanilla widgets: ContainerEventHandler picks the first child where isMouseOver (forward list order).
        // Widgets added later are drawn on top but lose hits when rects overlap; temporarily reverse so top-most wins.
        @SuppressWarnings("unchecked")
        List<GuiEventListener> listeners = (List<GuiEventListener>) (List<?>) children();
        Collections.reverse(listeners);
        try {
            return super.mouseClicked(event, doubleClick);
        } finally {
            Collections.reverse(listeners);
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            simulationScrollbar.mouseReleased();
        }
        return super.mouseReleased(event);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        simulationScrollbar.mouseMoved(mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (viewMode == ViewMode.SIMULATION
                && simulationScrollbar.isInPanelArea(mouseX, mouseY, leftPos, topPos, SIM_PANEL_X)
                && simulationScrollbar.mouseScrolled(scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static boolean isInBounds(int x, int y, int left, int top) {
        return x >= left && x < left + BUTTON_W && y >= top && y < top + BUTTON_H;
    }

    private static boolean isInWidget(int x, int y, AbstractWidget widget) {
        return x >= widget.getX() && x < widget.getX() + widget.getWidth() && y >= widget.getY() && y < widget.getY() + widget.getHeight();
    }

    private boolean isInRightBlockButton(int x, int y, int index) {
        int row = index / 3;
        int col = index % 3;
        int left = leftPos + (col == 0 ? RIGHT_COL0_X : col == 1 ? RIGHT_COL1_X : RIGHT_COL2_X);
        int top = topPos + (row == 0 ? RIGHT_ROW0_Y : RIGHT_ROW1_Y);
        return x >= left && x < left + RIGHT_BUTTON_W && y >= top && y < top + RIGHT_BUTTON_H;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        if (viewMode == ViewMode.WEBSITE_LINK) {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, BuilderWebsiteLinkGui.BACKGROUND, leftPos, topPos, 0.0F, 0.0F,
                    BuilderWebsiteLinkGui.WIDTH, BuilderWebsiteLinkGui.HEIGHT,
                    BuilderWebsiteLinkGui.WIDTH, BuilderWebsiteLinkGui.HEIGHT);
        } else if (viewMode == ViewMode.SIMULATION) {
            int ctrlW = ReactorControllerGui.WIDTH;
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, ReactorControllerGui.BACKGROUND, leftPos, topPos, 0.0F, 0.0F, ctrlW, REACTOR_GUI_HEIGHT, ctrlW, REACTOR_GUI_HEIGHT);
        } else {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0F, 0.0F, GUI_WIDTH, BUILDER_GUI_HEIGHT, GUI_WIDTH, BUILDER_GUI_HEIGHT);
            int amount = menu.getFluidAmount();
            int capacity = menu.getFluidCapacity();
            int fluidId = menu.getFluidId();
            if (capacity > 0 && amount > 0 && fluidId >= 0) {
                Fluid fluid = BuiltInRegistries.FLUID.byId(fluidId);
                if (fluid != null && fluid != Fluids.EMPTY) {
                    int fillPixels = (FLUID_FILL_HEIGHT * amount) / capacity;
                    if (fillPixels > 0) {
                        int barLeft = leftPos + FLUID_BAR_X + FLUID_FILL_INSET;
                        int barBottom = topPos + FLUID_BAR_Y + FLUID_FILL_INSET + FLUID_FILL_HEIGHT;
                        int fillTop = barBottom - fillPixels;
                        FluidRenderHelper.drawFluidInTank(guiGraphics, new FluidStack(fluid, amount), barLeft, fillTop, FLUID_FILL_WIDTH, fillPixels);
                    }
                }
            }
        }
    }

    /** GUI-local coords inside {@code extractContents} translate. Order: status+stats, blank, required-for-build. */
    private int renderSimulationPanel(GuiGraphicsExtractor guiGraphics) {
        int y = SIM_PANEL_Y - simulationScrollbar.getScrollOffset();
        int contentStart = y;
        boolean customPattern = menu.getRodPattern() == RodPatternLogic.PATTERN_CUSTOM;
        if (!customPattern) {
            y = renderSimulationStatusAndStats(guiGraphics, y);
            y += SIM_LINE_HEIGHT;
        }
        y = renderSimulationBuildRequirements(guiGraphics, y);
        return y - contentStart;
    }

    private int renderSimulationStatusAndStats(GuiGraphicsExtractor guiGraphics, int y) {
        ReactorPanelText.drawStatusLine(guiGraphics, font, SIM_PANEL_X, y,
                Component.translatable("gui.colossal_reactors.reactor_builder.simulation"), null);
        y += SIM_LINE_HEIGHT;

        ReactorSimulation.SimulationResult result = getSimulationResult();
        int rodCount = result.rodCount();
        String fuelStr = formatFuelPerTickSim(result.fuelPerTickHundredths());

        y = ReactorPanelText.drawMetricRow(guiGraphics, font, SIM_PANEL_X, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.reactor_controller.rods.label",
                Component.translatable("gui.colossal_reactors.reactor_controller.rods.value", rodCount, result.rodColumns()));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, SIM_PANEL_X, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.reactor_controller.coolant_blocks.label",
                Component.translatable("gui.colossal_reactors.reactor_controller.coolant_blocks.value",
                        GuiNumberFormat.format(result.coolantBlockCount())));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, SIM_PANEL_X, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.reactor_controller.energy_production.label",
                Component.translatable("gui.colossal_reactors.reactor_controller.energy_production.value",
                        GuiNumberFormat.format(result.rfPerTick())));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, SIM_PANEL_X, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.reactor_controller.water_consume.label",
                Component.translatable("gui.colossal_reactors.reactor_controller.water_consume.value",
                        GuiNumberFormat.format(result.coolantConsumedPerTick())));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, SIM_PANEL_X, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.reactor_controller.steam_production.label",
                Component.translatable("gui.colossal_reactors.reactor_controller.steam_production.value",
                        GuiNumberFormat.format(result.steamPerTick())));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, SIM_PANEL_X, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.reactor_controller.fuel_units.label",
                Component.translatable("gui.colossal_reactors.reactor_controller.fuel_units.value", fuelStr));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, SIM_PANEL_X, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.reactor_builder.simulation.ratio.label",
                Component.translatable("gui.colossal_reactors.reactor_builder.simulation.ratio.value",
                        GuiNumberFormat.formatReactorRfPerFuel(result.rfPerTick(), result.fuelPerTickHundredths())));
        return y;
    }

    private int renderSimulationBuildRequirements(GuiGraphicsExtractor guiGraphics, int y) {
        guiGraphics.text(font,
                Component.translatable("gui.colossal_reactors.reactor_builder.simulation.required_for_build"),
                SIM_PANEL_X, y, GuiTextColors.PANEL_YELLOW_LIGHT, false);
        y += SIM_LINE_HEIGHT;
        return renderMaterialCounts(guiGraphics, y);
    }

    private int renderMaterialCounts(GuiGraphicsExtractor guiGraphics, int y) {
        ReactorBuildMaterialCounter.BuildMaterialCounts counts = getMaterialCounts();
        boolean customPattern = menu.getRodPattern() == RodPatternLogic.PATTERN_CUSTOM;
        String unknown = "?";
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, SIM_PANEL_X, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.reactor_builder.calculate.frame_casings.label",
                Component.translatable("gui.colossal_reactors.reactor_builder.calculate.frame_casings.value",
                        GuiNumberFormat.format(counts.frameCasings())));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, SIM_PANEL_X, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.reactor_builder.calculate.face_casings.label",
                Component.translatable("gui.colossal_reactors.reactor_builder.calculate.face_casings.value",
                        GuiNumberFormat.format(counts.faceCasings())));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, SIM_PANEL_X, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.reactor_builder.calculate.rods.label",
                Component.translatable("gui.colossal_reactors.reactor_builder.calculate.rods.value",
                        customPattern ? unknown : GuiNumberFormat.format(counts.rods())));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, SIM_PANEL_X, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.reactor_builder.calculate.rod_controllers.label",
                Component.translatable("gui.colossal_reactors.reactor_builder.calculate.rod_controllers.value",
                        customPattern ? unknown : GuiNumberFormat.format(counts.rodControllers())));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, SIM_PANEL_X, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.reactor_builder.calculate.heat_sinks.label",
                Component.translatable("gui.colossal_reactors.reactor_builder.calculate.heat_sinks.value",
                        GuiNumberFormat.format(counts.heatSinkCells())));
        if (HeatSinkLoader.requiresLiquidPlacement(menu.getHeatSinkIndex()) && counts.heatSinkCells() > 0) {
            y = ReactorPanelText.drawMetricRow(guiGraphics, font, SIM_PANEL_X, y, SIM_LINE_HEIGHT,
                    "gui.colossal_reactors.reactor_builder.calculate.fluid_mb.label",
                    Component.translatable("gui.colossal_reactors.reactor_builder.calculate.fluid_mb.value",
                            GuiNumberFormat.format(counts.estimatedFluidMb())));
        }
        return y;
    }

    private ReactorBuildMaterialCounter.BuildMaterialCounts getMaterialCounts() {
        int sizeLeft = menu.getSizeRight();
        int sizeRight = menu.getSizeLeft();
        var masks = websiteSimMasks();
        return ReactorBuildMaterialCounter.estimate(
                sizeLeft, sizeRight, menu.getSizeH(), menu.getSizeD(),
                menu.getRodPattern(), menu.getPatternMode(), menu.getHeatSinkIndex(), menu.isOpenTop(),
                masks.rodOverride(), masks.materialHeatSinkCell());
    }

    /** Current coolant for simulation: null = None (index 0), else the selected definition. */
    private CoolantDefinition getSimulationCoolantDef() {
        int simulationCoolantIndex = menu.getSimCoolantIndex();
        if (simulationCoolantIndex <= 0) return null;
        List<Identifier> ids = getOrderedCoolantIds();
        int idx = simulationCoolantIndex - 1;
        if (idx >= ids.size()) return null;
        return CoolantLoader.get(ids.get(idx));
    }

    /**
     * Same simulation as the real reactor: same formula for RF and fuel consumption (fuel units/tick, same as uranium #c:ingots/uranium).
     * Parameters must match {@link ReactorBuildLogic}: sizeLeft/sizeRight/sizeHeight/sizeDepth from entity (menu sizeData 0,1,2,3).
     * Menu display swaps L/R: getSizeRight() = sizeData(0) = entity.sizeLeft, getSizeLeft() = sizeData(1) = entity.sizeRight.
     */
    private ReactorSimulation.SimulationResult getSimulationResult() {
        if (minecraft == null || minecraft.level == null) {
            return new ReactorSimulation.SimulationResult(0, 0, 0, 0, 0, 0, 0, true);
        }
        var ra = minecraft.level.registryAccess();
        CoolantDefinition coolantDef = getSimulationCoolantDef();
        List<Identifier> fuelIds = getOrderedFuelIds();
        Identifier simulationFuelId = fuelIds.isEmpty() ? null : fuelIds.get(Math.min(menu.getSimFuelIndex(), fuelIds.size() - 1));
        // Same order as ReactorBuildLogic: entity.sizeLeft, entity.sizeRight, entity.sizeHeight, entity.sizeDepth (sizeData 0,1,2,3)
        int sizeLeft = menu.getSizeRight();
        int sizeRight = menu.getSizeLeft();
        int sizeHeight = menu.getSizeH();
        int sizeDepth = menu.getSizeD();
        var masks = websiteSimMasks();
        return ReactorBuilderSimulation.run(ra,
                sizeLeft, sizeRight, sizeHeight, sizeDepth,
                menu.getRodPattern(), menu.getPatternMode(), menu.getHeatSinkIndex(),
                simulationFuelId, coolantDef,
                masks.rodOverride(), masks.simHeatSinkCell(), masks.heatSinkOption());
    }

    private record WebsiteSimMasks(
            @Nullable BiPredicate<Integer, Integer> rodOverride,
            /** Non-air / non-rod cells only — materials and build progress skip empty air. */
            @Nullable BiPredicate<Integer, Integer> materialHeatSinkCell,
            /** Includes air (runtime heat sink); excludes rod columns. */
            @Nullable BiPredicate<Integer, Integer> simHeatSinkCell,
            /** Per-cell heat-sink option index for Website simulation modifiers. */
            @Nullable BiFunction<Integer, Integer, Integer> heatSinkOption) {}

    private WebsiteSimMasks websiteSimMasks() {
        var be = menu.getBlockEntity();
        if (!isWebsitePattern() || be == null || !be.hasValidWebsiteDesign()) {
            return new WebsiteSimMasks(null, null, null, null);
        }
        return new WebsiteSimMasks(
                be::websiteIsRodColumn,
                (rx, rz) -> {
                    String cell = be.getWebsiteCell(rx, rz);
                    return cell != null && !"air".equals(cell) && !"rod".equals(cell);
                },
                (rx, rz) -> !"rod".equals(be.getWebsiteCell(rx, rz)),
                (rx, rz) -> {
                    String cell = be.getWebsiteCell(rx, rz);
                    if (cell == null || "air".equals(cell) || "rod".equals(cell)) {
                        return 0;
                    }
                    return BuilderWebsiteDesigns.findHeatSinkOptionIndex(cell);
                });
    }

    private static String formatFuelPerTickSim(int hundredths) {
        if (hundredths <= 0) return "0";
        int intPart = hundredths / 100;
        if (intPart >= 1000) {
            return GuiNumberFormat.format(hundredths / 100.0);
        }
        int frac = hundredths % 100;
        if (frac == 0) return String.valueOf(intPart);
        String fracStr = String.format("%02d", frac).replaceFirst("0+$", "");
        return intPart + "." + fracStr;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        if (viewMode == ViewMode.WEBSITE_LINK) {
            BuilderWebsiteLinkGui.drawBodyLocal(guiGraphics, font);
            return;
        }
        if (viewMode == ViewMode.SIMULATION) {
            // Scissor coords are GUI-local; pose is already translated by extractContents.
            guiGraphics.enableScissor(SIM_PANEL_X, SIM_PANEL_Y, GuiPanelScrollbar.TEXT_RIGHT, GuiPanelScrollbar.TEXT_BOTTOM);
            int contentHeight = renderSimulationPanel(guiGraphics);
            guiGraphics.disableScissor();
            simulationScrollbar.setContentHeight(contentHeight);
            Component simTitle = Component.translatable("gui.colossal_reactors.reactor_builder.simulation");
            int titleX = (imageWidth - font.width(simTitle)) / 2;
            guiGraphics.text(font, simTitle, titleX, 6, GuiTextColors.TITLE, false);
            return;
        }
        int titleX = (imageWidth - font.width(title)) / 2;
        guiGraphics.text(font, title, titleX, 6, GuiTextColors.TITLE, false);

        // Size: (L+R=X) x (Y+1) x (Z+1) — X and Y/Z as block count (+1)
        int totalW = menu.getSizeLeft() + menu.getSizeRight();
        Component sizeLabel = Component.translatable("gui.colossal_reactors.reactor_builder.size",
                menu.getSizeLeft(), menu.getSizeRight(), totalW + 1, menu.getSizeH() + 1, menu.getSizeD() + 1);
        int sizeX = (imageWidth - font.width(sizeLabel)) / 2;
        guiGraphics.text(font, sizeLabel, sizeX, SIZE_LABEL_Y, GuiTextColors.TITLE, false);

        if (menu.isBuildProgressVisible()) {
            int percent = menu.getBuildProgressPercent();
            float t = Math.max(0, Math.min(100, percent)) / 100f;
            int r = (int) (255 * (1f - t));
            int g = (int) (255 * t);
            int progressColor = 0xFF000000 | (r << 16) | (g << 8);
            Component percentText = Component.literal(percent + "%");
            guiGraphics.text(font, percentText, buildPercentTextX(percentText),
                    buildPercentTextY(font.lineHeight), progressColor, false);
        }
        if (menu.isInvalidBlocksDetected()) {
            guiGraphics.text(font,
                    Component.translatable("gui.colossal_reactors.reactor_builder.warning.invalid_blocks"),
                    STATUS_MESSAGE_X, buildInvalidBlocksTextY(font.lineHeight), GuiTextColors.ERROR, false);
        }
        if (viewMode == ViewMode.BUILDER && !websiteStatusMessage.getString().isEmpty()) {
            int statusY = menu.isInvalidBlocksDetected()
                    ? STATUS_MESSAGE_Y + font.lineHeight + 1
                    : buildInvalidBlocksTextY(font.lineHeight);
            guiGraphics.text(font, websiteStatusMessage,
                    STATUS_MESSAGE_X, statusY, GuiTextColors.ERROR, false);
        }
    }

    @Override
    public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractContents(guiGraphics, mouseX, mouseY, partialTick);
        if (viewMode == ViewMode.BUILDER) {
            renderMarkInputGhosts(guiGraphics);
        }
    }

    /** Ghost item + dark overlay in empty buffer slots with a mark-input filter (same as Pattern Crafter). */
    private void renderMarkInputGhosts(GuiGraphicsExtractor guiGraphics) {
        if (menu.getBlockEntity() == null) return;
        guiGraphics.nextStratum();
        for (int i = 0; i < ReactorBuilderMenu.BUFFER_SLOTS; i++) {
            if (!menu.hasMarkInputFilter(i)) continue;
            Slot guiSlot = menu.getSlot(i);
            if (!guiSlot.getItem().isEmpty()) continue;
            ItemStack ghost = menu.getMarkInputFilter(i);
            if (ghost.isEmpty()) continue;
            renderMarkInputGhostItem(guiGraphics, ghost, guiSlot.x, guiSlot.y);
        }
    }

    private void renderMarkInputGhostItem(GuiGraphicsExtractor guiGraphics, ItemStack stack, int sx, int sy) {
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(leftPos + sx, topPos + sy);
        guiGraphics.item(stack, 0, 0);
        guiGraphics.fill(0, 0, 16, 16, 0x80000000);
        guiGraphics.pose().popMatrix();
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        super.extractTooltip(guiGraphics, mouseX, mouseY);
        if (viewMode != ViewMode.BUILDER) return;
        int left = leftPos + FLUID_BAR_X + FLUID_FILL_INSET;
        int top = topPos + FLUID_BAR_Y + FLUID_FILL_INSET;
        if (mouseX >= left && mouseX < left + FLUID_FILL_WIDTH && mouseY >= top && mouseY < top + FLUID_FILL_HEIGHT) {
            int amount = menu.getFluidAmount();
            int capacity = menu.getFluidCapacity();
            int fluidId = menu.getFluidId();
            Component amountLine = capacity > 0
                    ? Component.translatable("gui.colossal_reactors.resource_port.tank_tooltip", amount, capacity)
                    : Component.translatable("gui.colossal_reactors.resource_port.tank_empty");
            List<FormattedCharSequence> tooltipLines = new ArrayList<>();
            tooltipLines.add(amountLine.getVisualOrderText());
            if (fluidId >= 0) {
                Fluid fluid = BuiltInRegistries.FLUID.byId(fluidId);
                if (fluid != null && fluid != Fluids.EMPTY) {
                    FluidType type = fluid.getFluidType();
                    tooltipLines.add(Component.translatable(type.getDescriptionId()).getVisualOrderText());
                }
            }
            guiGraphics.setTooltipForNextFrame(font, tooltipLines, mouseX, mouseY);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (viewMode == ViewMode.WEBSITE_LINK) {
            extractContents(guiGraphics, mouseX, mouseY, partialTick);
            extractTooltip(guiGraphics, mouseX, mouseY);
        } else if (viewMode != ViewMode.BUILDER) {
            extractContents(guiGraphics, mouseX, mouseY, partialTick);
            simulationScrollbar.render(guiGraphics, leftPos, topPos);
            extractCarriedItem(guiGraphics, mouseX, mouseY);
            extractSnapbackItem(guiGraphics);
            extractTooltip(guiGraphics, mouseX, mouseY);
        } else {
            super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (viewMode != ViewMode.BUILDER && event.isEscape()) {
            switchToBuilderView();
            return true;
        }
        return super.keyPressed(event);
    }

    private void togglePreview() {
        BlockPos builderPos = menu.getBlockPos();
        boolean enabling = !BuilderPreviewTracker.isPreviewActive(builderPos);
        if (enabling && isWebsitePattern() && !hasValidWebsiteDesign()) {
            websiteStatusMessage = Component.translatable("gui.colossal_reactors.builder.set_website_design");
            return;
        }
        if (enabling) {
            BuilderPreviewTracker.setPreviewActive(builderPos, true);
            BuilderPreviewTracker.clearMarkersForBuilder(builderPos);
        } else {
            BuilderPreviewTracker.setPreviewActive(builderPos, false);
        }
        previewButtonShowsHide = enabling;
        updatePreviewButtonLabel();
        lastPreviewSettingsHash = computePreviewSettingsHash();
        if (enabling) {
            BuilderPreviewTracker.seedWorldHash(minecraft.level, builderPos);
            BuilderPreviewTracker.noteRefreshRequested(builderPos);
        }
        ClientPacketDistributor.sendToServer(new BuilderPreviewTogglePayload(builderPos, enabling, true));
    }

    private void updatePreviewButtonLabel() {
        if (buttonPreview != null) {
            buttonPreview.setMessage(Component.translatable(
                    previewButtonShowsHide
                            ? "gui.colossal_reactors.reactor_builder.preview.hide"
                            : "gui.colossal_reactors.reactor_builder.preview"));
        }
    }

    @Override
    public void onClose() {
        simulationScrollbar.disposeButtons(this::removeWidget);
        menu.setHideAllSlotsForSimulationView(false);
        super.onClose();
    }
}
