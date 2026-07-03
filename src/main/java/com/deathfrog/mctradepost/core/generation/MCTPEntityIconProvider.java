package com.deathfrog.mctradepost.core.generation;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.google.common.hash.HashCode;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@EventBusSubscriber(modid = MCTradePostMod.MODID, value = Dist.CLIENT)
public class MCTPEntityIconProvider implements DataProvider
{
    private static final String OUTPUT_NAMESPACE = "minecolonies";
    private static final String OUTPUT_FOLDER = "textures/entity_icon/citizen/default";
    private static final Path SOURCE_FOLDER = projectRoot().resolve(
        "src/main/resources/assets/mctradepost/textures/entity/citizen/default");

    private final PackOutput.PathProvider outputProvider;
    private final Path sourceFolder;

    private static Path projectRoot()
    {
        return Path.of(System.getProperty("mctradepost.projectDir", ".")).toAbsolutePath().normalize();
    }

    public MCTPEntityIconProvider(final PackOutput packOutput, final Path sourceFolder)
    {
        this.outputProvider = packOutput.createPathProvider(PackOutput.Target.RESOURCE_PACK, OUTPUT_FOLDER);
        this.sourceFolder = sourceFolder;
    }

    @SubscribeEvent
    public static void gatherData(final GatherDataEvent event)
    {
        final DataGenerator generator = event.getGenerator();
        generator.addProvider(event.includeClient(),
            new MCTPEntityIconProvider(generator.getPackOutput(), SOURCE_FOLDER));
    }

    @Override
    public CompletableFuture<?> run(@Nonnull final CachedOutput cache)
    {
        if (!Files.isDirectory(sourceFolder))
        {
            MCTradePostMod.LOGGER.warn("Skipping MCTP citizen icon generation; missing {}", sourceFolder);
            return CompletableFuture.completedFuture(null);
        }

        try (Stream<Path> files = Files.list(sourceFolder))
        {
            return CompletableFuture.allOf(files
                .filter(path -> path.getFileName().toString().endsWith(".png"))
                .map(path -> generateIcon(path, cache))
                .toArray(CompletableFuture[]::new));
        }
        catch (final IOException e)
        {
            MCTradePostMod.LOGGER.error("Failed to list MCTP citizen skin directory {}", sourceFolder, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private static HashCode sha1(final byte[] bytes)
    {
        try
        {
            byte[] mess = MessageDigest.getInstance("SHA-1").digest(bytes);

            if (mess == null) throw new IllegalStateException("Null byte list from message disgest.");

            return HashCode.fromBytes(mess);
        }
        catch (final NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-1 digest is unavailable", e);
        }
    }

    private CompletableFuture<?> generateIcon(final Path source, final CachedOutput cache)
    {
        return CompletableFuture.runAsync(() ->
        {
            final String fileName = source.getFileName().toString();
            final String idPath = fileName.substring(0, fileName.length() - ".png".length());

            if (idPath == null) throw new IllegalArgumentException("Bad idPath from file name: " + fileName);

            final ResourceLocation outputId = ResourceLocation.fromNamespaceAndPath(OUTPUT_NAMESPACE, idPath);

            if (outputId == null) throw new IllegalArgumentException("Null output ID for output namespace " + OUTPUT_NAMESPACE + ":" + idPath);

            try (@SuppressWarnings("null")
            NativeImage skin = NativeImage.read(Files.newInputStream(source));
                 NativeImage icon = createIconForSkin(skin))
            {
                saveIcon(outputId, icon, cache);
            }
            catch (final IOException e)
            {
                MCTradePostMod.LOGGER.error("Failed to generate citizen map icon for {}", source, e);
            }
        }, Util.backgroundExecutor());
    }

    private static NativeImage createIconForSkin(final NativeImage skin)
    {
        final NativeImage icon = new NativeImage(16, 16, false);

        icon.resizeSubRectTo(0, 0, 16, 16, icon);
        skin.resizeSubRectTo(8, 8, 8, 8, icon);

        for (int i = 0; i < 16; ++i)
        {
            icon.blendPixel(0, i, 0x80000000);
            icon.blendPixel(15, i, 0x80000000);

            if (i > 0 && i < 15)
            {
                icon.blendPixel(i, 0, 0x80000000);
                icon.blendPixel(i, 15, 0x80000000);
            }
        }

        return icon;
    }

    @SuppressWarnings("null")
    private void saveIcon(final @Nonnull ResourceLocation id, final NativeImage icon, final CachedOutput cache) throws IOException
    {
        final BufferedImage image;
        try (ByteArrayInputStream stream = new ByteArrayInputStream(icon.asByteArray()))
        {
            image = ImageIO.read(stream);
        }

        final BufferedImage optimized = new BufferedImage(
            image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        optimized.getGraphics().drawImage(image, 0, 0, null);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(optimized, "PNG", outputStream);

        final byte[] bytes = outputStream.toByteArray();

        if (bytes == null) throw new RuntimeException("Null byte stream attempting to save resource icon.");

        cache.writeIfNeeded(outputProvider.file(id, "png"), bytes, sha1(bytes));
    }

    @NotNull
    @Override
    public String getName()
    {
        return "MCTP MineColonies Citizen Entity Icons";
    }
}
