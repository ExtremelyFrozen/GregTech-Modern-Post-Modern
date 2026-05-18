package com.gregtechceu.gtceu.data.pattern;

import com.gregtechceu.gtceu.GTCEu;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.javafmlmod.FMLModContainer;

import static net.neoforged.api.distmarker.Dist.DEDICATED_SERVER;

@Mod(value = GTCEu.MOD_ID, dist = DEDICATED_SERVER)
public class GTCEuServer {

    public GTCEuServer(IEventBus modBus, FMLModContainer container) {}
}
