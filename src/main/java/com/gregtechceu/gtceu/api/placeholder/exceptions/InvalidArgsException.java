package com.gregtechceu.gtceu.api.placeholder.exceptions;

import net.minecraft.network.chat.Component;

public class InvalidArgsException extends PlaceholderException {

    public InvalidArgsException() {
        super(Component.translatable("gtpm.computer_monitor_cover.error.invalid_args").getString());
    }
}
