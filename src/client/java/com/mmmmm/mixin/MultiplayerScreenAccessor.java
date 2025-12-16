package com.mmmmm.mixin;

import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.option.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// TODO(Ravel): can not resolve target class MultiplayerScreen
// TODO(Ravel): can not resolve target class MultiplayerScreen
@Mixin(MultiplayerScreen.class)
public interface MultiplayerScreenAccessor {
    // TODO(Ravel): Could not determine a single target
// TODO(Ravel): Could not determine a single target
    @Accessor("serverList")
    ServerList getServerList();
}