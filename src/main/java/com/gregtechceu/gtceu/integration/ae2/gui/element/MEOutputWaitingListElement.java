package com.gregtechceu.gtceu.integration.ae2.gui.element;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputWaitingListActionTarget;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputWaitingListActions;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputWaitingListClientState;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputWaitingListEntry;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputWaitingListMenuSession;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputWaitingListReceiver;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputWaitingListSessionReceiver;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputWaitingListTarget;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputWaitingListUpdate;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualItemHeightMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.server.level.ServerPlayer;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Shared opening-scoped protocol and virtual viewport for item and fluid ME output waiting lists.
 *
 * <p>
 * Concrete elements only validate their AE key type and project one row. This base owns request authentication,
 * ordered chunk assembly, absolute publication deadlines, stale-holder rejection, and atomic list replacement.
 * </p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class MEOutputWaitingListElement extends UIElement
                                                 implements MEOutputWaitingListSessionReceiver {

    protected static final int ROW_WIDTH = 158;
    protected static final int ROW_HEIGHT = 18;

    private static final int VIEWPORT_HEIGHT = 54;
    private static final int OVERSCAN_PIXELS = ROW_HEIGHT;
    private static final int REQUEST_TIMEOUT_TICKS = 100;
    private static final int PUBLICATION_TIMEOUT_TICKS = 100;
    private static final long NO_DEADLINE = -1;

    private final MachineUIHolder targetHolder;
    private final MachineUIHolder actionHolder;
    private final MEOutputWaitingListTarget target;
    private final BiConsumer<MachineUIHolder, SyncActionData> actionSender;
    private final BooleanSupplier canSendAction;
    private final MEOutputWaitingListClientState clientState = new MEOutputWaitingListClientState();

    @Getter
    private final UUID openingId = UUID.randomUUID();
    @Getter
    private final MEOutputWaitingListMenuSession waitingListMenuSession = new MEOutputWaitingListMenuSession();
    @Getter
    private final VirtualScrollerView<MEOutputWaitingListEntry> scroller;
    @Getter
    private long revision = -1;
    @Getter
    private List<MEOutputWaitingListEntry> entries = List.of();

    private boolean resyncRequired;
    private boolean requestPending;
    private int nextRequestSequence;
    private int activeRequestSequence = -1;
    private int pendingRequestSequence = -1;
    private long requestDeadline = NO_DEADLINE;
    private long publicationDeadline = NO_DEADLINE;
    private @Nullable MEOutputWaitingListUpdate.Mode pendingPublicationMode;
    private long pendingPublicationRevision = -1;
    private @Nullable UUID acceptedMenuSessionId;
    private int acceptedMenuSessionSequence = -1;

    /**
     * Creates the fixed three-row viewport at the supplied page-relative position.
     */
    protected MEOutputWaitingListElement(int x, int y, MachineUIHolder targetHolder,
                                         MachineUIHolder actionHolder,
                                         BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                         BooleanSupplier canSendAction) {
        this.targetHolder = targetHolder;
        this.actionHolder = actionHolder;
        this.target = requireWaitingListTarget(targetHolder);
        this.actionSender = actionSender;
        this.canSendAction = canSendAction;

        setId(MEOutputWaitingListReceiver.elementId(target.pos()));
        UITemplate.setLDLib2Bounds(this, x, y, ROW_WIDTH, VIEWPORT_HEIGHT);
        scroller = createScroller();
        addChild(scroller);
    }

    /**
     * Creates the viewport at the legacy page position of {@code 5,20}.
     */
    protected MEOutputWaitingListElement(MachineUIHolder targetHolder, MachineUIHolder actionHolder,
                                         BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                         BooleanSupplier canSendAction) {
        this(5, 20, targetHolder, actionHolder, actionSender, canSendAction);
    }

    @Override
    public void screenTick() {
        expireIncompletePublication();
        requestFullStateIfNeeded();
        super.screenTick();
    }

    @Override
    public boolean matchesWaitingListTarget(MEOutputWaitingListTarget requestedTarget) {
        MEOutputWaitingListActionTarget actionTarget = currentWaitingListTarget();
        return target.equals(requestedTarget) && actionTarget != null &&
                actionTarget.getWaitingListTarget().equals(target);
    }

    @Override
    public boolean matchesWaitingListTarget(MetaMachine machine) {
        return targetHolder.getMachine() == machine &&
                machine instanceof MEOutputWaitingListActionTarget actionTarget &&
                actionTarget.getWaitingListTarget().equals(target);
    }

    @Override
    public boolean canRequestFull(ServerPlayer player) {
        MEOutputWaitingListActionTarget actionTarget = currentWaitingListTarget();
        return actionTarget != null && actionTarget.getWaitingListPublisher().canRequestFull(player);
    }

    @Override
    public boolean requestFull(ServerPlayer player, UUID requestOpeningId, int requestSequence) {
        MEOutputWaitingListActionTarget actionTarget = currentWaitingListTarget();
        return actionTarget != null &&
                actionTarget.getWaitingListPublisher().requestFull(player, requestOpeningId, requestSequence);
    }

    @Override
    public void applyWaitingListMenuSession(UUID challengeOpeningId, int requestSequence, UUID menuSessionId) {
        if (requestSequence < 0) {
            throw new IllegalArgumentException("ME output waiting-list session sequence must be non-negative.");
        }
        if (!openingId.equals(challengeOpeningId) || currentWaitingListTarget() == null ||
                !requestPending || requestSequence != pendingRequestSequence) {
            return;
        }
        if (menuSessionId.equals(acceptedMenuSessionId) && acceptedMenuSessionSequence == requestSequence) {
            return;
        }
        acceptedMenuSessionId = menuSessionId;
        acceptedMenuSessionSequence = requestSequence;
        requestDeadline = deadlineFromNow(REQUEST_TIMEOUT_TICKS);
        sendFullStateRequest(requestSequence);
    }

    @Override
    public void applyWaitingListUpdate(UUID updateOpeningId, int requestSequence,
                                       MEOutputWaitingListUpdate update) {
        if (!openingId.equals(updateOpeningId) || currentWaitingListTarget() == null) {
            return;
        }
        if (requestSequence < 0) {
            throw new IllegalArgumentException("ME output waiting-list request sequence must be non-negative.");
        }
        if (update.mode() == MEOutputWaitingListUpdate.Mode.FULL) {
            if (requestSequence != pendingRequestSequence) {
                return;
            }
        } else if (resyncRequired || pendingRequestSequence >= 0 || requestSequence != activeRequestSequence) {
            return;
        }

        MEOutputWaitingListClientState.ApplyResult result;
        try {
            validateEntries(update);
            result = update.mode() == MEOutputWaitingListUpdate.Mode.FULL ?
                    clientState.acceptAuthoritativeFull(update) : clientState.accept(update);
        } catch (IllegalArgumentException exception) {
            GTCEu.LOGGER.error("ME output waiting-list opening {} rejected a malformed publication",
                    openingId, exception);
            requireFullResync();
            return;
        }

        switch (result) {
            case APPLIED -> publishClientState(update.mode(), requestSequence);
            case REQUEST_FULL -> {
                GTCEu.LOGGER.warn(
                        "ME output waiting-list opening {} requires a full resync after revision {} chunk {}/{}",
                        openingId, update.revision(), update.chunkIndex(), update.chunkCount());
                requireFullResync();
            }
            case WAITING_FOR_CHUNKS -> {
                if (update.mode() == MEOutputWaitingListUpdate.Mode.FULL) {
                    requestPending = false;
                    requestDeadline = NO_DEADLINE;
                }
                startPublicationDeadline(update);
            }
            case IGNORED -> {}
        }
    }

    /**
     * Projects one type-checked waiting-list entry into a fixed-height display row.
     */
    protected abstract UIElement createRow(MEOutputWaitingListEntry entry);

    /**
     * Rejects a publication entry whose AE key type cannot be rendered by the concrete output element.
     */
    protected abstract void validateEntry(MEOutputWaitingListEntry entry);

    private VirtualScrollerView<MEOutputWaitingListEntry> createScroller() {
        VirtualScrollerView<MEOutputWaitingListEntry> view = new VirtualScrollerView<>();
        UITemplate.setLDLib2Bounds(view, 0, 0, ROW_WIDTH, VIEWPORT_HEIGHT);
        view.scrollerStyle(style -> style
                .mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .horizontalScrollDisplay(ScrollDisplay.NEVER)
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .minScrollPixel(ROW_HEIGHT)
                .maxScrollPixel(ROW_HEIGHT));
        view.virtualScrollerViewStyle(style -> style
                .itemHeightMode(VirtualItemHeightMode.FIXED)
                .estimatedItemHeight(ROW_HEIGHT)
                .overscanPixels(OVERSCAN_PIXELS));
        view.viewPort.layout(layout -> layout.paddingAll(0));
        view.viewPort.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        view.setItemUIProvider(this::createRow);
        view.setItems(entries);
        view.refreshVisibleItems(0, VIEWPORT_HEIGHT);
        return view;
    }

    private void validateEntries(MEOutputWaitingListUpdate update) {
        for (MEOutputWaitingListEntry entry : update.entries()) {
            validateEntry(entry);
        }
    }

    private void publishClientState(MEOutputWaitingListUpdate.Mode appliedMode, int requestSequence) {
        revision = clientState.revision();
        entries = clientState.entries();
        refreshRowsAndClampScroll();
        clearPublicationDeadline();
        if (appliedMode == MEOutputWaitingListUpdate.Mode.FULL) {
            resyncRequired = false;
            requestPending = false;
            requestDeadline = NO_DEADLINE;
            activeRequestSequence = requestSequence;
            pendingRequestSequence = -1;
        }
    }

    private void refreshRowsAndClampScroll() {
        float previousMaxOffset = Math.max(0, scroller.getTotalVirtualHeight() - VIEWPORT_HEIGHT);
        float previousOffset = scroller.verticalScroller.getNormalizedValue() * previousMaxOffset;
        scroller.setItems(entries);
        float maxOffset = Math.max(0, scroller.getTotalVirtualHeight() - VIEWPORT_HEIGHT);
        float clampedOffset = Math.min(previousOffset, maxOffset);
        float normalizedOffset = maxOffset == 0 ? 0 : clampedOffset / maxOffset;
        scroller.verticalScroller.setNormalizedValue(normalizedOffset, false);
        scroller.refreshVisibleItems(clampedOffset, VIEWPORT_HEIGHT);
    }

    private void requireFullResync() {
        clientState.discardPendingPublication();
        resyncRequired = true;
        requestPending = false;
        pendingRequestSequence = -1;
        requestDeadline = NO_DEADLINE;
        clearPublicationDeadline();
    }

    private void expireIncompletePublication() {
        if (!clientState.hasPendingPublication() || publicationDeadline == NO_DEADLINE ||
                GTValues.CLIENT_TIME < publicationDeadline) {
            return;
        }
        GTCEu.LOGGER.warn("ME output waiting-list opening {} timed out waiting for publication chunks",
                openingId);
        requireFullResync();
    }

    private void startPublicationDeadline(MEOutputWaitingListUpdate update) {
        if (publicationDeadline != NO_DEADLINE && pendingPublicationMode == update.mode() &&
                pendingPublicationRevision == update.revision()) {
            return;
        }
        pendingPublicationMode = update.mode();
        pendingPublicationRevision = update.revision();
        publicationDeadline = deadlineFromNow(PUBLICATION_TIMEOUT_TICKS);
    }

    private void clearPublicationDeadline() {
        publicationDeadline = NO_DEADLINE;
        pendingPublicationMode = null;
        pendingPublicationRevision = -1;
    }

    private void requestFullStateIfNeeded() {
        if (requestPending) {
            if (requestDeadline != NO_DEADLINE && GTValues.CLIENT_TIME < requestDeadline) {
                return;
            }
            GTCEu.LOGGER.warn("ME output waiting-list opening {} timed out waiting for full state", openingId);
            requestPending = false;
            pendingRequestSequence = -1;
            requestDeadline = NO_DEADLINE;
        }
        if (pendingRequestSequence >= 0 || !canSendAction.getAsBoolean() || revision >= 0 && !resyncRequired) {
            return;
        }
        if (nextRequestSequence == Integer.MAX_VALUE) {
            GTCEu.LOGGER.error("ME output waiting-list opening {} exhausted its request sequence", openingId);
            return;
        }
        int requestSequence = nextRequestSequence++;
        pendingRequestSequence = requestSequence;
        requestPending = true;
        requestDeadline = deadlineFromNow(REQUEST_TIMEOUT_TICKS);
        sendFullStateRequest(requestSequence);
    }

    private void sendFullStateRequest(int requestSequence) {
        try {
            SyncActionData action = acceptedMenuSessionId == null ?
                    MEOutputWaitingListActions.createRequestFullAction(target, openingId, requestSequence) :
                    MEOutputWaitingListActions.createRequestFullAction(
                            target, openingId, requestSequence, acceptedMenuSessionId);
            actionSender.accept(actionHolder, action);
        } catch (RuntimeException exception) {
            GTCEu.LOGGER.error("ME output waiting-list opening {} failed to send full-state request",
                    openingId, exception);
            if (pendingRequestSequence == requestSequence) {
                requestPending = false;
                pendingRequestSequence = -1;
                requestDeadline = NO_DEADLINE;
            }
        }
    }

    private static long deadlineFromNow(int timeoutTicks) {
        return GTValues.CLIENT_TIME + timeoutTicks;
    }

    private static MEOutputWaitingListTarget requireWaitingListTarget(MachineUIHolder holder) {
        MetaMachine machine = holder.getMachine();
        if (!(machine instanceof MEOutputWaitingListActionTarget actionTarget)) {
            throw new IllegalArgumentException("ME output waiting-list holder must resolve an action target.");
        }
        MEOutputWaitingListTarget target = actionTarget.getWaitingListTarget();
        if (!holder.getPos().equals(target.pos()) ||
                !holder.getMachineDefinitionId().equals(target.machineDefinitionId())) {
            throw new IllegalArgumentException("ME output waiting-list holder identity does not match its machine.");
        }
        return target;
    }

    private @Nullable MEOutputWaitingListActionTarget currentWaitingListTarget() {
        MetaMachine machine = targetHolder.getMachine();
        if (machine instanceof MEOutputWaitingListActionTarget actionTarget &&
                actionTarget.getWaitingListTarget().equals(target)) {
            return actionTarget;
        }
        return null;
    }
}
