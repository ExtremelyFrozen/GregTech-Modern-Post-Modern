package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.factory.GTDynamicItemSlotContainerMenu;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotBinding;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotDefinition;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotManifest;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.server.level.ServerPlayer;

import lombok.Builder;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Connects one dynamic-slot UI tree to the opening-scoped Vanilla slot handshake without owning machine state.
 *
 * <p>
 * Business UI code supplies UUID-based source and layout callbacks. This element verifies every appended bundle,
 * retains it for tombstones, and applies only protocol-confirmed interaction and page-selection state.
 * </p>
 */
public final class GTDynamicItemSlotSessionElement extends UIElement {

    private final LongSupplier sourceRevision;
    private final Supplier<List<DynamicItemSlotDefinition>> definitionSource;
    private final Function<DynamicItemSlotBinding, List<GTDynamicItemSlotElement>> bindingAppender;
    private final Predicate<DynamicItemSlotBinding> bindingResolved;
    private final Predicate<DynamicItemSlotBinding> bindingSelectable;
    private final Consumer<Optional<DynamicItemSlotBinding>> selectionListener;
    private final Map<UUID, GTDynamicItemSlotBundle> bundles = new LinkedHashMap<>();

    /**
     * Creates the protocol boundary used by one UI opening; callers should use the generated builder for readability.
     */
    @Builder
    public GTDynamicItemSlotSessionElement(LongSupplier sourceRevision,
                                           Supplier<List<DynamicItemSlotDefinition>> definitionSource,
                                           Function<DynamicItemSlotBinding, List<GTDynamicItemSlotElement>> bindingAppender,
                                           Predicate<DynamicItemSlotBinding> bindingResolved,
                                           Predicate<DynamicItemSlotBinding> bindingSelectable,
                                           Consumer<Optional<DynamicItemSlotBinding>> selectionListener) {
        this.sourceRevision = requireCallback(sourceRevision, "sourceRevision");
        this.definitionSource = requireCallback(definitionSource, "definitionSource");
        this.bindingAppender = requireCallback(bindingAppender, "bindingAppender");
        this.bindingResolved = requireCallback(bindingResolved, "bindingResolved");
        this.bindingSelectable = requireCallback(bindingSelectable, "bindingSelectable");
        this.selectionListener = requireCallback(selectionListener, "selectionListener");
    }

    /**
     * Returns the authoritative source revision used for the next server manifest.
     */
    public long sourceRevision() {
        long revision = sourceRevision.getAsLong();
        if (revision < 0) {
            throw new IllegalStateException("dynamic item slot source revision must be non-negative: " + revision);
        }
        return revision;
    }

    /**
     * Returns a snapshot of the server's current ordered logical targets.
     */
    public List<DynamicItemSlotDefinition> definitions() {
        List<DynamicItemSlotDefinition> definitions = definitionSource.get();
        if (definitions == null) {
            throw new IllegalStateException("dynamic item slot definition source returned null");
        }
        return List.copyOf(definitions);
    }

    /**
     * Appends and verifies a binding exactly once at the slot ids assigned by the server manifest.
     */
    public GTDynamicItemSlotBundle appendBinding(DynamicItemSlotBinding binding) {
        GTDynamicItemSlotBundle existing = bundles.get(binding.bindingId());
        if (existing != null) {
            if (!existing.matchesIdentity(binding)) {
                throw new IllegalStateException("dynamic item slot binding identity changed after append");
            }
            return existing;
        }
        if (binding.tombstone()) {
            throw new IllegalStateException("cannot append a dynamic item slot binding as a tombstone");
        }

        List<GTDynamicItemSlotElement> elements = bindingAppender.apply(binding);
        GTDynamicItemSlotBundle bundle = new GTDynamicItemSlotBundle(binding, elements);
        verifyRegisteredSlotIds(bundle);
        bundle.setInteractionEnabled(false);
        bundles.put(binding.bindingId(), bundle);
        return bundle;
    }

    /**
     * Returns all lifecycle ids whose physical slots have already been registered in this menu.
     */
    public Set<UUID> appendedBindingIds() {
        return Set.copyOf(bundles.keySet());
    }

    /**
     * Returns the present bindings whose UUID-based business targets currently resolve on this side.
     */
    public Set<UUID> resolvedPresentBindingIds(DynamicItemSlotManifest manifest) {
        Set<UUID> resolved = new LinkedHashSet<>();
        for (DynamicItemSlotBinding binding : manifest.bindings()) {
            if (binding.present() && bundles.containsKey(binding.bindingId()) && bindingResolved.test(binding)) {
                resolved.add(binding.bindingId());
            }
        }
        return Set.copyOf(resolved);
    }

    /**
     * Returns whether the authoritative side currently permits this present binding to be selected.
     */
    public boolean isBindingSelectable(DynamicItemSlotBinding binding) {
        return binding.present() && bundles.containsKey(binding.bindingId()) && bindingResolved.test(binding) &&
                bindingSelectable.test(binding);
    }

    /**
     * Applies the state machine's exact per-binding interaction decision to every registered slot.
     */
    public void applyInteractionState(Predicate<UUID> interactiveBinding) {
        if (interactiveBinding == null) {
            throw new IllegalArgumentException("interactiveBinding must not be null");
        }
        bundles.forEach((bindingId, bundle) -> bundle.setInteractionEnabled(interactiveBinding.test(bindingId)));
    }

    /**
     * Applies the server-confirmed page only after the corresponding protocol transition completed.
     */
    public void applySelection(Optional<UUID> selectedBindingId, DynamicItemSlotManifest manifest) {
        if (selectedBindingId == null) {
            throw new IllegalArgumentException("selectedBindingId must not be null");
        }
        Optional<DynamicItemSlotBinding> selectedBinding = selectedBindingId.map(bindingId -> manifest.bindings()
                .stream()
                .filter(binding -> binding.present() && binding.bindingId().equals(bindingId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("selected dynamic item slot binding is not present")));
        selectionListener.accept(selectedBinding);
    }

    /**
     * Retries client preparation when UUID targets become available after the MANIFEST packet.
     */
    @Override
    public void screenTick() {
        super.screenTick();
        ModularUI modularUI = getModularUI();
        if (modularUI != null && modularUI.player != null &&
                modularUI.getMenu() instanceof GTDynamicItemSlotContainerMenu menu) {
            menu.retryClientPreparation(modularUI.player);
        }
    }

    /**
     * Publishes the newest server source revision after the previous manifest becomes active.
     */
    @Override
    public void serverTick() {
        super.serverTick();
        ModularUI modularUI = getModularUI();
        if (modularUI != null && modularUI.player instanceof ServerPlayer player &&
                modularUI.getMenu() instanceof GTDynamicItemSlotContainerMenu menu) {
            menu.refreshManifest(player);
        }
    }

    private void verifyRegisteredSlotIds(GTDynamicItemSlotBundle bundle) {
        ModularUI modularUI = getModularUI();
        if (modularUI == null || !(modularUI.getMenu() instanceof GTDynamicItemSlotContainerMenu menu)) {
            throw new IllegalStateException("dynamic item slots can only append after their menu is installed");
        }
        for (int offset = 0; offset < bundle.elements().size(); offset++) {
            GTDynamicItemSlotElement element = bundle.elements().get(offset);
            int expectedSlotId = bundle.binding().firstSlotId() + offset;
            if (!element.matchesBindingRoute(bundle.binding(), offset) || element.getModularUI() != modularUI ||
                    element.getSlot().index != expectedSlotId ||
                    menu.slots.get(expectedSlotId) != element.getSlot() ||
                    menu.asModularUIHolderMenu().getItemSlot(element.getSlot()) != element) {
                throw new IllegalStateException(
                        "dynamic item slot bundle was not registered at manifest slot id " + expectedSlotId);
            }
        }
    }

    private static <T> T requireCallback(T callback, String name) {
        if (callback == null) {
            GTCEu.LOGGER.error("Cannot create a dynamic item slot session without callback {}", name);
            throw new IllegalArgumentException(name + " callback must not be null");
        }
        return callback;
    }
}
