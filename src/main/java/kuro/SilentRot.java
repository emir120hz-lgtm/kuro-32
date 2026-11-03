package kuro;

import kuro.mixin.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.Entity;

/**
 * SilentRotate: kamera (gercek yaw/pitch) hic donmeden, sunucuya hedefe bakan bir bakis acisi gonderir.
 * Calisma sekli:
 *  1) face(): hedefe giden aciyi hesaplar, hemen bir LookAndOnGround paketi yollar. Boylece o tick'teki
 *     vurus / blok koyma sunucuda bu aciyla degerlendirilir.
 *  2) ClientPlayerEntity.sendMovementPackets sirasinda (mixin) aci birkac tick boyunca gecici olarak
 *     oyuncuya yazilir ve hemen geri alinir; vanilla "eski acia don" paketini araya sokmaz.
 *  3) refreshCrosshair(): mc.crosshairTarget'i sessiz aciya gore yeniden hesaplar; boylece "nisan aldi mi?"
 *     diye crosshair'a bakan modullerin mantigi degismeden calisir.
 */
public final class SilentRot {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   /** Legit.aim bu bayrak aciksa sessiz donmez (AimAssist gibi gorunur kamera modulleri icin). */
   public static boolean noSilent;

   public static boolean active;
   public static float yaw;
   public static float pitch;
   private static int hold;
   private static boolean swapped;
   private static float realYaw;
   private static float realPitch;
   private static boolean inRefresh;

   private SilentRot() {
   }

   public static boolean enabled() {
      return Kuro.on[Kuro.SILENTROT] && !noSilent;
   }

   public static void reset() {
      active = false;
      hold = 0;
      swapped = false;
   }

   /** Hedef noktaya sessizce bakar. Kalan aci hatasini (derece) dondurur; 0 = tam bakiyor. */
   public static float face(Vec3d pt) {
      ClientPlayerEntity p = mc.player;
      if (p == null || mc.getNetworkHandler() == null || pt == null) {
         return 0.0F;
      }

      Vec3d eye = p.getEyePos();
      double dx = pt.x - eye.x;
      double dy = pt.y - eye.y;
      double dz = pt.z - eye.z;
      float wantYaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F + Legit.noise(Config.srNoise);
      float wantPitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)))) + Legit.noise(Config.srNoise * 0.6F);

      float baseYaw = active ? yaw : p.getYaw();
      float basePitch = active ? pitch : p.getPitch();
      float dyaw = MathHelper.wrapDegrees(wantYaw - baseYaw);
      float dpitch = wantPitch - basePitch;
      float speed = Math.max(1.0F, Config.srSpeed);
      float sy = Legit.quant(MathHelper.clamp(dyaw, -speed, speed));
      float sp = Legit.quant(MathHelper.clamp(dpitch, -speed, speed));
      float ny = baseYaw + sy;
      float np = MathHelper.clamp(basePitch + sp, -90.0F, 90.0F);

      boolean changed = !active || Math.abs(MathHelper.wrapDegrees(ny - yaw)) > 0.001F || Math.abs(np - pitch) > 0.001F;
      yaw = ny;
      pitch = np;
      active = true;
      hold = Math.max(1, Math.round(Config.srHold));
      if (changed) {
         mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(ny, np, p.isOnGround(), p.horizontalCollision));
      }

      refreshCrosshair();
      return (float)Math.hypot(MathHelper.wrapDegrees(wantYaw - ny), wantPitch - np);
   }

   /** mc.crosshairTarget'i sessiz aciya gore yeniler (frame render'i bunu sonra yine gercek acidan hesaplar). */
   public static void refreshCrosshair() {
      ClientPlayerEntity p = mc.player;
      if (!active || p == null || inRefresh) {
         return;
      }

      inRefresh = true;
      float ry = p.getYaw();
      float rp = p.getPitch();
      try {
         p.setYaw(yaw);
         p.setPitch(pitch);
         ((GameRendererAccessor)mc.gameRenderer).kuro$updateCrosshairTarget(1.0F);
      } catch (Throwable ignored) {
      } finally {
         p.setYaw(ry);
         p.setPitch(rp);
         inRefresh = false;
      }
   }

   // ------------------------------------------------------------------ mixin kancalari

   /** sendMovementPackets basi: sessiz aciyi gecici olarak oyuncuya yaz. */
   public static void begin(ClientPlayerEntity p) {
      if (!active || p == null) {
         return;
      }

      realYaw = p.getYaw();
      realPitch = p.getPitch();
      p.setYaw(yaw);
      p.setPitch(pitch);
      swapped = true;
   }

   /** sendMovementPackets sonu: gercek acini geri yaz, suresi dolduysa sessiz acidan cik. */
   public static void end(ClientPlayerEntity p) {
      if (swapped && p != null) {
         p.setYaw(realYaw);
         p.setPitch(realPitch);
      }

      boolean wasSwapped = swapped;
      swapped = false;
      if (active && --hold <= 0) {
         active = false;
         // sunucudaki bakis acisini gercek kamera acina geri esitle (vanilla bunu kendiliginden yapmayabilir)
         if (wasSwapped && p != null && mc.getNetworkHandler() != null
            && (Math.abs(MathHelper.wrapDegrees(realYaw - yaw)) > 0.01F || Math.abs(realPitch - pitch) > 0.01F)) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(realYaw, realPitch, p.isOnGround(), p.horizontalCollision));
         }
      }
   }

   /** attackEntity kancasi: modul kameraya bakmadan bir varliga vuruyorsa once ona sessizce bak. */
   public static void onAttack(Entity target) {
      if (!enabled() || target == null || mc.player == null || Freecam.active) {
         return;
      }

      HitResult hr = mc.crosshairTarget;
      if (hr instanceof EntityHitResult ehr && ehr.getEntity() == target) {
         return;
      }

      face(target.getBoundingBox().getCenter());
   }

   /** interactBlock kancasi: modul kameraya bakmadan blok kullaniyorsa once o noktaya sessizce bak. */
   public static void onBlock(BlockHitResult bhr) {
      if (!enabled() || bhr == null || mc.player == null || Freecam.active) {
         return;
      }

      HitResult hr = mc.crosshairTarget;
      if (hr instanceof BlockHitResult cur && cur.getType() == HitResult.Type.BLOCK && cur.getBlockPos().equals(bhr.getBlockPos())) {
         return;
      }

      face(bhr.getPos());
   }
}
