package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.factory.CoverUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolderContext;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyPreviewPage;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2CircuitFancyConfiguratorActions;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2DirectionalCoverActions;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerList;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.ProxySlotRecipeHandler;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public class MEPatternBufferProxyPartMachine extends TieredIOPartMachine
                                             implements IDataStickInteractable, LDLib2MachineUIProvider,
                                             LDLib2FancyPartUIProvider, MEPatternBufferProxyActionTarget {

    static {
        MEPatternBufferProxyActions.initialize();
    }

    @Getter
    private final ProxySlotRecipeHandler proxySlotRecipeHandler;

    @SaveField
    @Getter
    @SyncToClient
    private @Nullable BlockPos bufferPos;

    @SaveField
    @SyncToClient
    @Getter
    private UUID proxyIncarnation = UUID.randomUUID();

    @SaveField
    @SyncToClient
    @Getter
    private long linkRevision;

    private @Nullable MEPatternBufferPartMachine buffer = null;
    private boolean bufferResolved = false;

    public MEPatternBufferProxyPartMachine(BlockEntityCreationInfo info) {
        super(info, GTValues.LuV, IO.IN);
        proxySlotRecipeHandler = new ProxySlotRecipeHandler(this, MEPatternBufferPartMachine.MAX_PATTERN_COUNT);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) this.setBuffer(bufferPos);
    }

    @Override
    public List<RecipeHandlerList> getRecipeHandlers() {
        return proxySlotRecipeHandler.getProxySlotHandlers();
    }

    public void setBuffer(@Nullable BlockPos pos) {
        advanceLinkRevision();
        bufferPos = pos;
        replaceResolvedBuffer(resolveBuffer(pos));
        syncDataHolder.markClientSyncFieldDirty("bufferPos");
    }

    @Nullable
    public MEPatternBufferPartMachine getBuffer() {
        MEPatternBufferPartMachine resolved = resolveBuffer(bufferPos);
        if (!bufferResolved || resolved != buffer) {
            advanceLinkRevision();
            replaceResolvedBuffer(resolved);
        }
        return buffer;
    }

    private @Nullable MEPatternBufferPartMachine resolveBuffer(@Nullable BlockPos pos) {
        var level = getLevel();
        if (level == null || pos == null) {
            return null;
        }
        if (MetaMachine.getMachine(level, pos) instanceof MEPatternBufferPartMachine machine &&
                machine.supportsMEPatternBufferActions()) {
            return machine;
        }
        return null;
    }

    private void replaceResolvedBuffer(@Nullable MEPatternBufferPartMachine resolved) {
        MEPatternBufferPartMachine previous = buffer;
        if (previous == resolved && bufferResolved) {
            return;
        }
        if (previous != null) {
            previous.removeProxy(this);
        }
        proxySlotRecipeHandler.clearProxy();
        buffer = resolved;
        bufferResolved = true;
        if (resolved != null) {
            resolved.addProxy(this);
            if (!isRemote()) {
                proxySlotRecipeHandler.updateProxy(resolved);
            }
        }
    }

    private void advanceLinkRevision() {
        if (!isRemote()) {
            linkRevision = Math.incrementExact(linkRevision);
            syncDataHolder.markClientSyncFieldDirty("linkRevision");
        }
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        MEPatternBufferPartMachine linked = getBuffer();
        return linked != null && MachineOwner.canOpenOwnerMachine(player, this) &&
                MachineOwner.canOpenOwnerMachine(player, linked);
    }

    @Override
    public boolean openLDLib2UI(MetaMachine machine, ServerPlayer player) {
        if (machine != this) {
            throw new IllegalArgumentException("Pattern Buffer Proxy cannot open a menu for another machine.");
        }
        return MEPatternBufferProxyUIMenuType.openUI(this, player);
    }

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        if (holder instanceof MEPatternBufferProxyUIContext context) {
            return context.getMachine() == this &&
                    context.getMachineDefinitionId().equals(GTAEMachines.ME_PATTERN_BUFFER_PROXY.getId()) &&
                    getDefinition() == GTAEMachines.ME_PATTERN_BUFFER_PROXY && context.isOpeningValid(player);
        }
        MEPatternBufferPartMachine linked = getBuffer();
        return holder.getMachine() == this &&
                holder.getMachineDefinitionId().equals(GTAEMachines.ME_PATTERN_BUFFER_PROXY.getId()) &&
                getDefinition() == GTAEMachines.ME_PATTERN_BUFFER_PROXY && linked != null &&
                MachineOwner.canOpenOwnerMachine(player, this) && MachineOwner.canOpenOwnerMachine(player, linked);
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        LDLib2FancyUIProvider page;
        if (holder instanceof MEPatternBufferProxyUIContext context) {
            page = createLDLib2Page(player, context);
        } else {
            page = createLDLib2Page(player, holder, MachineUIHelper::sendAction,
                    () -> player.level().isClientSide(), UIEvent::isShiftDown);
        }
        return UI.of(new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight()));
    }

    /** Creates the proxy-local preview used by a surrounding multiblock without opening its linked buffer. */
    @Override
    public LDLib2FancyUIProvider createLDLib2FancyPage(Player player, MachineUIHolder holder) {
        return new LDLib2FancyPreviewPage(this, player, holder,
                new LDLib2FancyUIProvider.PageGroupingData(
                        "gtpm.multiblock.page_switcher.io.import", 1));
    }

    MEPatternBufferProxyUIHolder createLDLib2UIHolder(ServerPlayer player) {
        MachineUIHolder preflight = new MachineUIHolderContext(player, this);
        PatternBufferProxyOpening opening = requireOpening(player, preflight);
        MEPatternBufferProxyViewSnapshot snapshot = MEPatternBufferProxyViewSnapshot.capture(
                opening.buffer(), player.level().registryAccess());
        return new MEPatternBufferProxyUIHolder(player, this, opening.buffer(), opening.identity(), snapshot);
    }

    private LDLib2FancyUIProvider createLDLib2Page(Player player, MEPatternBufferProxyUIContext context) {
        BooleanSupplier openingValid = () -> context.isOpeningValid(player);
        return createLDLib2Page(player, context, context.getPatternBufferView(), context.getOpeningIdentity(),
                MachineUIHelper::sendAction, openingValid,
                () -> player.level().isClientSide() && openingValid.getAsBoolean(), UIEvent::isShiftDown);
    }

    /**
     * Builds an opening-scoped Proxy page with injectable action transport for direct interaction tests.
     */
    LDLib2FancyUIProvider createLDLib2Page(Player player, MachineUIHolder holder,
                                           BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                           BooleanSupplier canSendAction, Predicate<UIEvent> shiftDown) {
        PatternBufferProxyOpening opening = requireOpening(player, holder);
        BooleanSupplier openingValid = () -> matchesOpening(player, holder, opening);
        return createLDLib2Page(player, holder, opening.buffer(), opening.identity(), actionSender, openingValid,
                canSendAction, shiftDown);
    }

    private LDLib2FancyUIProvider createLDLib2Page(
                                                   Player player, MachineUIHolder holder,
                                                   MEPatternBufferPartMachine linkedBuffer,
                                                   MEPatternBufferProxyOpeningIdentity opening,
                                                   BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                                   BooleanSupplier openingValid,
                                                   BooleanSupplier canSendAction,
                                                   Predicate<UIEvent> shiftDown) {
        if (!openingValid.getAsBoolean()) {
            throw new IllegalStateException("Pattern Buffer Proxy page requires its exact opening context.");
        }
        BooleanSupplier guardedCanSendAction = () -> openingValid.getAsBoolean() && canSendAction.getAsBoolean();
        MEPatternBufferPartMachine.PatternBufferPageActions pageActions = createOpeningPageActions(opening);
        return linkedBuffer.createOpeningScopedLDLib2Page(
                player, holder, actionSender, openingValid, guardedCanSendAction, shiftDown, pageActions);
    }

    /**
     * Creates the allowlisted page commands bound to one already captured opening identity.
     */
    MEPatternBufferPartMachine.PatternBufferPageActions createOpeningPageActions(
                                                                                 MEPatternBufferProxyOpeningIdentity opening) {
        return new ProxyPatternBufferPageActions(opening);
    }

    private PatternBufferProxyOpening requireOpening(Player player, MachineUIHolder holder) {
        if (holder.getMachine() != this ||
                !holder.getMachineDefinitionId().equals(GTAEMachines.ME_PATTERN_BUFFER_PROXY.getId()) ||
                getDefinition() != GTAEMachines.ME_PATTERN_BUFFER_PROXY) {
            throw new IllegalArgumentException("Pattern Buffer Proxy page holder must resolve the opened definition.");
        }
        MEPatternBufferPartMachine linked = getBuffer();
        BlockPos linkedPos = bufferPos;
        if (linked == null || linkedPos == null || !MachineOwner.canOpenOwnerMachine(player, this) ||
                !MachineOwner.canOpenOwnerMachine(player, linked)) {
            throw new IllegalStateException("Pattern Buffer Proxy page requires an authorized linked buffer.");
        }
        MEPatternBufferProxyOpeningIdentity identity = new MEPatternBufferProxyOpeningIdentity(
                proxyIncarnation, linkedPos, linkRevision);
        PatternBufferProxyOpening opening = new PatternBufferProxyOpening(linked, identity);
        if (!matchesOpening(player, holder, opening)) {
            throw new IllegalStateException("Pattern Buffer Proxy link changed while its page was opening.");
        }
        return opening;
    }

    private boolean matchesOpening(Player player, MachineUIHolder holder, PatternBufferProxyOpening opening) {
        return holder.getMachine() == this &&
                holder.getMachineDefinitionId().equals(GTAEMachines.ME_PATTERN_BUFFER_PROXY.getId()) &&
                getDefinition() == GTAEMachines.ME_PATTERN_BUFFER_PROXY &&
                matchesLinkedBuffer(opening.identity()) && getBuffer() == opening.buffer() &&
                MachineOwner.canOpenOwnerMachine(player, this) &&
                MachineOwner.canOpenOwnerMachine(player, opening.buffer());
    }

    private boolean matchesLinkedBuffer(MEPatternBufferProxyOpeningIdentity opening) {
        var level = getLevel();
        if (level == null || MetaMachine.getMachine(level, getBlockPos()) != this ||
                getDefinition() != GTAEMachines.ME_PATTERN_BUFFER_PROXY ||
                !proxyIncarnation.equals(opening.proxyIncarnation()) || linkRevision != opening.linkRevision() ||
                !opening.bufferPos().equals(bufferPos)) {
            return false;
        }
        MEPatternBufferPartMachine linked = getBuffer();
        return linked != null && proxyIncarnation.equals(opening.proxyIncarnation()) &&
                linkRevision == opening.linkRevision() && opening.bufferPos().equals(bufferPos) &&
                linked.getBlockPos().equals(opening.bufferPos());
    }

    private MEPatternBufferPartMachine requireLinkedBuffer(MEPatternBufferProxyOpeningIdentity opening) {
        if (!matchesLinkedBuffer(opening)) {
            throw new IllegalStateException("Pattern Buffer Proxy action no longer matches its opened link.");
        }
        MEPatternBufferPartMachine linked = buffer;
        if (linked == null) {
            throw new IllegalStateException("Pattern Buffer Proxy action lost its validated linked buffer.");
        }
        return linked;
    }

    @Override
    public boolean canExecuteMEPatternBufferProxyAction(ServerPlayer player,
                                                        MEPatternBufferProxyOpeningIdentity opening) {
        if (!matchesLinkedBuffer(opening)) {
            return false;
        }
        MEPatternBufferPartMachine linked = buffer;
        return linked != null && MachineOwner.canOpenOwnerMachine(player, this) &&
                MachineOwner.canOpenOwnerMachine(player, linked);
    }

    @Override
    public void setLinkedMEPatternBufferName(MEPatternBufferProxyOpeningIdentity opening, String name) {
        requireLinkedBuffer(opening).setMEPatternBufferName(name);
    }

    @Override
    public void refundLinkedMEPatternBufferContents(MEPatternBufferProxyOpeningIdentity opening) {
        requireLinkedBuffer(opening).refundMEPatternBufferContents();
    }

    @Override
    public int getLinkedMEPatternBufferShareTankCount(MEPatternBufferProxyOpeningIdentity opening) {
        return requireLinkedBuffer(opening).getMEPatternBufferShareTankCount();
    }

    @Override
    public void clickLinkedMEPatternBufferShareTank(ServerPlayer player,
                                                    MEPatternBufferProxyOpeningIdentity opening,
                                                    int tankIndex, boolean shiftDown) {
        requireLinkedBuffer(opening).clickMEPatternBufferShareTank(player, tankIndex, shiftDown);
    }

    @Override
    public void configureLinkedMEPatternBufferCircuit(ServerPlayer player,
                                                      MEPatternBufferProxyOpeningIdentity opening,
                                                      int configuration) {
        SyncActionData action = LDLib2CircuitFancyConfiguratorActions
                .createSetMachineCircuitConfigurationAction(configuration);
        dispatchLinkedAction(player, opening, action, "circuit");
    }

    @Override
    public void configureLinkedMEPatternBufferCover(ServerPlayer player,
                                                    MEPatternBufferProxyOpeningIdentity opening,
                                                    Direction side,
                                                    MEPatternBufferProxyCoverOperation operation) {
        if (operation == MEPatternBufferProxyCoverOperation.OPEN) {
            MEPatternBufferPartMachine linked = requireLinkedBuffer(opening);
            var cover = linked.getCoverContainer().getCoverAtSide(side);
            if (cover == null || !CoverUIHelper.canOpenLDLib2(cover, player)) {
                throw new IllegalStateException("Pattern Buffer Proxy cover-open request is no longer valid.");
            }
            BooleanSupplier anchorValid = () -> canExecuteMEPatternBufferProxyAction(player, opening);
            if (!CoverUIHelper.open(cover, player, getBlockPos(), anchorValid)) {
                throw new IllegalStateException("Pattern Buffer Proxy failed to open its linked cover UI.");
            }
            return;
        }
        SyncActionData action = switch (operation) {
            case PLACE -> LDLib2DirectionalCoverActions.createPlaceCoverAction(side);
            case REMOVE -> LDLib2DirectionalCoverActions.createRemoveCoverAction(side);
            case OPEN -> throw new IllegalStateException("Cover-open action must use the anchored holder path.");
        };
        dispatchLinkedAction(player, opening, action, "cover " + operation);
    }

    private void dispatchLinkedAction(ServerPlayer player, MEPatternBufferProxyOpeningIdentity opening,
                                      SyncActionData action, String description) {
        MEPatternBufferPartMachine linked = requireLinkedBuffer(opening);
        if (!SyncActionDispatchers.server().dispatch(
                SyncActionContext.machine(player, linked, action, opening.bufferPos()))) {
            throw new IllegalStateException("Pattern Buffer Proxy linked " + description + " action was rejected.");
        }
    }

    private record PatternBufferProxyOpening(MEPatternBufferPartMachine buffer,
                                             MEPatternBufferProxyOpeningIdentity identity) {}

    /** Creates every opening-bound command rendered by one Proxy page. */
    private static final class ProxyPatternBufferPageActions
                                                             implements
                                                             MEPatternBufferPartMachine.PatternBufferPageActions {

        private final MEPatternBufferProxyOpeningIdentity opening;

        private ProxyPatternBufferPageActions(MEPatternBufferProxyOpeningIdentity opening) {
            this.opening = opening;
        }

        @Override
        public SyncActionData createSetNameAction(String name) {
            return MEPatternBufferProxyActions.createSetNameAction(opening, name);
        }

        @Override
        public SyncActionData createRefundAllAction() {
            return MEPatternBufferProxyActions.createRefundAllAction(opening);
        }

        @Override
        public SyncActionData createClickShareTankAction(int tankIndex, boolean shiftDown) {
            return MEPatternBufferProxyActions.createClickShareTankAction(opening, tankIndex, shiftDown);
        }

        @Override
        public SyncActionData createSetCircuitConfigurationAction(int configuration) {
            return MEPatternBufferProxyActions.createSetCircuitConfigurationAction(opening, configuration);
        }

        @Override
        public SyncActionData createPlaceCoverAction(Direction side) {
            return MEPatternBufferProxyActions.createPlaceCoverAction(opening, side);
        }

        @Override
        public SyncActionData createRemoveCoverAction(Direction side) {
            return MEPatternBufferProxyActions.createRemoveCoverAction(opening, side);
        }

        @Override
        public SyncActionData createOpenCoverAction(Direction side) {
            return MEPatternBufferProxyActions.createOpenCoverAction(opening, side);
        }
    }

    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        setBuffer(null);
    }

    @Override
    public InteractionResult onDataStickUse(Player player, ItemStack dataStick) {
        BlockPos bufferPos = dataStick.get(GTDataComponents.DATA_COPY_POS);
        if (bufferPos != null) {
            setBuffer(bufferPos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
