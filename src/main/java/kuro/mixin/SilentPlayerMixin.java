package kuro.mixin;

import kuro.SilentRot;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class SilentPlayerMixin {
   // SilentRotate: hareket paketi gonderilirken sunucuya giden aci, kamera acisi yerine sessiz aci olur.
   @Inject(method = "sendMovementPackets", at = @At("HEAD"), require = 0)
   private void kuro$silentBegin(CallbackInfo ci) {
      SilentRot.begin((ClientPlayerEntity)(Object)this);
   }

   @Inject(method = "sendMovementPackets", at = @At("TAIL"), require = 0)
   private void kuro$silentEnd(CallbackInfo ci) {
      SilentRot.end((ClientPlayerEntity)(Object)this);
   }
}
