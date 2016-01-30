package buildcraft.transport.pluggable;

import java.util.Collections;
import java.util.List;

import javax.vecmath.Matrix4f;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.model.ModelRotation;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IFlexibleBakedModel;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import buildcraft.api.transport.IPipe;
import buildcraft.api.transport.pluggable.IPipePluggableState;
import buildcraft.api.transport.pluggable.IPipePluggableStaticRenderer;
import buildcraft.api.transport.pluggable.IPipeRenderState;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.core.lib.render.BakedModelHolder;
import buildcraft.core.lib.render.PerspAwareModelBase;
import buildcraft.core.lib.utils.ColorUtils;
import buildcraft.core.lib.utils.MatrixUtils;

public final class LensPluggableModel extends BakedModelHolder implements IPipePluggableStaticRenderer.Translucent {
    public static final LensPluggableModel INSTANCE = new LensPluggableModel();

    private static final ResourceLocation cutoutLensLoc = new ResourceLocation("buildcrafttransport:models/blocks/pluggables/lens_cutout.obj");
    private static final ResourceLocation cutoutFilterLoc = new ResourceLocation("buildcrafttransport:models/blocks/pluggables/filter_cutout.obj");
    private static final ResourceLocation translucentLoc = new ResourceLocation("buildcrafttransport:models/blocks/pluggables/lens_translucent.obj");

    private static final ResourceLocation cutoutLensSpriteLoc = new ResourceLocation("buildcrafttransport:pipes/lens");
    private static final ResourceLocation cutoutFilterSpriteLoc = new ResourceLocation("buildcrafttransport:pipes/filter");
    private static final ResourceLocation translucentSpriteLoc = new ResourceLocation("buildcrafttransport:pipes/overlay_lens");
    private static TextureAtlasSprite spriteLensCutout, spriteFilterCutout, spriteTranslucent;

    private LensPluggableModel() {}

    public static PerspAwareModelBase create(ItemLens lensItem, int meta) {
        LensPluggable lens = new LensPluggable(new ItemStack(lensItem, 1, meta));
        ImmutableList.Builder<BakedQuad> quads = ImmutableList.builder();
        VertexFormat format = DefaultVertexFormats.ITEM;
        quads.addAll(INSTANCE.bakeCutout(lens, EnumFacing.EAST, format));
        quads.addAll(INSTANCE.bakeTransclucent(lens, EnumFacing.EAST, format));
        return new PerspAwareModelBase(format, quads.build(), spriteLensCutout, getBlockTransforms());
    }

    public IModel modelCutoutLens() {
        return getModelOBJ(cutoutLensLoc);
    }

    public IModel modelCutoutFilter() {
        return getModelOBJ(cutoutFilterLoc);
    }

    public IModel modelTranslucent() {
        return getModelOBJ(translucentLoc);
    }

    @SubscribeEvent
    public void textureStitch(TextureStitchEvent.Pre event) {
        spriteLensCutout = null;
        spriteLensCutout = event.map.getTextureExtry(cutoutLensSpriteLoc.toString());
        if (spriteLensCutout == null) spriteLensCutout = event.map.registerSprite(cutoutLensSpriteLoc);

        spriteFilterCutout = null;
        spriteFilterCutout = event.map.getTextureExtry(cutoutFilterSpriteLoc.toString());
        if (spriteFilterCutout == null) spriteFilterCutout = event.map.registerSprite(cutoutFilterSpriteLoc);

        spriteTranslucent = null;
        spriteTranslucent = event.map.getTextureExtry(translucentSpriteLoc.toString());
        if (spriteTranslucent == null) spriteLensCutout = event.map.registerSprite(translucentSpriteLoc);
    }

    @Override
    public List<BakedQuad> bakeCutout(IPipeRenderState render, IPipePluggableState pluggableState, IPipe pipe, PipePluggable pluggable,
            EnumFacing face) {
        LensPluggable lens = (LensPluggable) pluggable;

        return bakeCutout(lens, face, DefaultVertexFormats.BLOCK);
    }

    @Override
    public List<BakedQuad> bakeTranslucent(IPipeRenderState render, IPipePluggableState pluggableState, IPipe pipe, PipePluggable pluggable,
            EnumFacing face) {
        LensPluggable lens = (LensPluggable) pluggable;

        return bakeTransclucent(lens, face, DefaultVertexFormats.BLOCK);
    }

    private List<BakedQuad> bakeCutout(LensPluggable lens, EnumFacing face, VertexFormat format) {
        IModel model = lens.isFilter ? modelCutoutFilter() : modelCutoutLens();
        TextureAtlasSprite sprite = lens.isFilter ? spriteFilterCutout : spriteLensCutout;

        List<BakedQuad> quads = Lists.newArrayList();
        List<BakedQuad> bakedQuads = renderLens(model, sprite, format);
        Matrix4f matrix = MatrixUtils.rotateTowardsFace(face);
        for (BakedQuad quad : bakedQuads) {
            quad = transform(quad, matrix);
            // quad = applyDiffuse(quad);
            quads.add(quad);
        }

        return quads;
    }

    private List<BakedQuad> bakeTransclucent(LensPluggable lens, EnumFacing face, VertexFormat format) {
        EnumDyeColor colour = lens.getColour();
        if (colour == null) return Collections.emptyList();
        int shade = ColorUtils.getLightHex(colour);
        if (format == DefaultVertexFormats.ITEM) shade = ColorUtils.convertARGBtoABGR(shade);

        List<BakedQuad> quads = Lists.newArrayList();
        List<BakedQuad> bakedQuads = renderLens(modelTranslucent(), spriteTranslucent, format);
        Matrix4f matrix = MatrixUtils.rotateTowardsFace(face);
        for (BakedQuad quad : bakedQuads) {
            quad = transform(quad, matrix);
            // quad = applyDiffuse(quad);
            quad = replaceTint(quad, shade);
            quads.add(quad);
        }

        return quads;
    }

    public static List<BakedQuad> renderLens(IModel model, TextureAtlasSprite sprite, VertexFormat format) {
        List<BakedQuad> quads = Lists.newArrayList();
        IFlexibleBakedModel baked = model.bake(ModelRotation.X0_Y0, format, singleTextureFunction(sprite));
        for (BakedQuad quad : baked.getGeneralQuads()) {
            quad = replaceShade(quad, 0xFFFFFFFF);
            quads.add(quad);
        }
        return quads;
    }
}
