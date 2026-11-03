package kuro.mixin;

import kuro.Xray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sodium sivi cizimi: XRay acikken secili olmayan sivilar (su/lav) cizilmez. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer", remap = false)
public abstract class SodiumXrayFluidMixin {
   @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
   private void kuro$xrayFluid(@org.spongepowered.asm.mixin.injection.Coerce Object level, net.minecraft.block.BlockState blockState,
                               net.minecraft.fluid.FluidState fluidState, net.minecraft.util.math.BlockPos pos,
                               net.minecraft.util.math.BlockPos offset, @org.spongepowered.asm.mixin.injection.Coerce Object collector,
                               @org.spongepowered.asm.mixin.injection.Coerce Object builder, CallbackInfo ci) {
      if (Xray.active) {
         Xray.hooked = true;
         if (!Xray.visible(blockState)) {
            ci.cancel();
         }
      }
   }
}
