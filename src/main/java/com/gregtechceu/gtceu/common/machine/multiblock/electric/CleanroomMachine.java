package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.IFilterType;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.feature.IDataInfoProvider;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.multiblock.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.item.behavior.PortableScannerBehavior;
import com.gregtechceu.gtceu.common.machine.electric.HullMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.DiodePartMachine;
import com.gregtechceu.gtceu.common.machine.trait.CleanroomLogic;
import com.gregtechceu.gtceu.common.machine.trait.CleanroomProviderTrait;
import com.gregtechceu.gtceu.common.machine.trait.CleanroomReceiverTrait;
import com.gregtechceu.gtceu.data.pattern.StructurePatternKey;
import com.gregtechceu.gtceu.data.pattern.StructurePatternResolver;
import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

public class CleanroomMachine extends WorkableElectricMultiblockMachine
                              implements IDisplayUIMachine, IDataInfoProvider {

    public static final int CLEAN_AMOUNT_THRESHOLD = 95;
    public static final int MIN_CLEAN_AMOUNT = 0;

    public static final int MIN_RADIUS = 2;
    public static final int MIN_DEPTH = 4;

    @SaveField
    private int lDist = 0, rDist = 0, bDist = 0, fDist = 0, hDist = 0;
    @Nullable
    private CleanroomType cleanroomType = null;
    @SaveField
    private int cleanAmount;
    // runtime
    @Getter
    @Nullable
    private EnergyContainerList inputEnergyContainers;
    @Getter
    @Nullable
    private Collection<CleanroomReceiverTrait> cleanroomReceivers;

    private final CleanroomProviderTrait cleanroomProviderTrait;

    public CleanroomMachine(BlockEntityCreationInfo info) {
        super(info, new CleanroomLogic());
        this.cleanroomProviderTrait = attachTrait(new CleanroomProviderTrait());
    }

    @Override
    public CleanroomLogic getRecipeLogic() {
        return (CleanroomLogic) super.getRecipeLogic();
    }

    //////////////////////////////////////
    // *** Multiblock LifeCycle ***//
    //////////////////////////////////////

    @Override
    public void formStructure(String structureName) {
        super.formStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        initializeAbilities();
        IFilterType filterType = getMultiblockState(DEFAULT_STRUCTURE).getMatchContext().get("FilterType");
        if (filterType != null) {
            this.cleanroomType = filterType.getCleanroomType();
        } else {
            this.cleanroomType = CleanroomType.CLEANROOM;
        }
        cleanroomProviderTrait.setProvidedTypes(Set.of(this.cleanroomType));

        // bind cleanroom
        if (cleanroomReceivers != null) {
            this.cleanroomReceivers.forEach(CleanroomReceiverTrait::removeCleanroom);
            this.cleanroomReceivers = null;
        }
        Set<CleanroomReceiverTrait> receivers = getMultiblockState(DEFAULT_STRUCTURE).getMatchContext().getOrCreate(
                "cleanroomReceiver",
                Sets::newHashSet);
        this.cleanroomReceivers = ImmutableSet.copyOf(receivers);
        this.cleanroomReceivers.forEach(receiver -> receiver.setCleanroomProvider(cleanroomProviderTrait));

        // max progress is based roughly on the dimensions of the structure: ((w * d) ^ .8 * h)
        // taller cleanrooms take longer than wider ones
        // minimum of 100 is a 5x5x5 cleanroom: 125-25=100 ticks
        // max sized CR is around 1142 ticks per progression

        var area = (lDist + rDist + 1) * (bDist + fDist + 1);
        var duration = Math.pow(area, 0.8) * (hDist + 1);
        this.getRecipeLogic().setDuration(Math.max(100, (int) duration));
    }

    @Override
    public void invalidateStructure(String structureName) {
        super.invalidateStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        this.inputEnergyContainers = null;
        this.cleanAmount = MIN_CLEAN_AMOUNT;
        cleanroomProviderTrait.setActive(false);
        if (cleanroomReceivers != null) {
            this.cleanroomReceivers.forEach(CleanroomReceiverTrait::removeCleanroom);
            this.cleanroomReceivers = null;
        }
    }

    @Override
    public boolean shouldAddPartToController(IMultiPart part) {
        var cache = getMultiblockState(DEFAULT_STRUCTURE).getCache();
        for (Direction side : GTUtil.DIRECTIONS) {
            if (!cache.contains(part.self().getBlockPos().relative(side))) {
                return true;
            }
        }
        return false;
    }

    protected void initializeAbilities() {
        List<IEnergyContainer> energyContainers = new ArrayList<>();
        Long2ObjectMap<IO> ioMap = getMultiblockState(DEFAULT_STRUCTURE).getMatchContext().getOrCreate("ioMap",
                Long2ObjectMaps::emptyMap);
        for (IMultiPart part : getParts()) {
            if (isPartIgnored(part)) continue;
            IO io = ioMap.getOrDefault(part.self().getBlockPos().asLong(), IO.BOTH);
            if (io == IO.NONE || io == IO.OUT) continue;
            var handlerLists = part.getRecipeHandlers();
            for (var handlerList : handlerLists) {
                if (!handlerList.isValid(io)) continue;
                handlerList.getCapability(EURecipeCapability.CAP).stream()
                        .filter(IEnergyContainer.class::isInstance)
                        .map(IEnergyContainer.class::cast)
                        .forEach(energyContainers::add);
            }

            if (part instanceof IMaintenanceMachine maintenanceMachine) {
                getRecipeLogic().setMaintenanceMachine(maintenanceMachine);
            }
        }
        this.inputEnergyContainers = new EnergyContainerList(energyContainers);
        getRecipeLogic().setEnergyContainer(this.inputEnergyContainers);
        this.tier = Math.min(GTValues.MAX, GTUtil.getFloorTierByVoltage(getMaxVoltage()));
    }

    @SuppressWarnings("RedundantIfStatement") // `return false` being a separate statement is better for readability
    private static boolean isPartIgnored(IMultiPart part) {
        if (part instanceof DiodePartMachine) return true;
        if (part instanceof HullMachine) return true;

        return false;
    }

    /**
     * Scans for blocks around the controller to update the dimensions
     */
    public void updateStructureDimensions() {
        Level world = getLevel();
        if (world == null) return;
        Direction front = getFrontFacing();
        Direction back = front.getOpposite();
        Direction left = front.getCounterClockWise();
        Direction right = left.getOpposite();

        BlockPos.MutableBlockPos lPos = getBlockPos().mutable();
        BlockPos.MutableBlockPos rPos = getBlockPos().mutable();
        BlockPos.MutableBlockPos fPos = getBlockPos().mutable();
        BlockPos.MutableBlockPos bPos = getBlockPos().mutable();
        BlockPos.MutableBlockPos hPos = getBlockPos().mutable();

        // find the distances from the controller to the plascrete blocks on one horizontal axis and the Y axis
        // repeatable aisles take care of the second horizontal axis
        int lDist = 0;
        int rDist = 0;
        int bDist = 0;
        int fDist = 0;
        int hDist = 0;

        // find the left, right, back, and front distances for the structure pattern
        // maximum size is 15x15x15 including walls, so check 7 block radius around the controller for blocks
        for (int i = 1; i < 8; i++) {
            if (lDist == 0 && isBlockEdge(world, lPos, left)) lDist = i;
            if (rDist == 0 && isBlockEdge(world, rPos, right)) rDist = i;
            if (bDist == 0 && isBlockEdge(world, bPos, back)) bDist = i;
            if (fDist == 0 && isBlockEdge(world, fPos, front)) fDist = i;
            if (lDist != 0 && rDist != 0 && bDist != 0 && fDist != 0) break;
        }

        // height is diameter instead of radius, so it needs to be done separately
        for (int i = 1; i < 15; i++) {
            if (isBlockFloor(world, hPos, Direction.DOWN)) hDist = i;
            if (hDist != 0) break;
        }

        if (Math.abs(lDist - rDist) > 1 || Math.abs(bDist - fDist) > 1) {
            this.isFormed = false;
            return;
        }

        if (lDist < MIN_RADIUS || rDist < MIN_RADIUS || bDist < MIN_RADIUS || fDist < MIN_RADIUS || hDist < MIN_DEPTH) {
            this.isFormed = false;
            return;
        }

        this.lDist = lDist;
        this.rDist = rDist;
        this.bDist = bDist;
        this.fDist = fDist;
        this.hDist = hDist;
    }

    /**
     * @param world     the world to check
     * @param pos       the pos to check and move
     * @param direction the direction to move
     * @return if a block is a valid wall block at pos moved in direction
     */
    public boolean isBlockEdge(Level world, BlockPos.MutableBlockPos pos,
                               Direction direction) {
        var state = world.getBlockState(pos.move(direction));
        return state == getCasingState() || state == getGlassState();
    }

    /**
     * @param world     the world to check
     * @param pos       the pos to check and move
     * @param direction the direction to move
     * @return if a block is a valid floor block at pos moved in direction
     */
    public boolean isBlockFloor(Level world, BlockPos.MutableBlockPos pos,
                                Direction direction) {
        var state = world.getBlockState(pos.move(direction));
        return state == getCasingState() || state == getGlassState() || state.is(CustomTags.CLEANROOM_FLOORS);
    }

    @Override
    public BlockPattern getPattern(String structureName) {
        if (!DEFAULT_STRUCTURE.equals(structureName)) {
            return super.getPattern(structureName);
        }
        // return the default structure, even if there is no valid size found
        // this means auto-build will still work, and prevents terminal crashes.
        if (getLevel() != null) updateStructureDimensions();

        // these can sometimes get set to 0 when loading the game, breaking JEI
        if (lDist < MIN_RADIUS) lDist = MIN_RADIUS;
        if (rDist < MIN_RADIUS) rDist = MIN_RADIUS;
        if (bDist < MIN_RADIUS) bDist = MIN_RADIUS;
        if (fDist < MIN_RADIUS) fDist = MIN_RADIUS;
        if (hDist < MIN_DEPTH) hDist = MIN_DEPTH;

        if (this.getFrontFacing() == Direction.EAST || this.getFrontFacing() == Direction.WEST) {
            int tmp = lDist;
            lDist = rDist;
            rDist = tmp;
        }

        StringBuilder[] floorLayer = new StringBuilder[fDist + bDist + 1];
        List<StringBuilder[]> wallLayers = new ArrayList<>();
        StringBuilder[] ceilingLayer = new StringBuilder[fDist + bDist + 1];

        for (int i = 0; i < floorLayer.length; i++) {
            floorLayer[i] = new StringBuilder(lDist + rDist + 1);
            ceilingLayer[i] = new StringBuilder(lDist + rDist + 1);
        }

        for (int i = 0; i < hDist - 1; i++) {
            wallLayers.add(new StringBuilder[fDist + bDist + 1]);
            for (int j = 0; j < fDist + bDist + 1; j++) {
                var s = new StringBuilder(lDist + rDist + 1);
                wallLayers.get(i)[j] = s;
            }
        }

        for (int i = 0; i < lDist + rDist + 1; i++) {
            for (int j = 0; j < fDist + bDist + 1; j++) {
                if (i == 0 || i == lDist + rDist || j == 0 || j == fDist + bDist) { // all edges
                    floorLayer[j].append('A'); // floor edge
                    for (int k = 0; k < hDist - 1; k++) {
                        wallLayers.get(k)[j].append('W'); // walls
                    }
                    ceilingLayer[j].append('D'); // ceiling edge
                } else { // not edges
                    if (i == lDist && j == fDist) { // very center
                        floorLayer[j].append('K');
                    } else {
                        floorLayer[j].append('E'); // floor valid blocks
                    }
                    for (int k = 0; k < hDist - 1; k++) {
                        wallLayers.get(k)[j].append('I');
                    }
                    if (i == lDist && j == fDist) { // very center
                        ceilingLayer[j].append('~'); // controller
                    } else {
                        ceilingLayer[j].append('F'); // filter
                    }
                }
            }
        }

        String[] f = new String[bDist + fDist + 1];
        for (int i = 0; i < floorLayer.length; i++) {
            f[i] = floorLayer[i].toString();
        }
        String[] m = new String[bDist + fDist + 1];
        for (int i = 0; i < wallLayers.get(0).length; i++) {
            m[i] = wallLayers.get(0)[i].toString();
        }
        String[] c = new String[bDist + fDist + 1];
        for (int i = 0; i < ceilingLayer.length; i++) {
            c[i] = ceilingLayer[i].toString();
        }

        BlockPattern baseline = FactoryBlockPattern.start(LEFT, FRONT, UP)
                .aisle("~")
                .build();
        return StructurePatternResolver.rebuildRuntimeStringArrayPattern(
                this.getDefinition(),
                StructurePatternKey.main(this.getDefinition().getId()),
                baseline,
                List.of(
                        new StructurePatternResolver.Unit(Collections.singletonList(f), 1, 1),
                        new StructurePatternResolver.Unit(Collections.singletonList(m), wallLayers.size(),
                                wallLayers.size()),
                        new StructurePatternResolver.Unit(Collections.singletonList(c), 1, 1)));
    }

    // protected to allow easy addition of addon "cleanrooms"
    protected BlockState getCasingState() {
        return GTBlocks.PLASTCRETE.getDefaultState();
    }

    protected BlockState getGlassState() {
        return GTBlocks.CLEANROOM_GLASS.getDefaultState();
    }

    public int getDynamicPatternFloorArea() {
        return (lDist + rDist + 1) * (bDist + fDist + 1);
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        if (isFormed()) {
            var maxVoltage = getMaxVoltage();
            if (maxVoltage > 0) {
                String voltageName = GTValues.VNF[GTUtil.getFloorTierByVoltage(maxVoltage)];
                textList.add(Component.translatable("gtpm.multiblock.max_energy_per_tick", maxVoltage, voltageName));
            }

            if (cleanroomType != null) {
                textList.add(Component.translatable(cleanroomType.translationKey()));
            }

            if (!isWorkingEnabled()) {
                textList.add(Component.translatable("gtpm.multiblock.work_paused"));

            } else if (isActive()) {
                textList.add(Component.translatable("gtpm.multiblock.running"));
                int currentProgress = (int) (recipeLogic.getProgressPercent() * 100);
                double maxInSec = (float) recipeLogic.getDuration() / 20.0f;
                double currentInSec = (float) recipeLogic.getProgress() / 20.0f;
                textList.add(
                        Component.translatable("gtpm.multiblock.progress", String.format("%.2f", (float) currentInSec),
                                String.format("%.2f", (float) maxInSec), currentProgress));
            } else {
                textList.add(Component.translatable("gtpm.multiblock.idling"));
            }

            if (recipeLogic.isWaiting()) {
                textList.add(Component.translatable("gtpm.multiblock.waiting")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
            }

            if (cleanroomProviderTrait.isActive()) {
                textList.add(Component.translatable("gtpm.multiblock.cleanroom.clean_state"));
            } else {
                textList.add(Component.translatable("gtpm.multiblock.cleanroom.dirty_state"));
            }
            textList.add(Component.translatable("gtpm.multiblock.cleanroom.clean_amount", this.cleanAmount));
            textList.add(Component.translatable("gtpm.multiblock.dimensions.0"));
            textList.add(Component.translatable("gtpm.multiblock.dimensions.1", lDist + rDist + 1, hDist + 1,
                    fDist + bDist + 1));
        } else {
            Component tooltip = Component.translatable("gtpm.multiblock.invalid_structure.tooltip")
                    .withStyle(ChatFormatting.GRAY);
            textList.add(Component.translatable("gtpm.multiblock.invalid_structure")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip))));
        }
    }

    /**
     * Adjust the cleanroom's clean amount
     *
     * @param amount the amount of cleanliness to increase/decrease by
     */
    public void adjustCleanAmount(int amount) {
        // do not allow negative cleanliness nor cleanliness above 100
        this.cleanAmount = Mth.clamp(this.cleanAmount + amount, 0, 100);
        cleanroomProviderTrait.setActive(this.cleanAmount >= CLEAN_AMOUNT_THRESHOLD);
    }

    @Override
    public List<Component> getDataInfo(PortableScannerBehavior.DisplayMode mode) {
        if (mode == PortableScannerBehavior.DisplayMode.SHOW_ALL ||
                mode == PortableScannerBehavior.DisplayMode.SHOW_MACHINE_INFO) {
            return Collections.singletonList(Component.translatable(
                    cleanroomProviderTrait.isActive() ? "gtpm.multiblock.cleanroom.clean_state" :
                            "gtpm.multiblock.cleanroom.dirty_state"));
        }
        return new ArrayList<>();
    }

    @Override
    public long getMaxVoltage() {
        if (inputEnergyContainers == null) return GTValues.LV;
        return inputEnergyContainers.getInputVoltage();
    }

    // Do not allow cleanroom to be paused due to custom recipe logic
    @Override
    public boolean isWorkingEnabled() {
        return true;
    }

    @Override
    public void setWorkingEnabled(boolean ignored) {}
}
