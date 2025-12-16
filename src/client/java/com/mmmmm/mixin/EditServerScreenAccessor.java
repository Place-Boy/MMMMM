package com.mmmmm.mixin;

import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// TODO(Ravel): can not resolve target class AddServerScreen
// TODO(Ravel): can not resolve target class AddServerScreen
@Mixin(ManageServerScreen.class)
public interface EditServerScreenAccessor {
    // TODO(Ravel): Could not determine a single target
// TODO(Ravel): Could not determine a single target
    @Accessor("server")
    ServerData getServerData();
}