package kuro.mixin;

import kuro.Xray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium kendi blok render yolunu kullanir (BlockRenderManager cagrilmaz). Sodium yokken uygulanmaz.
 * XRay acikken Sodium'un blok modeli cizimi: secili degilse atlanir (state, 2. parametre).
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumXrayMixin {
   @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
   private void kuro$xrayModel(net.minecraft.client.render.model.BakedModel model, net.minecraft.block.BlockState state,
                               net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.BlockPos origin, CallbackInfo ci) {
      if (Xray.active) {
         Xray.hooked = true;
         if (!Xray.visible(state)) {
            ci.cancel();
         }
      }
   }
}
