package net.unfamily.colossal_reactors.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.unfamily.colossal_reactors.ColossalReactors;
import net.unfamily.colossal_reactors.menu.TurbineBuilderMenu;
import net.unfamily.colossal_reactors.turbine.ElecCoilLoader;
import net.unfamily.colossal_reactors.network.TurbineBuilderBuildPayload;
import net.unfamily.colossal_reactors.network.TurbineBuilderMarkInputPayload;
import net.unfamily.colossal_reactors.network.TurbineBuilderCoilPayload;
import net.unfamily.colossal_reactors.network.TurbineBuilderOptionPayload;
import net.unfamily.colossal_reactors.network.TurbineBuilderSimSettingsPayload;
import net.unfamily.colossal_reactors.network.TurbineBuilderSizePayload;
import net.unfamily.colossal_reactors.network.FluidTankDumpPayload;
import net.unfamily.colossal_reactors.client.BuilderPreviewTracker;
import net.unfamily.colossal_reactors.network.BuilderPreviewTogglePayload;
import net.unfamily.colossal_reactors.network.TurbinePreviewPayload;
import net.unfamily.colossal_reactors.Config;
import net.unfamily.colossal_reactors.turbine.TurbineBuildMaterialCounter;
import net.unfamily.colossal_reactors.turbine.TurbinePlacementAxis;
import net.unfamily.colossal_reactors.turbine.TurbineRodSpaceLayout;
import net.unfamily.colossal_reactors.turbine.TurbineGenerationDefinition;
import net.unfamily.colossal_reactors.turbine.TurbineGenerationLoader;
import net.unfamily.colossal_reactors.turbine.TurbineRodPatternLogic;
import net.unfamily.colossal_reactors.turbine.TurbineSimulation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Reactor Builder GUI. Background turbine_builder.png 230x240; simulation uses turbine_controller.png 230x240.
 * Like Deep Drawer Extractor: Simulation is a view mode (isSimulationView) on the same screen, not a separate Screen.
 */
public class TurbineBuilderScreen extends AbstractContainerScreen<TurbineBuilderMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            ColossalReactors.MODID, "textures/gui/turbine_builder.png");
    private static final int GUI_WIDTH = ReactorControllerGui.WIDTH;
    private static final int BUILDER_GUI_HEIGHT = 240;
    private static final int REACTOR_GUI_HEIGHT = ReactorControllerGui.HEIGHT;

    /** Close button (X): top right */
    private static final int CLOSE_BUTTON_Y = 5;
    private static final int CLOSE_BUTTON_SIZE = 12;
    private static final int CLOSE_BUTTON_X = GUI_WIDTH - CLOSE_BUTTON_SIZE - 5;

    /**
     * Tank at (12, 26), same dimensions as Resource Port: 12x54 fill with 1px inset
     */
    private static final int FLUID_BAR_X = 12;
    private static final int FLUID_BAR_Y = 26;
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
    private static final int ROW1_Y = 32;
    private static final int ROW2_Y = 46;
    private static final int GAP = 3;
    private static final int INVENTORY_LEFT_X = 35;
    private static final int ARROW_GROUP_WIDTH = 3 * BUTTON_W + 2 * GAP;  // 48
    /**
     * Arrow block left edge = inventory left edge (35).
     */
    private static final int GROUP_LEFT_X = INVENTORY_LEFT_X;
    private static final int BUTTON_UP_X = GROUP_LEFT_X + ARROW_GROUP_WIDTH / 2 - BUTTON_W / 2;
    private static final int BUTTON_LEFT_X = GROUP_LEFT_X;
    private static final int BUTTON_DOWN_X = GROUP_LEFT_X + BUTTON_W + GAP;
    private static final int BUTTON_RIGHT_X = GROUP_LEFT_X + 2 * (BUTTON_W + GAP);
    /** Preview / Mark Input: full arrow-group width (unchanged). */
    private static final int PREVIEW_BUTTON_Y = ROW2_Y + BUTTON_H + GAP;
    private static final int PREVIEW_BUTTON_W = ARROW_GROUP_WIDTH;
    private static final int PREVIEW_BUTTON_X = GROUP_LEFT_X;
    private static final int MARK_INPUT_BUTTON_Y = PREVIEW_BUTTON_Y + BUTTON_H + GAP;

    /**
     * 6 buttons: 3 cols x 2 rows (Coil type, Pattern, Placement axis, OpenTop, Simulation, Build/Stop).
     */
    private static final int RIGHT_EDGE_INSET = 12;
    private static final int RIGHT_BUTTON_W = 42;
    private static final int RIGHT_BLOCK_X = GUI_WIDTH - RIGHT_EDGE_INSET - (3 * RIGHT_BUTTON_W + 2 * GAP);
    private static final int RIGHT_BUTTON_H = BUTTON_H;
    private static final int RIGHT_COL0_X = RIGHT_BLOCK_X;
    private static final int RIGHT_COL1_X = RIGHT_BLOCK_X + RIGHT_BUTTON_W + GAP;
    private static final int RIGHT_COL2_X = RIGHT_BLOCK_X + 2 * (RIGHT_BUTTON_W + GAP);
    private static final int RIGHT_ROW0_Y = ROW1_Y;
    private static final int RIGHT_ROW1_Y = MARK_INPUT_BUTTON_Y;
    /** Beside Preview (same row); width matches Closed + Simulation on the right block. */
    private static final int COIL_LAYERS_BUTTON_X = PREVIEW_BUTTON_X + PREVIEW_BUTTON_W + GAP;
    private static final int COIL_LAYERS_BUTTON_Y = PREVIEW_BUTTON_Y;
    private static final int COIL_LAYERS_BUTTON_W = RIGHT_COL1_X + RIGHT_BUTTON_W - RIGHT_COL0_X;
    private static final int WARNING_RIGHT_X = RIGHT_BLOCK_X;
    private static final int BUILD_BUTTON_COL_X = RIGHT_COL2_X;

    private int buildPercentTextX(Component percentText) {
        return BUILD_BUTTON_COL_X + (RIGHT_BUTTON_W - font.width(percentText)) / 2;
    }

    private static int buildPercentTextY(int lineHeight) {
        return RIGHT_ROW1_Y - lineHeight - GAP;
    }

    /** Former build % Y (bottom of arrow row), after vertical swap with invalid-blocks warning. */
    private static int buildInvalidBlocksTextY(int lineHeight) {
        return ROW2_Y + BUTTON_H - lineHeight;
    }

    /** Simulation view panel (same layout as reactor controller). */
    private static final int SIM_PANEL_X = 16;
    private static final int SIM_PANEL_Y = GuiPanelScrollbar.TEXT_TOP;
    private static final int SIM_LINE_HEIGHT = 12;
    private static final int SIM_TEXT_COLOR = 0xFFFFFF;

    /** Steam generation cycle button (full width, left-aligned where fuel button was on reactor builder). */
    private static final int SIM_BUTTONS_HORIZONTAL_INSET = 12;
    private static final int STEAM_BUTTON_W = GUI_WIDTH - SIM_BUTTONS_HORIZONTAL_INSET * 2;
    private static final int STEAM_BUTTON_H = 20;
    private static final int COOLANT_BUTTON_RIGHT_INSET = SIM_BUTTONS_HORIZONTAL_INSET;
    private static final int COOLANT_BUTTON_BOTTOM_INSET = 13;

    private enum ViewMode { BUILDER, SIMULATION }

    private ViewMode viewMode = ViewMode.BUILDER;

    private static final String TOOLTIP_LEFT_CLICK = "gui.colossal_reactors.turbine_builder.tooltip.left_click";
    private static final String TOOLTIP_RIGHT_CLICK = "gui.colossal_reactors.turbine_builder.tooltip.right_click";
    private static final String TOOLTIP_SHIFT_10 = "gui.colossal_reactors.turbine_builder.tooltip.shift_10";
    private static final String TOOLTIP_ALT_CTRL_5 = "gui.colossal_reactors.turbine_builder.tooltip.alt_ctrl_5";

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
    private Button buttonLeft;
    private Button buttonRight;
    private Button buttonDown;
    private Button buttonPreview;
    private boolean previewButtonShowsHide;
    private int lastPreviewSettingsHash;
    private Button buttonMarkInput;
    private Button buttonDumpFluid;
    /** Right block: 0=Coil, 1=Pattern, 2=Placement axis, 3=OpenTop, 4=Simulation, 5=Build/Stop. */
    private static final int RIGHT_BUTTON_COUNT = 6;
    private final Button[] rightBlockButtons = new Button[RIGHT_BUTTON_COUNT];
    /** Coil zone layer count (below placement-axis column). */
    private Button coilLayersButton;
    /** Shown only in simulation view: cycles turbine steam generation recipe. */
    private Button steamGenerationButton;
    private final GuiPanelScrollbar simulationScrollbar = new GuiPanelScrollbar();

    public TurbineBuilderScreen(TurbineBuilderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = GUI_WIDTH;
        // Tall enough for simulation ({@code turbine_controller.png}); builder background uses {@link #BUILDER_GUI_HEIGHT}.
        imageHeight = REACTOR_GUI_HEIGHT;
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
        addRenderableWidget(buttonLeft);
        addRenderableWidget(buttonDown);
        addRenderableWidget(buttonRight);

        // Preview below the 4 arrows, centered with arrow group
        buttonPreview = Button.builder(Component.translatable("gui.colossal_reactors.turbine_builder.preview"), b -> togglePreview())
                .bounds(leftPos + PREVIEW_BUTTON_X, topPos + PREVIEW_BUTTON_Y, PREVIEW_BUTTON_W, BUTTON_H)
                .build();
        buttonPreview.setTooltip(Tooltip.create(
                Component.translatable("gui.colossal_reactors.turbine_builder.preview.tooltip.line1")
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.turbine_builder.preview.tooltip.line2"))
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.turbine_builder.preview.tooltip.line3"))
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.turbine_builder.preview.tooltip.line4"))
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.turbine_builder.preview.tooltip.line5"))
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.turbine_builder.preview.tooltip.line6"))));
        addRenderableWidget(buttonPreview);

        buttonMarkInput = Button.builder(Component.translatable("gui.colossal_reactors.turbine_builder.mark_input"), b -> onMarkInputPressed())
                .bounds(leftPos + PREVIEW_BUTTON_X, topPos + MARK_INPUT_BUTTON_Y, PREVIEW_BUTTON_W, BUTTON_H)
                .build();
        buttonMarkInput.setTooltip(Tooltip.create(
                Component.translatable("gui.colossal_reactors.turbine_builder.mark_input.tooltip.line1")
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.turbine_builder.mark_input.tooltip.line2"))
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.turbine_builder.mark_input.tooltip.line3"))));
        addRenderableWidget(buttonMarkInput);

        int[] rowY = {RIGHT_ROW0_Y, RIGHT_ROW1_Y};
        int[] colX = {RIGHT_COL0_X, RIGHT_COL1_X, RIGHT_COL2_X};
        for (int i = 0; i < RIGHT_BUTTON_COUNT; i++) {
            int row = i / 3;
            int col = i % 3;
            Component label = getRightButtonLabel(i);
            final int buttonIndex = i;
            rightBlockButtons[i] = Button.builder(label, b -> onRightBlockClick(buttonIndex))
                    .bounds(leftPos + colX[col], topPos + rowY[row], RIGHT_BUTTON_W, RIGHT_BUTTON_H)
                    .build();
            addRenderableWidget(rightBlockButtons[i]);
        }
        coilLayersButton = Button.builder(getCoilLayersButtonLabel(), b -> onCoilLayersClick(true))
                .bounds(leftPos + COIL_LAYERS_BUTTON_X, topPos + COIL_LAYERS_BUTTON_Y, COIL_LAYERS_BUTTON_W, RIGHT_BUTTON_H)
                .build();
        addRenderableWidget(coilLayersButton);
        int steamY = topPos + imageHeight - STEAM_BUTTON_H - COOLANT_BUTTON_BOTTOM_INSET;
        int steamX = leftPos + SIM_BUTTONS_HORIZONTAL_INSET;
        steamGenerationButton = Button.builder(Component.translatable("gui.colossal_reactors.turbine_builder.simulation.steam_default"), b -> cycleSimulationGeneration(true))
                .bounds(steamX, steamY, STEAM_BUTTON_W, STEAM_BUTTON_H)
                .build();
        addRenderableWidget(steamGenerationButton);
        simulationScrollbar.createButtons(leftPos, topPos, this::addRenderableWidget, () -> {});
        updateWidgetVisibility();
        previewButtonShowsHide = BuilderPreviewTracker.isPreviewActive(menu.getBlockPos());
        updatePreviewButtonLabel();
        lastPreviewSettingsHash = computePreviewSettingsHash();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (viewMode == ViewMode.BUILDER) {
            updateButtonTooltips();
            BlockPos builderPos = menu.getBlockPos();
            if (BuilderPreviewTracker.isPreviewActive(builderPos)) {
                int hash = computePreviewSettingsHash();
                if (hash != lastPreviewSettingsHash) {
                    lastPreviewSettingsHash = hash;
                    BuilderPreviewTracker.noteRefreshRequested(builderPos);
                    PacketDistributor.sendToServer(new TurbinePreviewPayload(builderPos));
                }
            } else {
                lastPreviewSettingsHash = computePreviewSettingsHash();
            }
        } else {
            updateSteamGenerationButtonLabel();
        }
    }

    private int computePreviewSettingsHash() {
        return java.util.Objects.hash(
                menu.getSizeLeft(),
                menu.getSizeRight(),
                menu.getSizeH(),
                menu.getSizeD(),
                menu.getSelectedCoilIndex(),
                menu.getCoilLayerCount(),
                menu.getRodPattern(),
                menu.isOpenTop(),
                menu.getPlacementAxisOrdinal());
    }

    private List<TurbineGenerationDefinition> getVisibleGenerations() {
        return TurbineGenerationLoader.getVisibleDefinitions();
    }

    private void cycleSimulationGeneration(boolean next) {
        if (menu.getBlockEntity() == null || getVisibleGenerations().isEmpty()) return;
        PacketDistributor.sendToServer(new TurbineBuilderSimSettingsPayload(menu.getBlockPos(), next));
    }

    private void updateSteamGenerationButtonLabel() {
        if (steamGenerationButton == null) return;
        List<TurbineGenerationDefinition> gens = getVisibleGenerations();
        Component clickHint = Component.translatable("gui.colossal_reactors.turbine_builder.simulation.click_hint");
        MutableComponent title = Component.translatable("gui.colossal_reactors.turbine_builder.simulation.steam_label");
        if (gens.isEmpty()) {
            steamGenerationButton.setMessage(Component.translatable("gui.colossal_reactors.turbine_builder.simulation.steam_default"));
            steamGenerationButton.setTooltip(Tooltip.create(title.append(Component.literal("\n")).append(clickHint)));
            return;
        }
        int simulationGenerationIndex = Math.min(menu.getSimGenerationIndex(), gens.size() - 1);
        TurbineGenerationDefinition def = gens.get(simulationGenerationIndex);
        var ra = minecraft != null && minecraft.level != null ? minecraft.level.registryAccess() : null;
        Component label = getGenerationDisplayName(def, ra);
        MutableComponent tooltip = title.copy()
                .append(Component.literal("\n"))
                .append(Component.translatable("jei.colossal_reactors.turbine_generation.rf_per_mb",
                        net.unfamily.colossal_reactors.turbine.TurbineGenerationLoader.formatRfPerSteamMb(def.rfProduction())))
                .append(Component.literal("\n"))
                .append(clickHint);
        steamGenerationButton.setMessage(label);
        steamGenerationButton.setTooltip(Tooltip.create(tooltip));
    }

    private static Component getGenerationDisplayName(TurbineGenerationDefinition def, net.minecraft.core.RegistryAccess ra) {
        if (ra == null || def.inputs().isEmpty()) {
            return Component.literal(def.generationId().toString());
        }
        return net.unfamily.colossal_reactors.client.SelectorDisplayNames.fromFirstSelector(def.inputs(), ra);
    }

    private void onCloseButtonClicked() {
        if (viewMode != ViewMode.BUILDER) {
            if (minecraft != null && minecraft.getSoundManager() != null)
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            switchToBuilderView();
        } else {
            if (minecraft != null && minecraft.getSoundManager() != null)
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            if (minecraft != null && minecraft.player != null) minecraft.player.closeContainer();
        }
    }

    private void onFluidDumpPressed() {
        if (menu.getBlockEntity() == null) return;
        PacketDistributor.sendToServer(new FluidTankDumpPayload(menu.getBlockPos()));
    }

    private void onMarkInputPressed() {
        if (menu.getBlockEntity() == null) return;
        int mode = TurbineBuilderMarkInputPayload.MODE_NORMAL;
        if (Screen.hasShiftDown()) {
            mode = TurbineBuilderMarkInputPayload.MODE_SHIFT;
        } else if (Screen.hasControlDown() || Screen.hasAltDown()) {
            mode = TurbineBuilderMarkInputPayload.MODE_CTRL;
        }
        PacketDistributor.sendToServer(new TurbineBuilderMarkInputPayload(menu.getBlockEntity().getBlockPos(), mode));
    }

    private void switchToSimulationView() {
        viewMode = ViewMode.SIMULATION;
        simulationScrollbar.resetScroll();
        menu.setHideAllSlotsForSimulationView(true);
        simulationScrollbar.ensureButtons(leftPos, topPos, this::addRenderableWidget, () -> {});
        updateWidgetVisibility();
    }

    private void switchToBuilderView() {
        viewMode = ViewMode.BUILDER;
        simulationScrollbar.disposeButtons(this::removeWidget);
        menu.setHideAllSlotsForSimulationView(false);
        updateWidgetVisibility();
    }

    private void updateWidgetVisibility() {
        boolean showBuilder = viewMode == ViewMode.BUILDER;
        if (closeButton != null) closeButton.visible = true;
        if (buttonUp != null) buttonUp.visible = showBuilder;
        if (buttonLeft != null) buttonLeft.visible = showBuilder;
        if (buttonRight != null) buttonRight.visible = showBuilder;
        if (buttonDown != null) buttonDown.visible = showBuilder;
        if (buttonPreview != null) buttonPreview.visible = showBuilder;
        if (buttonMarkInput != null) buttonMarkInput.visible = showBuilder;
        if (buttonDumpFluid != null) buttonDumpFluid.visible = showBuilder;
        for (Button b : rightBlockButtons) {
            if (b != null) b.visible = showBuilder;
        }
        if (coilLayersButton != null) coilLayersButton.visible = showBuilder;
        if (steamGenerationButton != null) {
            steamGenerationButton.visible = viewMode == ViewMode.SIMULATION;
            if (viewMode == ViewMode.SIMULATION) updateSteamGenerationButtonLabel();
        }
    }

    private Component getRightButtonLabel(int index) {
        return switch (index) {
            case 0 -> getCoilButtonLabel();
            case 1 -> Component.translatable("gui.colossal_reactors.turbine_builder.pattern." + menu.getRodPattern());
            case 2 -> getPlacementAxisShortLabel();
            case 3 -> menu.isOpenTop()
                    ? Component.translatable("gui.colossal_reactors.turbine_builder.open_top.open")
                    : Component.translatable("gui.colossal_reactors.turbine_builder.open_top.closed");
            case 4 -> Component.translatable("gui.colossal_reactors.turbine_builder.simulation");
            case 5 -> menu.isBuilding()
                    ? Component.translatable("gui.colossal_reactors.turbine_builder.stop")
                    : Component.translatable("gui.colossal_reactors.turbine_builder.build");
            default -> Component.literal("?");
        };
    }

    private TurbinePlacementAxis currentPlacementAxis() {
        return TurbinePlacementAxis.fromIndex(menu.getPlacementAxisOrdinal());
    }

    private Component getPlacementAxisShortLabel() {
        return Component.translatable("gui.colossal_reactors.turbine_builder.placement_axis.short." + currentPlacementAxis().id());
    }

    private MutableComponent getPlacementAxisTooltip() {
        return Component.translatable("gui.colossal_reactors.turbine_builder.tooltip.placement_axis.header")
                .append(Component.literal("\n"))
                .append(Component.translatable(
                        "gui.colossal_reactors.turbine_builder.tooltip.placement_axis.flow." + currentPlacementAxis().id()))
                .append(Component.literal("\n"))
                .append(Component.translatable("gui.colossal_reactors.turbine_builder.tooltip.coil_left"))
                .append(Component.literal("\n"))
                .append(Component.translatable("gui.colossal_reactors.turbine_builder.tooltip.coil_right"));
    }

    private Component getCoilButtonLabel() {
        if (minecraft != null && minecraft.level != null) {
            return ElecCoilLoader.getOptionDisplayName(minecraft.level.registryAccess(), menu.getSelectedCoilIndex());
        }
        return Component.translatable("block.minecraft.air");
    }

    private int interiorExtentAlongPlacementAxis() {
        int w = menu.getSizeLeft() + menu.getSizeRight() + 1;
        int h = menu.getSizeH() + 1;
        int d = menu.getSizeD() + 1;
        return switch (TurbinePlacementAxis
                .fromIndex(menu.getPlacementAxisOrdinal()).facing().getAxis()) {
            case Y -> TurbineRodSpaceLayout.interiorHeight(h);
            case Z -> TurbineRodSpaceLayout.interiorDepth(d);
            case X -> TurbineRodSpaceLayout.interiorWidth(w);
            default -> TurbineRodSpaceLayout.interiorHeight(h);
        };
    }

    /** Layer count shown in GUI (matches build/simulation; stored setting is +1 in code). */
    private int effectiveCoilLayerCount() {
        return TurbineRodSpaceLayout.appliedCoilLayerCount(
                interiorExtentAlongPlacementAxis(), menu.getCoilLayerCount());
    }

    private Component getCoilLayersButtonLabel() {
        return Component.translatable("gui.colossal_reactors.turbine_builder.coil_layers", effectiveCoilLayerCount());
    }

    private void onCoilLayersClick(boolean next) {
        if (menu.getBlockEntity() == null) return;
        PacketDistributor.sendToServer(new TurbineBuilderOptionPayload(menu.getBlockPos(), 2, next));
    }

    private void onRightBlockClick(int index) {
        if (index == 4) {
            switchToSimulationView();
            return;
        }
        if (menu.getBlockEntity() == null) return;
        BlockPos pos = menu.getBlockPos();
        if (index == 0) {
            PacketDistributor.sendToServer(new TurbineBuilderCoilPayload(pos, true));
        }
        if (index == 1) PacketDistributor.sendToServer(new TurbineBuilderOptionPayload(pos, 1, true));
        if (index == 2) PacketDistributor.sendToServer(new TurbineBuilderOptionPayload(pos, 3, true));
        if (index == 3) PacketDistributor.sendToServer(new TurbineBuilderOptionPayload(pos, 0, true));
        if (index == 5) PacketDistributor.sendToServer(new TurbineBuilderBuildPayload(pos));
    }

    private void updateButtonTooltips() {
        buttonUp.setTooltip(tooltipWithValue("gui.colossal_reactors.turbine_builder.tooltip.up_value", menu.getSizeH() + 1));
        buttonLeft.setTooltip(tooltipWithValue("gui.colossal_reactors.turbine_builder.tooltip.left_value", menu.getSizeLeft()));
        buttonRight.setTooltip(tooltipWithValue("gui.colossal_reactors.turbine_builder.tooltip.right_value", menu.getSizeRight()));
        buttonDown.setTooltip(tooltipWithValue("gui.colossal_reactors.turbine_builder.tooltip.behind_value", menu.getSizeD() + 1));
        Component coilName = getCoilButtonLabel();
        rightBlockButtons[0].setMessage(coilName);
        rightBlockButtons[0].setTooltip(Tooltip.create(
                Component.translatable("gui.colossal_reactors.turbine_builder.tooltip.coil_value", coilName)
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.turbine_builder.tooltip.coil_left"))
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.turbine_builder.tooltip.coil_right"))));
        if (coilLayersButton != null) {
            coilLayersButton.setMessage(getCoilLayersButtonLabel());
            MutableComponent layersTip = Component.translatable(
                    "gui.colossal_reactors.turbine_builder.tooltip.coil_layers", effectiveCoilLayerCount());
            if (effectiveCoilLayerCount() < menu.getCoilLayerCount() + 1) {
                layersTip.append(Component.literal("\n"))
                        .append(Component.translatable(
                                "gui.colossal_reactors.turbine_builder.tooltip.coil_layers_capped",
                                effectiveCoilLayerCount(), menu.getCoilLayerCount() + 1));
            }
            layersTip.append(Component.literal("\n"))
                    .append(Component.translatable("gui.colossal_reactors.turbine_builder.tooltip.coil_left"))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("gui.colossal_reactors.turbine_builder.tooltip.coil_right"));
            coilLayersButton.setTooltip(Tooltip.create(layersTip));
        }
        rightBlockButtons[1].setTooltip(Tooltip.create(
                Component.translatable("gui.colossal_reactors.turbine_builder.tooltip.pattern." + menu.getRodPattern())
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.turbine_builder.tooltip.coil_left"))
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.turbine_builder.tooltip.coil_right"))));
        rightBlockButtons[1].setMessage(getRightButtonLabel(1));
        rightBlockButtons[2].setTooltip(Tooltip.create(getPlacementAxisTooltip()));
        rightBlockButtons[2].setMessage(getRightButtonLabel(2));
        rightBlockButtons[3].setTooltip(Tooltip.create(
                Component.translatable(menu.isOpenTop()
                        ? "gui.colossal_reactors.turbine_builder.tooltip.open_top.open"
                        : "gui.colossal_reactors.turbine_builder.tooltip.open_top.closed")
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.turbine_builder.tooltip.coil_left"))
                        .append(Component.literal("\n"))
                        .append(Component.translatable("gui.colossal_reactors.turbine_builder.tooltip.coil_right"))));
        rightBlockButtons[3].setMessage(getRightButtonLabel(3));
        rightBlockButtons[4].setMessage(getRightButtonLabel(4));
        rightBlockButtons[4].setTooltip(Tooltip.create(Component.translatable("gui.colossal_reactors.turbine_builder.simulation.tooltip")));
        rightBlockButtons[5].setMessage(getRightButtonLabel(5));
        rightBlockButtons[5].setTooltip(Tooltip.create(Component.translatable(
                menu.isBuilding() ? "gui.colossal_reactors.turbine_builder.stop.tooltip" : "gui.colossal_reactors.turbine_builder.build.tooltip")));
    }

    private void sendSize(int direction, boolean increment) {
        if (menu.getBlockEntity() == null) return;
        BlockPos pos = menu.getBlockPos();
        int amount = Screen.hasShiftDown() ? 10 : (Screen.hasControlDown() || Screen.hasAltDown()) ? 5 : 1;
        PacketDistributor.sendToServer(new TurbineBuilderSizePayload(pos, direction, increment, amount));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (viewMode == ViewMode.SIMULATION && button == 0 && simulationScrollbar.mouseClicked(mouseX, mouseY, leftPos, topPos)) {
            return true;
        }
        int x = (int) mouseX;
        int y = (int) mouseY;
        if (button == 1) {
            if (viewMode == ViewMode.SIMULATION && steamGenerationButton != null && steamGenerationButton.visible && isInWidget(x, y, steamGenerationButton)) {
                cycleSimulationGeneration(false);
                return true;
            }
            if (isInBounds(x, y, leftPos + BUTTON_UP_X, topPos + ROW1_Y)) {
                sendSize(0, false);
                return true;
            }
            if (isInBounds(x, y, leftPos + BUTTON_LEFT_X, topPos + ROW2_Y)) {
                sendSize(1, false);
                return true;
            }
            if (isInBounds(x, y, leftPos + BUTTON_DOWN_X, topPos + ROW2_Y)) {
                sendSize(3, false);
                return true;
            }
            if (isInBounds(x, y, leftPos + BUTTON_RIGHT_X, topPos + ROW2_Y)) {
                sendSize(2, false);
                return true;
            }
            if (coilLayersButton != null && coilLayersButton.visible && isInWidget(x, y, coilLayersButton)) {
                onCoilLayersClick(false);
                return true;
            }
            if (isInRightBlockButton(x, y, 0)) {
                if (menu.getBlockEntity() != null) {
                    PacketDistributor.sendToServer(new TurbineBuilderCoilPayload(menu.getBlockPos(), false));
                }
                return true;
            }
            if (isInRightBlockButton(x, y, 1)) {
                if (menu.getBlockEntity() != null)
                    PacketDistributor.sendToServer(new TurbineBuilderOptionPayload(menu.getBlockPos(), 1, false));
                return true;
            }
            if (isInRightBlockButton(x, y, 2)) {
                if (menu.getBlockEntity() != null)
                    PacketDistributor.sendToServer(new TurbineBuilderOptionPayload(menu.getBlockPos(), 3, false));
                return true;
            }
            if (isInRightBlockButton(x, y, 3)) {
                if (menu.getBlockEntity() != null)
                    PacketDistributor.sendToServer(new TurbineBuilderOptionPayload(menu.getBlockPos(), 0, false));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            simulationScrollbar.mouseReleased();
        }
        return super.mouseReleased(mouseX, mouseY, button);
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

    private static boolean isInWidget(int x, int y, Button widget) {
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
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        if (viewMode == ViewMode.SIMULATION) {
            guiGraphics.blit(ReactorControllerGui.BACKGROUND, leftPos, topPos, 0, 0, GUI_WIDTH, REACTOR_GUI_HEIGHT, GUI_WIDTH, REACTOR_GUI_HEIGHT);
            guiGraphics.enableScissor(leftPos + SIM_PANEL_X, topPos + SIM_PANEL_Y, leftPos + GuiPanelScrollbar.TEXT_RIGHT, topPos + GuiPanelScrollbar.TEXT_BOTTOM);
            int contentHeight = renderSimulationPanel(guiGraphics);
            guiGraphics.disableScissor();
            simulationScrollbar.setContentHeight(contentHeight);
        } else {
            guiGraphics.blit(BACKGROUND, leftPos, topPos, 0, 0, GUI_WIDTH, BUILDER_GUI_HEIGHT, GUI_WIDTH, BUILDER_GUI_HEIGHT);
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

    /**
     * Panel order: (1) Status + simulation stats, blank line, (2) Required for build + material counts.
     * Custom pattern: required-for-build only (stats are not determinable).
     */
    private int renderSimulationPanel(GuiGraphics guiGraphics) {
        int y = topPos + SIM_PANEL_Y - simulationScrollbar.getScrollOffset();
        int contentStart = y;
        boolean customPattern = menu.getRodPattern() == TurbineRodPatternLogic.PATTERN_CUSTOM;
        if (!customPattern) {
            y = renderSimulationStatusAndStats(guiGraphics, y);
            y += SIM_LINE_HEIGHT;
        }
        y = renderSimulationBuildRequirements(guiGraphics, y);
        return y - contentStart;
    }

    private int renderSimulationStatusAndStats(GuiGraphics guiGraphics, int y) {
        int textX = leftPos + SIM_PANEL_X;
        ReactorPanelText.drawStatusLine(guiGraphics, font, textX, y,
                Component.translatable("gui.colossal_reactors.turbine_builder.simulation"), null);
        y += SIM_LINE_HEIGHT;

        TurbineSimulation.SimulationResult result = getSimulationResult();

        y = ReactorPanelText.drawMetricRow(guiGraphics, font, textX, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.turbine_controller.blades_valid.label",
                Component.translatable("gui.colossal_reactors.turbine_controller.blades_valid.value",
                        GuiNumberFormat.format(result.validBladeCount())));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, textX, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.turbine_controller.energy_runtime.label",
                Component.translatable("gui.colossal_reactors.turbine_controller.energy_runtime.value",
                        GuiNumberFormat.format(result.rfPerTick())));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, textX, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.turbine_controller.steam_runtime.label",
                Component.translatable("gui.colossal_reactors.turbine_controller.steam_runtime.value",
                        GuiNumberFormat.format(result.steamMbPerTick())));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, textX, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.turbine_controller.efficiency.label",
                Component.translatable("gui.colossal_reactors.turbine_controller.efficiency.value",
                        String.format("%.2f", result.bladeEfficiency())));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, textX, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.turbine_builder.simulation.ratio.label",
                Component.translatable("gui.colossal_reactors.turbine_builder.simulation.ratio.value",
                        GuiNumberFormat.formatTurbineRfPerMb((long) result.rfPerTick(), result.steamMbPerTick())));
        return y;
    }

    private int renderSimulationBuildRequirements(GuiGraphics guiGraphics, int y) {
        guiGraphics.drawString(font,
                Component.translatable("gui.colossal_reactors.turbine_builder.simulation.required_for_build"),
                leftPos + SIM_PANEL_X, y, GuiTextColors.PANEL_YELLOW_LIGHT, false);
        y += SIM_LINE_HEIGHT;
        return renderMaterialCounts(guiGraphics, y);
    }

    private int renderMaterialCounts(GuiGraphics guiGraphics, int y) {
        int textX = leftPos + SIM_PANEL_X;
        TurbineBuildMaterialCounter.BuildMaterialCounts counts = getMaterialCounts();
        boolean customPattern = menu.getRodPattern() == TurbineRodPatternLogic.PATTERN_CUSTOM;
        String unknown = "?";
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, textX, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.turbine_builder.calculate.frame_casings.label",
                Component.translatable("gui.colossal_reactors.turbine_builder.calculate.frame_casings.value",
                        GuiNumberFormat.format(counts.frameCasings())));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, textX, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.turbine_builder.calculate.face_casings.label",
                Component.translatable("gui.colossal_reactors.turbine_builder.calculate.face_casings.value",
                        GuiNumberFormat.format(counts.faceCasings())));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, textX, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.turbine_builder.calculate.closure_deck.label",
                Component.translatable("gui.colossal_reactors.turbine_builder.calculate.closure_deck.value",
                        GuiNumberFormat.format(counts.closureDeckCasings())));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, textX, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.turbine_builder.calculate.rods.label",
                Component.translatable("gui.colossal_reactors.turbine_builder.calculate.rods.value",
                        customPattern ? unknown : GuiNumberFormat.format(counts.rods())));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, textX, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.turbine_builder.calculate.rod_controllers.label",
                Component.translatable("gui.colossal_reactors.turbine_builder.calculate.rod_controllers.value",
                        customPattern ? "1" : GuiNumberFormat.format(counts.rodControllers())));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, textX, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.turbine_builder.calculate.blades.label",
                Component.translatable("gui.colossal_reactors.turbine_builder.calculate.blades.value",
                        customPattern ? unknown : GuiNumberFormat.format(counts.blades())));
        y = ReactorPanelText.drawMetricRow(guiGraphics, font, textX, y, SIM_LINE_HEIGHT,
                "gui.colossal_reactors.turbine_builder.calculate.coils.label",
                Component.translatable("gui.colossal_reactors.turbine_builder.calculate.coils.value",
                        GuiNumberFormat.format(counts.coilBlocks())));
        return y;
    }

    private TurbineBuildMaterialCounter.BuildMaterialCounts getMaterialCounts() {
        var p = builderSimParams();
        return TurbineBuildMaterialCounter.estimate(
                minecraft != null && minecraft.level != null ? minecraft.level.registryAccess() : net.minecraft.core.RegistryAccess.EMPTY,
                p.sizeLeft(), p.sizeRight(), p.sizeHeight(), p.sizeDepth(),
                p.placementAxisOrdinal(), p.rodPattern(), p.coilIndex(), p.coilLayerCount(), p.openTop());
    }

    @Nullable
    private ResourceLocation getSelectedGenerationId() {
        List<TurbineGenerationDefinition> gens = getVisibleGenerations();
        if (gens.isEmpty()) return null;
        int idx = Math.min(menu.getSimGenerationIndex(), gens.size() - 1);
        return gens.get(idx).generationId();
    }

    private TurbineSimulation.SimulationResult getSimulationResult() {
        if (minecraft == null || minecraft.level == null) {
            return new TurbineSimulation.SimulationResult(0, 0, 0, 0, 0, 1, 1);
        }
        var p = builderSimParams();
        return TurbineBuilderSimulation.run(minecraft.level.registryAccess(),
                p.sizeLeft(), p.sizeRight(), p.sizeHeight(), p.sizeDepth(),
                p.placementAxisOrdinal(), p.rodPattern(), p.coilIndex(), p.coilLayerCount(),
                getSelectedGenerationId());
    }

    /** Same size/coil/pattern fields as {@link net.unfamily.colossal_reactors.blockentity.TurbineBuilderBlockEntity} build. */
    private BuilderSimParams builderSimParams() {
        var be = menu.getBlockEntity();
        if (be != null) {
            return new BuilderSimParams(
                    be.getSizeLeft(), be.getSizeRight(), be.getSizeHeight(), be.getSizeDepth(),
                    be.getPlacementAxisIndex(), be.getRodPattern(), be.getSelectedCoilIndex(),
                    be.getCoilLayerCount(), be.isOpenTop());
        }
        // Menu L/R getters are swapped vs block entity field names; match BE layout when BE is unavailable.
        return new BuilderSimParams(
                menu.getSizeRight(), menu.getSizeLeft(), menu.getSizeH(), menu.getSizeD(),
                menu.getPlacementAxisOrdinal(), menu.getRodPattern(), menu.getSelectedCoilIndex(),
                menu.getCoilLayerCount(), menu.isOpenTop());
    }

    private record BuilderSimParams(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisOrdinal, int rodPattern, int coilIndex, int coilLayerCount, boolean openTop) {}

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
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (viewMode == ViewMode.SIMULATION) {
            Component simTitle = Component.translatable("gui.colossal_reactors.turbine_builder.simulation");
            int titleX = leftPos + (imageWidth - font.width(simTitle)) / 2;
            int titleY = topPos + 6;
            guiGraphics.drawString(font, simTitle, titleX, titleY, 0x404040, false);
            return;
        }
        updateButtonTooltips();

        int titleX = (imageWidth - font.width(title)) / 2;
        guiGraphics.drawString(font, title, titleX, 6, 0x404040, false);

        // Size: (L+R=X) x (Y+1) x (Z+1) — X and Y/Z as block count (+1)
        int totalW = menu.getSizeLeft() + menu.getSizeRight();
        Component sizeLabel = Component.translatable("gui.colossal_reactors.turbine_builder.size",
                menu.getSizeLeft(), menu.getSizeRight(), totalW + 1, menu.getSizeH() + 1, menu.getSizeD() + 1);
        int sizeX = (imageWidth - font.width(sizeLabel)) / 2;
        guiGraphics.drawString(font, sizeLabel, sizeX, SIZE_LABEL_Y, 0x404040, false);

        if (menu.isBuildProgressVisible()) {
            int percent = menu.getBuildProgressPercent();
            float t = Math.max(0, Math.min(100, percent)) / 100f;
            int r = (int) (255 * (1f - t));
            int g = (int) (255 * t);
            int progressColor = 0xFF000000 | (r << 16) | (g << 8);
            Component percentText = Component.literal(percent + "%");
            guiGraphics.drawString(font, percentText, buildPercentTextX(percentText),
                    buildPercentTextY(font.lineHeight), progressColor, false);
        }
        if (menu.isInvalidBlocksDetected()) {
            guiGraphics.drawString(font,
                    Component.translatable("gui.colossal_reactors.turbine_builder.warning.invalid_blocks"),
                    WARNING_RIGHT_X, buildInvalidBlocksTextY(font.lineHeight), 0xFF0000, false);
        }
    }

    private void renderMarkInputGhosts(GuiGraphics guiGraphics) {
        if (viewMode != ViewMode.BUILDER) return;
        if (menu.getBlockEntity() == null) return;
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            for (int i = 0; i < TurbineBuilderMenu.BUFFER_SLOTS; i++) {
                if (!menu.hasMarkInputFilter(i)) continue;
                Slot guiSlot = menu.getSlot(i);
                if (!guiSlot.getItem().isEmpty()) continue;
                ItemStack ghost = menu.getMarkInputFilter(i);
                if (ghost.isEmpty()) continue;
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(leftPos + guiSlot.x, topPos + guiSlot.y, 0f);
                guiGraphics.renderItem(ghost, 0, 0);
                guiGraphics.fill(0, 0, 16, 16, 0x80000000);
                guiGraphics.pose().popPose();
            }
        } finally {
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderTooltip(guiGraphics, mouseX, mouseY);
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
            guiGraphics.renderTooltip(font, tooltipLines, mouseX, mouseY);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (viewMode != ViewMode.BUILDER) {
            this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
            this.renderBg(guiGraphics, partialTick, mouseX, mouseY);
            this.renderLabels(guiGraphics, mouseX, mouseY);
            for (var renderable : this.renderables) {
                if (renderable instanceof net.minecraft.client.gui.components.AbstractWidget w && !w.visible) {
                    continue;
                }
                if (renderable instanceof net.minecraft.client.gui.components.Renderable r) {
                    r.render(guiGraphics, mouseX, mouseY, partialTick);
                }
            }
            simulationScrollbar.render(guiGraphics, leftPos, topPos);
            this.renderTooltip(guiGraphics, mouseX, mouseY);
        } else {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            renderMarkInputGhosts(guiGraphics);
            this.renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (viewMode != ViewMode.BUILDER && (keyCode == 256 || keyCode == 1)) {
            switchToBuilderView();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void togglePreview() {
        BlockPos builderPos = menu.getBlockPos();
        boolean enabling = !BuilderPreviewTracker.isPreviewActive(builderPos);
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
        PacketDistributor.sendToServer(new BuilderPreviewTogglePayload(builderPos, enabling, false));
    }

    private void updatePreviewButtonLabel() {
        if (buttonPreview != null) {
            buttonPreview.setMessage(Component.translatable(
                    previewButtonShowsHide
                            ? "gui.colossal_reactors.turbine_builder.preview.hide"
                            : "gui.colossal_reactors.turbine_builder.preview"));
        }
    }

    @Override
    public void onClose() {
        simulationScrollbar.disposeButtons(this::removeWidget);
        menu.setHideAllSlotsForSimulationView(false);
        super.onClose();
    }
}
