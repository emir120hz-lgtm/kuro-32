package kuro.mixin;

import kuro.Xray;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * XRay: shouldDrawSide sadece tam kup yuzeylerini kontrol eder. Cimen, cicek, fidan, su gibi
 * "capraz model" ve sivi bloklar o yoldan gecmez; bu yuzden XRay acikken de gorunuyorlardi.
 * Burada secili olmayan her blok / sivi tamamen cizim disi birakilir.
 */
@Mixin(BlockRenderManager.class)
public abstract class BlockRenderManagerMixin {
   @Inject(
      method = "renderBlock(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;)V",
      at = @At("HEAD"), cancellable = true, require = 0
   )
   private void kuro$xrayBlockState(BlockState state, net.minecraft.util.math.BlockPos pos, net.minecraft.world.BlockRenderView world,
                                    net.minecraft.client.util.math.MatrixStack matrices, net.minecraft.client.render.VertexConsumer vc,
                                    boolean cull, net.minecraft.util.math.random.Random random, CallbackInfo ci) {
      if (Xray.active) {
         Xray.hooked = true;
         if (!Xray.visible(state)) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = "renderFluid(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/block/BlockState;Lnet/minecraft/fluid/FluidState;)V",
      at = @At("HEAD"), cancellable = true, require = 0
   )
   private void kuro$xrayFluid(net.minecraft.util.math.BlockPos pos, net.minecraft.world.BlockRenderView world,
                               net.minecraft.client.render.VertexConsumer vc, BlockState state,
                               net.minecraft.fluid.FluidState fluid, CallbackInfo ci) {
      if (Xray.active) {
         Xray.hooked = true;
         if (!Xray.visible(state)) {
            ci.cancel();
         }
      }
   }
}
