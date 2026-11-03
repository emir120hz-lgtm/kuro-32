package kuro.mixin;

import kuro.FakePlayer;
import kuro.Kuro;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
   // CrystalOptimizer: kristale vurur vurmaz istemcide kaldır; sunucuyu beklemeden hemen yenisini koyabilirsin.
   @Inject(method = "attackEntity", at = @At("TAIL"))
   private void kuro$crystalOptimizer(PlayerEntity player, Entity target, CallbackInfo ci) {
      if (!Kuro.on[Kuro.CRYSTALOPT] || !(target instanceof EndCrystalEntity) || player != MinecraftClient.getInstance().player) {
         return;
      }

      target.setRemoved(Entity.RemovalReason.KILLED);
      target.onRemoved();
   }

   // BaseAura: oyuncuya vurunca kristal komboyu tetikle
   @Inject(method = "attackEntity", at = @At("TAIL"))
   private void kuro$hitTrigger(PlayerEntity player, Entity target, CallbackInfo ci) {
      if (Kuro.on[Kuro.CRYSTALBASE] && player == MinecraftClient.getInstance().player) {
         kuro.BaseAura.onHit(target);
      }
   }

   // TargetESP / HitParticles
   @Inject(method = "attackEntity", at = @At("HEAD"), require = 0)
   private void kuro$fx(PlayerEntity player, Entity target, CallbackInfo ci) {
      if (player == MinecraftClient.getInstance().player && (Kuro.on[Kuro.TARGETESP] || Kuro.on[Kuro.HITPARTICLES])) {
         kuro.Fx.onAttack(target);
      }
   }

   // WTap: vurustan hemen once sprint sifirla.
   @Inject(method = "attackEntity", at = @At("HEAD"), require = 0)
   private void kuro$wtap(PlayerEntity player, Entity target, CallbackInfo ci) {
      if (Kuro.on[Kuro.WTAP] && player == MinecraftClient.getInstance().player && target instanceof net.minecraft.entity.LivingEntity && !(target instanceof EndCrystalEntity)) {
         kuro.Cpvp.wtap();
      }
   }

   // Criticals: yerdeyken vurusta sahte zipla paketleri (onGround=false) yolla.
   @Inject(method = "attackEntity", at = @At("HEAD"), require = 0)
   private void kuro$crit(PlayerEntity player, Entity target, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (!Kuro.on[Kuro.CRITICALS] || player != mc.player || mc.getNetworkHandler() == null
         || !(target instanceof net.minecraft.entity.LivingEntity) || target instanceof EndCrystalEntity
         || !player.isOnGround() || player.isTouchingWater() || player.isInLava() || player.isClimbing()) {
         return;
      }

      double x = player.getX();
      double y = player.getY();
      double z = player.getZ();
      boolean hc = player.horizontalCollision;
      mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false, hc));
      mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, hc));
   }

   // FakePlayer: sahte oyuncuya vurus sunucuya gitmesin, hasar istemcide hesaplansin.
   @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
   private void kuro$fakeHit(PlayerEntity player, Entity target, CallbackInfo ci) {
      if (FakePlayer.melee(player, target)) {
         ci.cancel();
      }
   }
}
