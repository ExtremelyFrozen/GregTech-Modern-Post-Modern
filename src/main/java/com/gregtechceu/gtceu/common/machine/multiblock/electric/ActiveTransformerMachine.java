package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.WorkLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.multiblock.TraceabilityPredicate;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.*;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.gregtechceu.gtceu.api.multiblock.Predicates.abilities;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ActiveTransformerMachine extends WorkableElectricMultiblockMachine
                                      implements IControllable, IFancyUIMachine, IDisplayUIMachine {

    private IEnergyContainer powerOutput;
    private IEnergyContainer powerInput;
    protected ConditionalSubscriptionHandler converterSubscription;

    public ActiveTransformerMachine(BlockEntityCreationInfo info) {
        super(info);
        this.powerOutput = new EnergyContainerList(new ArrayList<>());
        this.powerInput = new EnergyContainerList(new ArrayList<>());

        this.converterSubscription = new ConditionalSubscriptionHandler(this, this::convertEnergyTick,
                this::isSubscriptionActive);
    }

    public void convertEnergyTick() {
        if (isWorkingEnabled()) {
            getWorkLogic()
                    .setStatus(isSubscriptionActive() ? WorkLogic.Status.WORKING : WorkLogic.Status.SUSPEND);
        }
        if (isWorkingEnabled()) {
            long canDrain = powerInput.getEnergyStored();
            long totalDrained = powerOutput.changeEnergy(canDrain);
            powerInput.removeEnergy(totalDrained);
        }
        converterSubscription.updateSubscription();
    }

    @SuppressWarnings("RedundantIfStatement") // It is cleaner to have the final return true separate.
    protected boolean isSubscriptionActive() {
        if (!isFormed()) return false;

        if (powerInput == null || powerInput.getEnergyStored() <= 0) return false;
        if (powerOutput == null) return false;
        if (powerOutput.getEnergyStored() >= powerOutput.getEnergyCapacity()) return false;

        return true;
    }

    @Override
    public void formStructure(String structureName) {
        super.formStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        // capture all energy containers
        List<IEnergyContainer> powerInput = new ArrayList<>();
        List<IEnergyContainer> powerOutput = new ArrayList<>();
        Long2ObjectMap<IO> ioMap = getMultiblockState(DEFAULT_STRUCTURE).getMatchContext().getOrDefault("ioMap",
                Long2ObjectMaps.emptyMap());

        for (IMultiPart part : getPrioritySortedParts()) {
            IO io = ioMap.getOrDefault(part.self().getBlockPos().asLong(), IO.BOTH);
            if (io == IO.NONE) continue;
            var handlerLists = part.getRecipeHandlers();
            for (var handlerList : handlerLists) {
                if (!handlerList.isValid(io)) continue;

                var containers = handlerList.getCapability(EURecipeCapability.CAP).stream()
                        .filter(IEnergyContainer.class::isInstance)
                        .map(IEnergyContainer.class::cast)
                        .toList();

                if (handlerList.getHandlerIO().support(IO.IN)) {
                    powerInput.addAll(containers);
                } else if (handlerList.getHandlerIO().support(IO.OUT)) {
                    powerOutput.addAll(containers);
                }

                traitSubscriptions
                        .add(handlerList.subscribe(converterSubscription::updateSubscription, EURecipeCapability.CAP));
            }
        }

        // Invalidate the structure if there is not at least one output and one input
        if (powerInput.isEmpty() || powerOutput.isEmpty()) {
            this.invalidateStructure(DEFAULT_STRUCTURE);
        }

        this.powerOutput = new EnergyContainerList(powerOutput);
        this.powerInput = new EnergyContainerList(powerInput);

        converterSubscription.updateSubscription();
    }

    @NotNull
    private List<IMultiPart> getPrioritySortedParts() {
        return getParts().stream().sorted(Comparator.comparingInt(part -> {
            if (part instanceof MetaMachine partMachine) {
                var partBlock = partMachine.getBlockState().getBlock();

                if (PartAbility.OUTPUT_ENERGY.isApplicable(partBlock))
                    return 1;

                if (PartAbility.SUBSTATION_OUTPUT_ENERGY.isApplicable(partBlock))
                    return 2;

                if (PartAbility.OUTPUT_LASER.isApplicable(partBlock))
                    return 3;
            }

            return 4;
        })).toList();
    }

    @Override
    public void invalidateStructure(String structureName) {
        boolean shouldExplode = DEFAULT_STRUCTURE.equals(structureName) &&
                (isWorkingEnabled() && getWorkLogic().getStatus() == WorkLogic.Status.WORKING) &&
                !ConfigHolder.INSTANCE.machines.harmlessActiveTransformers;
        float explosionStrength = 6f + getTier();
        super.invalidateStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        if (shouldExplode) {
            GTUtil.doExplosion(getLevel(), getBlockPos(), explosionStrength);
        }
        this.powerOutput = new EnergyContainerList(new ArrayList<>());
        this.powerInput = new EnergyContainerList(new ArrayList<>());
        getWorkLogic().setStatus(WorkLogic.Status.SUSPEND);
        converterSubscription.unsubscribe();
    }

    public static TraceabilityPredicate getHatchPredicates() {
        return abilities(PartAbility.INPUT_ENERGY).setPreviewCount(1)
                .or(abilities(PartAbility.OUTPUT_ENERGY).setPreviewCount(2))
                .or(abilities(PartAbility.SUBSTATION_INPUT_ENERGY).setPreviewCount(1))
                .or(abilities(PartAbility.SUBSTATION_OUTPUT_ENERGY).setPreviewCount(1))
                .or(abilities(PartAbility.INPUT_LASER).setPreviewCount(1))
                .or(abilities(PartAbility.OUTPUT_LASER).setPreviewCount(1));
    }

    @Override
    public void addDisplayText(@NotNull List<Component> textList) {
        // super.addDisplayText(textList); idek what it does stop doing what you do for a minute pls
        // Assume That the Structure is ALWAYS formed, and has at least 1 In and 1 Out, there is never a case where this
        // does not occur.
        if (isFormed()) {
            if (!isWorkingEnabled()) {
                textList.add(Component.translatable("gtpm.multiblock.work_paused"));
            } else if (isActive()) {
                textList.add(Component.translatable("gtpm.multiblock.running"));
                textList.add(Component
                        .translatable("gtpm.multiblock.active_transformer.max_input",
                                FormattingUtil.formatNumbers(
                                        Math.abs(powerInput.getInputVoltage() * powerInput.getInputAmperage()))));
                textList.add(Component
                        .translatable("gtpm.multiblock.active_transformer.max_output",
                                FormattingUtil.formatNumbers(
                                        Math.abs(powerOutput.getOutputVoltage() * powerOutput.getOutputAmperage()))));
                textList.add(Component
                        .translatable("gtpm.multiblock.active_transformer.average_in",
                                FormattingUtil.formatNumbers(Math.abs(powerInput.getInputPerSec() / 20))));
                textList.add(Component
                        .translatable("gtpm.multiblock.active_transformer.average_out",
                                FormattingUtil.formatNumbers(Math.abs(powerOutput.getOutputPerSec() / 20))));
                if (!ConfigHolder.INSTANCE.machines.harmlessActiveTransformers) {
                    textList.add(Component
                            .translatable("gtpm.multiblock.active_transformer.danger_enabled"));
                }
            } else {
                textList.add(Component.translatable("gtpm.multiblock.idling"));
            }
        }
    }

    @Override
    public @NotNull Widget createUIWidget() {
        var group = new WidgetGroup(0, 0, 182 + 8, 117 + 8);
        group.addWidget(new DraggableScrollableWidgetGroup(4, 4, 182, 117).setBackground(getScreenTexture())
                .addWidget(new LabelWidget(4, 5, self().getBlockState().getBlock().getDescriptionId()))
                .addWidget(new ComponentPanelWidget(4, 17, this::addDisplayText)
                        .setMaxWidthLimit(150)
                        .clickHandler(this::handleDisplayClick)));
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        return group;
    }

    @Override
    public @NotNull ModularUI createUI(@NotNull Player entityPlayer) {
        return new ModularUI(198, 208, this, entityPlayer).widget(new FancyMachineUIWidget(this, 198, 208));
    }
}
