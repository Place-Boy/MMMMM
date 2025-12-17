package com.mmmmm.mixin;

import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// TODO(Ravel): can not resolve target class MultiplayerScreen
// TODO(Ravel): can not resolve target class MultiplayerScreen
@Mixin(JoinMultiplayerScreen.class)
public interface MultiplayerScreenAccessor {
    @Accessor("servers")
    ServerList getServerList();
}