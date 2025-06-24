package com.mmmmm.mixin;

import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EditServerScreen.class)
public interface EditServerScreenAccessor {
    // Make sure "serverData" is the correct Mojang field name
    @Accessor("serverData")
    ServerData getServerData();
}