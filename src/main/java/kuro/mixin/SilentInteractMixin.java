package kuro.mixin;

import kuro.SilentRot;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class SilentInteractMixin {
   // SilentRotate: vurus / blok kullanma paketinden hemen once sunucuya hedefe bakan aciyi gonder.
   @Inject(method = "attackEntity", at = @At("HEAD"), require = 0)
   private void kuro$silentAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
      SilentRot.onAttack(target);
   }

   @Inject(method = "interactBlock", at = @At("HEAD"), require = 0)
   private void kuro$silentBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
      SilentRot.onBlock(hitResult);
   }
}
