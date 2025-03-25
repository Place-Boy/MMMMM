package com.example.examplemod;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
@EventBusSubscriber(modid = ExampleMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.ConfigValue<String> PACK_URL = BUILDER
            .comment("URL to locate the modpack for the client to download")
            .define("packUrl", "replace");

    private static final ModConfigSpec.ConfigValue<Boolean> HTTP = BUILDER
            .comment("Use HTTP request. If disabled will scan folders on server instead (less stable)")
            .define("useHTTP", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static String packURL;
    public static boolean useHTTP;

    private static boolean validateItemName(final Object obj)
    {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        packURL = PACK_URL.get();
        useHTTP = HTTP.get();
    }
}
