package com.gregtechceu.gtceu.common.machine.multiblock.steam;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LargeBoilerMachine extends WorkableMultiblockMachine implements LDLib2MachineUIProvider {

    public static final int TICKS_PER_STEAM_GENERATION = 5;
    private static final int THROTTLE_STEP = 5;
    private static final int MIN_THROTTLE = 25;
    private static final int MAX_THROTTLE = 100;
    private static final ResourceLocation ADJUST_LARGE_BOILER_THROTTLE_ACTION = GTCEu
            .id("adjust_large_boiler_throttle");
    private static final ResourceLocation THROTTLE_DIRECTION_FIELD = SyncFieldData.key("direction");

    static {
        SyncActionDispatchers.server().register(new LargeBoilerThrottleActionHandler());
    }

    @Getter
    public final int maxTemperature, heatSpeed;
    @SaveField
    @SyncToClient
    @Getter
    private int currentTemperature, throttle;
    @Nullable
    protected TickableSubscription temperatureSubs;
    @SyncToClient
    private int steamGenerated;

    public LargeBoilerMachine(BlockEntityCreationInfo info, int maxTemperature, int heatSpeed) {
        super(info, new LargeBoilerRecipeLogic());
        this.maxTemperature = maxTemperature;
        this.heatSpeed = heatSpeed;
        this.throttle = 100;
    }

    //////////////////////////////////////
    // ****** Recipe Logic ******//
    //////////////////////////////////////

    @Override
    public LargeBoilerMachine.LargeBoilerRecipeLogic getRecipeLogic() {
        return (LargeBoilerMachine.LargeBoilerRecipeLogic) super.getRecipeLogic();
    }

    @Override
    public void formStructure(String structureName) {
        super.formStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        updateSteamSubscription();
    }

    @Override
    public void invalidateStructure(String structureName) {
        super.invalidateStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        updateSteamSubscription();
    }

    @Override
    public void onUnload() {
        if (temperatureSubs != null) {
            temperatureSubs.unsubscribe();
            temperatureSubs = null;
        }
        super.onUnload();
    }

    protected void updateSteamSubscription() {
        if (currentTemperature > 0) {
            temperatureSubs = subscribeServerTick(temperatureSubs, this::updateCurrentTemperature);
        } else if (temperatureSubs != null) {
            temperatureSubs.unsubscribe();
            temperatureSubs = null;
        }
    }

    @SuppressWarnings("unchecked")
    protected void updateCurrentTemperature() {
        if (getWorkLogic().isWorking()) {
            if (getOffsetTimer() % 10 == 0) {
                if (currentTemperature < getMaxTemperature()) {
                    setCurrentTemperature(Mth.clamp(currentTemperature + heatSpeed * 10, 0, getMaxTemperature()));
                }
            }
        } else if (currentTemperature > 0) {
            setCurrentTemperature(currentTemperature - getCoolDownRate());
        }

        if (isFormed() && getOffsetTimer() % TICKS_PER_STEAM_GENERATION == 0) {
            var maxDrain = currentTemperature * throttle * TICKS_PER_STEAM_GENERATION /
                    (ConfigHolder.INSTANCE.machines.largeBoilers.steamPerWater * 100);
            if (currentTemperature < 100) {
                setSteamGenerated(0);
            } else if (maxDrain > 0) { // if maxDrain is 0 because throttle is too low, skip trying to make steam
                // drain water
                var drainWater = List.of(SizedFluidIngredient.of(Fluids.WATER, maxDrain));
                List<IRecipeHandler<?>> inputTanks = new ArrayList<>();
                inputTanks.addAll(getCapabilitiesFlat(IO.IN, FluidRecipeCapability.CAP));
                inputTanks.addAll(getCapabilitiesFlat(IO.BOTH, FluidRecipeCapability.CAP));
                for (IRecipeHandler<?> tank : inputTanks) {
                    drainWater = (List<SizedFluidIngredient>) tank.handleRecipe(IO.IN, null, drainWater, false);
                    if (drainWater == null || drainWater.isEmpty()) {
                        break;
                    }
                }
                var drained = (drainWater == null || drainWater.isEmpty()) ? maxDrain :
                        maxDrain - drainWater.getFirst().amount();

                setSteamGenerated(drained * ConfigHolder.INSTANCE.machines.largeBoilers.steamPerWater);

                if (drained > 0) {
                    // fill steam
                    var fillSteam = List.of(SizedFluidIngredient.of(GTMaterials.Steam.getFluid(steamGenerated)));
                    List<IRecipeHandler<?>> outputTanks = new ArrayList<>();
                    outputTanks.addAll(getCapabilitiesFlat(IO.OUT, FluidRecipeCapability.CAP));
                    outputTanks.addAll(getCapabilitiesFlat(IO.BOTH, FluidRecipeCapability.CAP));
                    for (IRecipeHandler<?> tank : outputTanks) {
                        fillSteam = (List<SizedFluidIngredient>) tank.handleRecipe(IO.OUT, null, fillSteam, false);
                        if (fillSteam == null) break;
                    }
                }

                // check explosion
                if (drained < maxDrain) {
                    GTUtil.doExplosion(getLevel(), getBlockPos(), 2f);
                    var center = getBlockPos().below().relative(getFrontFacing().getOpposite());
                    if (GTValues.RNG.nextInt(100) > 80) {
                        GTUtil.doExplosion(getLevel(), center, 2f);
                    }
                    for (Direction x : Direction.Plane.HORIZONTAL) {
                        for (Direction y : Direction.Plane.HORIZONTAL) {
                            if (GTValues.RNG.nextInt(100) > 80) {
                                GTUtil.doExplosion(getLevel(), center.relative(x).relative(y), 2f);
                            }
                        }
                    }
                }
            }
        }
        updateSteamSubscription();
    }

    protected int getCoolDownRate() {
        return 1;
    }

    @Override
    public boolean onWorking() {
        boolean value = super.onWorking();
        if (currentTemperature < getMaxTemperature()) {
            setCurrentTemperature(Math.max(1, currentTemperature));
            updateSteamSubscription();
        }
        return value;
    }

    /**
     * Recipe Modifier for <b>Large Boiler Machines</b> - can be used as a valid {@link RecipeModifier}
     * <p>
     * Does not modify recipe. Real recipe duration is determined by
     * {@link LargeBoilerRecipeLogic#modifyFuelBurnTime(int)}
     * </p>
     *
     * @param machine a {@link LargeBoilerMachine}
     * @param recipe  recipe
     * @return A {@link ModifierFunction} for the given Large Boiler and recipe
     */
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        return ModifierFunction.IDENTITY;
    }

    public void addDisplayText(List<Component> textList) {
        for (var part : getParts()) {
            part.addMultiText(textList);
        }
        if (isFormed()) {
            textList.add(Component.translatable("gtpm.multiblock.large_boiler.temperature",
                    currentTemperature + 274, maxTemperature + 274));
            textList.add(Component.translatable("gtpm.multiblock.large_boiler.steam_output",
                    steamGenerated / TICKS_PER_STEAM_GENERATION));

            var throttleText = Component.translatable("gtpm.multiblock.large_boiler.throttle",
                    ChatFormatting.AQUA.toString() + getThrottle() + "%")
                    .withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("gtpm.multiblock.large_boiler.throttle.tooltip"))));
            textList.add(throttleText);

            var buttonText = Component.translatable("gtpm.multiblock.large_boiler.throttle_modify");
            buttonText.append(" ");
            buttonText.append(GTComponentPanelElement.withButton(Component.literal("[-]"), "sub"));
            buttonText.append(" ");
            buttonText.append(GTComponentPanelElement.withButton(Component.literal("[+]"), "add"));
            textList.add(buttonText);
        }
    }

    public IGuiTexture getScreenTexture() {
        return GuiTextures.DISPLAY_STEAM.get(maxTemperature > 800);
    }

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, 176, 216);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND));
        root.addChild(createDisplayScreen(player, holder));
        root.addChild(UITemplate.bindPlayerInventoryLDLib2(player.getInventory(), GuiTextures.SLOT, 7, 134, true));
        return UI.of(root);
    }

    private GTScrollerViewElement createDisplayScreen(Player player, MachineUIHolder holder) {
        GTScrollerViewElement screen = new GTScrollerViewElement(7, 4, 162, 121);
        screen.style(style -> style.backgroundTexture(getScreenTexture()));
        screen.viewPort(viewPort -> viewPort
                .layout(layout -> layout.paddingAll(0))
                .style(style -> style.backgroundTexture(getScreenTexture())));
        screen.scrollerStyle(style -> style
                .mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .horizontalScrollDisplay(ScrollDisplay.NEVER));
        screen.addScrollViewChild(createTitleLabel());
        screen.addScrollViewChild(createDisplayTextPanel(player, holder));
        return screen;
    }

    private GTLabelElement createTitleLabel() {
        GTLabelElement label = new GTLabelElement(4, 5, 154, 10,
                self().getBlockState().getBlock().getDescriptionId(), true);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private GTComponentPanelElement createDisplayTextPanel(Player player, MachineUIHolder holder) {
        return new GTComponentPanelElement(4, 17, this::addDisplayText)
                .setMaxWidthLimit(150)
                .clickHandler((componentData, clickData) -> adjustLDLib2Throttle(player, holder, componentData));
    }

    private void adjustLDLib2Throttle(Player player, MachineUIHolder holder, String componentData) {
        int direction = readThrottleButtonDirection(componentData);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createAdjustLargeBoilerThrottleAction(direction));
        }
    }

    private void adjustThrottle(int direction) {
        setThrottle(Mth.clamp(throttle + direction * THROTTLE_STEP, MIN_THROTTLE, MAX_THROTTLE));
    }

    void setThrottle(int throttle) {
        if (this.throttle != throttle) {
            this.throttle = throttle;
        }
        this.getRecipeLogic().modifyFuelBurnTime(this.throttle);
    }

    void setCurrentTemperature(int currentTemperature) {
        if (this.currentTemperature == currentTemperature) {
            return;
        }
        this.currentTemperature = currentTemperature;
    }

    void setSteamGenerated(int steamGenerated) {
        if (this.steamGenerated == steamGenerated) {
            return;
        }
        this.steamGenerated = steamGenerated;
    }

    private static int readThrottleButtonDirection(String componentData) {
        return switch (componentData) {
            case "sub" -> -1;
            case "add" -> 1;
            default -> throw new IllegalArgumentException("Unknown large boiler throttle action: " + componentData);
        };
    }

    private static SyncActionData createAdjustLargeBoilerThrottleAction(int direction) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(THROTTLE_DIRECTION_FIELD, new JsonPrimitive(direction))
                        .build())
                .build();
        return new SyncActionData(ADJUST_LARGE_BOILER_THROTTLE_ACTION, direction > 0 ? 1 : 0, payload);
    }

    private static final class LargeBoilerThrottleActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return ADJUST_LARGE_BOILER_THROTTLE_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof LargeBoilerMachine;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readThrottleDirection(fields, THROTTLE_DIRECTION_FIELD) != null;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof LargeBoilerMachine machine)) {
                throw new IllegalStateException("Large boiler throttle action received a non-large-boiler machine.");
            }
            machine.adjustThrottle(requireThrottleDirection(context.payload(), THROTTLE_DIRECTION_FIELD));
        }
    }

    private static int requireThrottleDirection(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Large boiler throttle action payload is missing field data.");
        }
        Integer value = readThrottleDirection(fields, field);
        if (value == null) {
            throw new IllegalStateException("Large boiler throttle action payload is missing " + field + ".");
        }
        return value;
    }

    private static @Nullable Integer readThrottleDirection(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            int direction = primitive.getAsInt();
            if (direction == -1 || direction == 1) {
                return direction;
            }
        }
        return null;
    }

    public static class LargeBoilerRecipeLogic extends RecipeLogic {

        @SaveField
        @SyncToClient
        @Getter
        int currentThrottle;

        public LargeBoilerRecipeLogic() {
            super();
            currentThrottle = 100;
        }

        @Override
        public LargeBoilerMachine getMachine() {
            return (LargeBoilerMachine) super.getMachine();
        }

        @Override
        protected List<Class<?>> validMachineClasses() {
            return List.of(LargeBoilerMachine.class);
        }

        public void setCurrentThrottle(int currentThrottle) {
            if (this.currentThrottle == currentThrottle) {
                return;
            }
            this.currentThrottle = currentThrottle;
        }

        @Override
        public void setupRecipe(GTRecipe recipe) {
            super.setupRecipe(recipe);
            if (lastRecipe != null) {
                setCurrentThrottle(getMachine().getThrottle());
                duration = (int) Math.round(lastRecipe.duration / (currentThrottle / 100.0));
            }
        }

        public void modifyFuelBurnTime(int newThrottle) {
            if (lastRecipe != null) {
                double newThrottleMultiplier = (double) currentThrottle / newThrottle;
                duration = (int) Math.round(lastRecipe.duration / (newThrottle / 100.0));
                progress = (int) Math.round(newThrottleMultiplier * progress);
            }
            setCurrentThrottle(newThrottle);
        }
    }
}
