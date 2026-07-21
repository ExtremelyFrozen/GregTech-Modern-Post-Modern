package com.gregtechceu.gtceu.client.bloom;

import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;
import java.util.function.Supplier;

public final class BloomRenderTicket {

    public static final BloomRenderTicket INVALID = new BloomRenderTicket();

    final @Nullable IRenderSetup renderSetup;
    private final @Nullable IBloomEffect render;
    final @Nullable Predicate<BloomRenderTicket> validityChecker;
    final @Nullable Supplier<@Nullable Level> worldContext;

    private boolean invalidated;

    private BloomRenderTicket() {
        this.renderSetup = null;
        this.render = null;
        this.validityChecker = null;
        this.worldContext = null;
        this.invalidated = true;
    }

    BloomRenderTicket(@Nullable IRenderSetup renderSetup, IBloomEffect render,
                      @Nullable Predicate<BloomRenderTicket> validityChecker,
                      @Nullable Supplier<@Nullable Level> worldContext) {
        this.renderSetup = renderSetup;
        if (render == null) {
            throw new NullPointerException("render == null");
        }
        this.render = render;
        this.validityChecker = validityChecker;
        this.worldContext = worldContext;
    }

    public boolean isValid() {
        return !this.invalidated;
    }

    public void invalidate() {
        this.invalidated = true;
    }

    IBloomEffect requireValidRender() {
        if (!isValid() || render == null) {
            throw new IllegalStateException("Only a valid bloom render ticket has a renderer.");
        }
        return render;
    }

    void checkValidity() {
        if (!this.invalidated && this.validityChecker != null && !this.validityChecker.test(this)) {
            invalidate();
        }
    }
}
