package com.mmmmm.mixin;

import net.minecraft.client.gui.screen.AddServerScreen;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AddServerScreen.class)
public interface EditServerScreenAccessor {
    @Accessor("server")
    ServerInfo getServerData();
}