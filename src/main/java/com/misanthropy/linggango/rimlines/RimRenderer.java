package com.misanthropy.linggango.rimlines;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.lang.reflect.Method;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.ARBTextureBarrier;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.NVTextureBarrier;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = RimLines.MOD_ID, value = Dist.CLIENT)
public final class RimRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static EffectInstance effect;
    private static RenderTarget work;
    private static RenderTarget terrainDepth;
    private static RenderTarget entityDepth;
    private static VertexBuffer quad;
    private static int width = -1;
    private static int height = -1;
    private static boolean terrainDepthReady;
    private static boolean entityDepthReady;

    private static int inPlaceFbo = -1;
    private static int inPlaceColor = -1;
    private static boolean inPlaceComplete;
    private static int barrierMode = -1;

    private static boolean irisChecked;
    private static Object irisApi;
    private static Method shaderPackInUse;

    private RimRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            captureTerrainDepth();
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            captureEntityDepth();
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            if (entityDepthReady) {
                draw(Minecraft.getInstance().getMainRenderTarget(), false);
            }
            terrainDepthReady = false;
            entityDepthReady = false;
        }
    }

    private static void captureTerrainDepth() {
        terrainDepthReady = false;
        entityDepthReady = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (!active(minecraft)) {
            return;
        }
        RenderTarget main = minecraft.getMainRenderTarget();
        if (!ensure(main)) {
            return;
        }
        copyDepth(main, terrainDepth);
        terrainDepthReady = true;
    }

    private static void captureEntityDepth() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (!terrainDepthReady || main.width != width || main.height != height) {
            return;
        }
        copyDepth(main, entityDepth);
        entityDepthReady = true;
    }

    public static void renderHand() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active(minecraft) || minecraft.options.hideGui || !minecraft.options.getCameraType().isFirstPerson()
                || minecraft.getCameraEntity() instanceof LivingEntity living && living.isSleeping()
                || minecraft.gameMode == null || minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return;
        }
        RenderTarget main = minecraft.getMainRenderTarget();
        if (ensure(main)) {
            draw(main, true);
        }
    }

    public static void targetRebuilt() {
        inPlaceColor = -1;
    }

    private static boolean active(Minecraft minecraft) {
        return minecraft.level != null && RimLinesConfig.ENABLED.get()
                && minecraft.options.graphicsMode().get() != GraphicsStatus.FAST && !shaderPackActive();
    }

    private static void copyDepth(RenderTarget from, RenderTarget to) {
        to.copyDepthFrom(from);
        from.bindWrite(false);
    }

    private static void draw(RenderTarget main, boolean hand) {
        float far = Math.max(Minecraft.getInstance().gameRenderer.getDepthFar(), 16.0F);
        effect.safeGetUniform("RimData").set(hand ? 1.0F : 0.0F, RimLinesConfig.THICKNESS.get().floatValue(),
                RimLinesConfig.STRENGTH.get().floatValue(), RimLinesConfig.DARKNESS_CUTOFF.get().floatValue());
        effect.safeGetUniform("RimColor").set(RimLinesConfig.COLOR_RED.get().floatValue(),
                RimLinesConfig.COLOR_GREEN.get().floatValue(), RimLinesConfig.COLOR_BLUE.get().floatValue(),
                RimLinesConfig.SCENE_INFLUENCE.get().floatValue());
        effect.safeGetUniform("DepthData").set(RimLinesConfig.DEPTH_THRESHOLD.get().floatValue(),
                RimLinesConfig.MAX_DISTANCE.get().floatValue(), 0.05F, far);
        if (!drawInPlace(main)) {
            drawViaWork(main);
        }
    }

    private static boolean drawInPlace(RenderTarget main) {
        if (main.filterMode != GL11.GL_NEAREST || barrierMode() == 0) {
            return false;
        }
        int color = main.getColorTextureId();
        if (inPlaceFbo < 0) {
            inPlaceFbo = GlStateManager.glGenFramebuffers();
        }
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, inPlaceFbo);
        if (inPlaceColor != color) {
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
            inPlaceColor = color;
            inPlaceComplete = GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        }
        if (!inPlaceComplete) {
            main.bindWrite(true);
            return false;
        }
        GlStateManager._viewport(0, 0, width, height);
        textureBarrier();
        effect.safeGetUniform("InPlace").set(1.0F);
        drawQuad();
        main.bindWrite(true);
        return true;
    }

    private static void drawViaWork(RenderTarget main) {
        work.bindWrite(true);
        effect.safeGetUniform("InPlace").set(0.0F);
        drawQuad();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, work.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(true);
    }

    private static void drawQuad() {
        RenderSystem.disableBlend();
        effect.apply();
        quad.bind();
        quad.draw();
        VertexBuffer.unbind();
        effect.clear();
    }

    private static int barrierMode() {
        if (barrierMode < 0) {
            GLCapabilities caps = GL.getCapabilities();
            barrierMode = caps.OpenGL45 ? 1 : caps.GL_ARB_texture_barrier ? 2 : caps.GL_NV_texture_barrier ? 3 : 0;
        }
        return barrierMode;
    }

    private static void textureBarrier() {
        switch (barrierMode) {
            case 1 -> GL45C.glTextureBarrier();
            case 2 -> ARBTextureBarrier.glTextureBarrier();
            case 3 -> NVTextureBarrier.glTextureBarrierNV();
            default -> {
            }
        }
    }

    private static boolean ensure(RenderTarget main) {
        if (main.width <= 0 || main.height <= 0) {
            return false;
        }
        if (width == main.width && height == main.height) {
            return effect != null;
        }
        close();
        width = main.width;
        height = main.height;
        try {
            build(main);
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Could not create the rim lines pass", e);
            close();
            return false;
        }
    }

    private static void build(RenderTarget main) throws IOException {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.vertex(0.0D, 0.0D, 500.0D).endVertex();
        builder.vertex(main.width, 0.0D, 500.0D).endVertex();
        builder.vertex(main.width, main.height, 500.0D).endVertex();
        builder.vertex(0.0D, main.height, 500.0D).endVertex();
        quad = new VertexBuffer(VertexBuffer.Usage.STATIC);
        quad.bind();
        quad.upload(builder.end());
        VertexBuffer.unbind();

        RenderTarget terrain = target(main, true);
        RenderTarget entity = target(main, true);
        terrainDepth = terrain;
        entityDepth = entity;
        work = target(main, false);
        effect = new EffectInstance(Minecraft.getInstance().getResourceManager(), RimLines.MOD_ID + ":rim");
        effect.setSampler("DiffuseSampler", main::getColorTextureId);
        effect.setSampler("DepthSampler", main::getDepthTextureId);
        effect.setSampler("TerrainDepthSampler", terrain::getDepthTextureId);
        effect.setSampler("EntityDepthSampler", entity::getDepthTextureId);
        effect.safeGetUniform("ProjMat").set(new Matrix4f().setOrtho(0.0F, main.width, 0.0F, main.height, 0.1F, 1000.0F));
        effect.safeGetUniform("OutSize").set((float) main.width, (float) main.height);
    }

    private static RenderTarget target(RenderTarget main, boolean depth) {
        TextureTarget target = new TextureTarget(main.width, main.height, depth, Minecraft.ON_OSX);
        if (depth && main.isStencilEnabled()) {
            target.enableStencil();
        }
        return target;
    }

    private static void close() {
        if (effect != null) {
            effect.close();
        }
        for (RenderTarget target : new RenderTarget[]{work, terrainDepth, entityDepth}) {
            if (target != null) {
                target.destroyBuffers();
            }
        }
        if (quad != null) {
            quad.close();
        }
        effect = null;
        work = null;
        terrainDepth = null;
        entityDepth = null;
        quad = null;
        terrainDepthReady = false;
        entityDepthReady = false;
    }

    private static boolean shaderPackActive() {
        if (!irisChecked) {
            irisChecked = true;
            if (ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris")) {
                try {
                    Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    irisApi = type.getMethod("getInstance").invoke(null);
                    shaderPackInUse = type.getMethod("isShaderPackInUse");
                } catch (ReflectiveOperationException | RuntimeException e) {
                    shaderPackInUse = null;
                }
            }
        }
        if (shaderPackInUse == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(shaderPackInUse.invoke(irisApi));
        } catch (ReflectiveOperationException | RuntimeException e) {
            shaderPackInUse = null;
            return false;
        }
    }
}
