package com.misanthropy.linggango.rimlines;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(RimLines.MOD_ID)
public final class RimLines {
    public static final String MOD_ID = "rimlines";

    public RimLines() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, RimLinesConfig.SPEC);
    }
}
