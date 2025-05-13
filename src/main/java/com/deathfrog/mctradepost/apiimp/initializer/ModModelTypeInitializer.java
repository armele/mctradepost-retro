package com.deathfrog.mctradepost.apiimp.initializer;

import com.deathfrog.mctradepost.api.client.render.modeltype.ModModelTypes;
import com.minecolonies.api.client.render.modeltype.registry.IModelTypeRegistry;
import com.minecolonies.api.client.render.modeltype.SimpleModelType;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.deathfrog.mctradepost.core.client.model.FemaleShopkeeperModel;
import com.deathfrog.mctradepost.core.client.model.MaleShopkeeperModel;
import com.deathfrog.mctradepost.core.event.ClientRegistryHandler;
public class ModModelTypeInitializer
{
    private ModModelTypeInitializer()
    {
        throw new IllegalStateException("Tried to initialize: MCTradePost.ModModelTypeInitializer but this is a Utility class.");
    }

    /* TODO:  Evaluate TextureReloadListener as a solution to below issue
[12May2025 09:41:30.502] [Render thread/WARN] [net.minecraft.client.renderer.texture.TextureManager/]: Failed to load texture: minecolonies:textures/entity/citizen/default/shopkeepermale1_d.png
java.io.FileNotFoundException: minecolonies:textures/entity/citizen/default/shopkeepermale1_d.png
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.server.packs.resources.ResourceProvider.lambda$getResourceOrThrow$1(ResourceProvider.java:18) ~[neoforge-21.1.89.jar%23193!/:?]
	at java.base/java.util.Optional.orElseThrow(Optional.java:403) ~[?:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.server.packs.resources.ResourceProvider.getResourceOrThrow(ResourceProvider.java:18) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.renderer.texture.SimpleTexture$TextureImage.load(SimpleTexture.java:83) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.renderer.texture.SimpleTexture.getTextureImage(SimpleTexture.java:57) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.renderer.texture.SimpleTexture.load(SimpleTexture.java:30) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.renderer.texture.TextureManager.loadTexture(TextureManager.java:92) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.renderer.texture.TextureManager.register(TextureManager.java:63) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.renderer.texture.TextureManager.getTexture(TextureManager.java:113) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.renderer.RenderStateShard$TextureStateShard.lambda$new$0(RenderStateShard.java:620) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.renderer.RenderStateShard.setupRenderState(RenderStateShard.java:365) ~[neoforge-21.1.89.jar%23193!/:?]
	at MC-BOOTSTRAP/com.google.common@32.1.2-jre/com.google.common.collect.ImmutableList.forEach(ImmutableList.java:422) ~[guava-32.1.2-jre.jar%23111!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.renderer.RenderType$CompositeRenderType.lambda$new$1(RenderType.java:1227) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.renderer.RenderStateShard.setupRenderState(RenderStateShard.java:365) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.renderer.RenderType.draw(RenderType.java:1144) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.renderer.MultiBufferSource$BufferSource.endBatch(MultiBufferSource.java:99) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.renderer.MultiBufferSource$BufferSource.endBatch(MultiBufferSource.java:87) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.renderer.MultiBufferSource$BufferSource.getBuffer(MultiBufferSource.java:57) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.gui.Font$StringRenderOutput.accept(Font.java:432) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.util.StringDecomposer.feedChar(StringDecomposer.java:13) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.util.StringDecomposer.iterate(StringDecomposer.java:39) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.util.FormattedCharSequence.lambda$forward$2(FormattedCharSequence.java:19) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.gui.Font.renderText(Font.java:264) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.gui.Font.drawInternal(Font.java:226) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.gui.Font.drawInBatch(Font.java:131) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.gui.Font.drawInBatch(Font.java:114) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.renderer.entity.EntityRenderer.renderNameTag(EntityRenderer.java:204) ~[neoforge-21.1.89.jar%23193!/:?]
	at TRANSFORMER/minecolonies@1.1.950-1.21.1-snapshot/com.minecolonies.core.client.render.RenderBipedCitizen.renderNameTag(RenderBipedCitizen.java:111) ~[minecolonies-1.1.950-1.21.1-snapshot.jar%23197!/:1.1.950-1.21.1-snapshot]
	at TRANSFORMER/minecolonies@1.1.950-1.21.1-snapshot/com.minecolonies.core.client.render.RenderBipedCitizen.renderNameTag(RenderBipedCitizen.java:30) ~[minecolonies-1.1.950-1.21.1-snapshot.jar%23197!/:1.1.950-1.21.1-snapshot]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.renderer.entity.EntityRenderer.render(EntityRenderer.java:101) ~[neoforge-21.1.89.jar%23193!/:?]
     * 
     */


    public static void init(final EntityRendererProvider.Context context)
    {
        final IModelTypeRegistry reg = IModelTypeRegistry.getInstance();

        ModModelTypes.SHOPKEEPER = new SimpleModelType(ModModelTypes.SHOPKEEPER_ID, 1, new MaleShopkeeperModel(context.bakeLayer(ClientRegistryHandler.MALE_SHOPKEEPER)), 
            new FemaleShopkeeperModel(context.bakeLayer(ClientRegistryHandler.FEMALE_SHOPKEEPER)));
        reg.register(ModModelTypes.SHOPKEEPER);

    }
}
