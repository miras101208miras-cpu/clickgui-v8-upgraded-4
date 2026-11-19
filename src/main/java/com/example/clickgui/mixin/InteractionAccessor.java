package com.example.clickgui.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Доступ к позиции и точному float-прогрессу ломания блока для Break Progress Overlay. */
@Mixin(ClientPlayerInteractionManager.class)
public interface InteractionAccessor {
    @Accessor("currentBreakingPos")
    BlockPos clickgui$getBreakingPos();

    @Accessor("currentBreakingProgress")
    float clickgui$getBreakingProgress();
}
