package kuro.mixin;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
   // SilentRotate: crosshair hedefini sessiz aciya gore yeniden hesaplatmak icin
   @Invoker("updateCrosshairTarget")
   void kuro$updateCrosshairTarget(float tickDelta);
}
