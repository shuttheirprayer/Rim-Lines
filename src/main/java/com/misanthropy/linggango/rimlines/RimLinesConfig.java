package com.misanthropy.linggango.rimlines;

import net.minecraftforge.common.ForgeConfigSpec;

public final class RimLinesConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue THICKNESS;
    public static final ForgeConfigSpec.DoubleValue STRENGTH;
    public static final ForgeConfigSpec.DoubleValue DEPTH_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue MAX_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue COLOR_RED;
    public static final ForgeConfigSpec.DoubleValue COLOR_GREEN;
    public static final ForgeConfigSpec.DoubleValue COLOR_BLUE;
    public static final ForgeConfigSpec.DoubleValue SCENE_INFLUENCE;
    public static final ForgeConfigSpec.DoubleValue DARKNESS_CUTOFF;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Lit outlines along terrain and mob silhouettes, and along the item held in first person.",
                "Off while a shader pack is active or graphics are on Fast.").push("rim_lines");
        ENABLED = builder.define("enabled", true);
        THICKNESS = builder.comment("Outline width in pixels.").defineInRange("thickness", 3.0D, 1.0D, 12.0D);
        STRENGTH = builder.defineInRange("strength", 0.65D, 0.0D, 5.0D);
        DEPTH_THRESHOLD = builder.comment("How big a depth step has to be before it gets an outline. Lower draws more lines.")
                .defineInRange("depthThreshold", 0.1D, 0.01D, 2.0D);
        MAX_DISTANCE = builder.comment("Outlines fade out by this many blocks away.")
                .defineInRange("maxDistance", 96.0D, 1.0D, 1024.0D);
        COLOR_RED = builder.defineInRange("colorRed", 0.65D, 0.0D, 2.0D);
        COLOR_GREEN = builder.defineInRange("colorGreen", 0.76D, 0.0D, 2.0D);
        COLOR_BLUE = builder.defineInRange("colorBlue", 1.0D, 0.0D, 2.0D);
        SCENE_INFLUENCE = builder.comment("How much the outline takes on the colour of the surface under it.")
                .defineInRange("sceneColorInfluence", 0.35D, 0.0D, 1.0D);
        DARKNESS_CUTOFF = builder.comment("Outlines fade out on surfaces darker than this and are gone below it.",
                        "Full strength at three times this brightness. 0 keeps outlines in the dark.")
                .defineInRange("darknessCutoff", 0.04D, 0.0D, 0.3D);
        builder.pop();
        SPEC = builder.build();
    }

    private RimLinesConfig() {
    }
}
