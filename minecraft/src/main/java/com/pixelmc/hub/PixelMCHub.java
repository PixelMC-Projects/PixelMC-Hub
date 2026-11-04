package com.pixelmc.hub;

import com.pixelmc.hub.client.ClientBootstrap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod(PixelMCHub.MOD_ID)
public final class PixelMCHub {
    public static final String MOD_ID = "pixelmc_hub";

    public PixelMCHub() {
        com.pixelmc.hub.network.HubNetwork.initialize();
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientBootstrap::initialize);
    }
}
