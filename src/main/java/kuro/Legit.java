package kuro;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.network.ClientPlayerEntity;

/** "Legit" yardimcilari: rastgele gecikme, hata payi ve gercek (gorunen) nisan alma. */
public final class Legit {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   private Legit() {
   }

   /** CPVP gucu: 0 = legit (ayarlar oldugu gibi), 1 = guclu (gecikmeler yarim, nisan 2x hizli, hata yok), 2 = maksimum. */
   public static int power() {
      return Math.max(0, Math.min(2, Math.round(Config.cpPower)));
   }

   /** Gecikme (tick) degerini CPVP gucune gore kisaltir. */
   public static int scale(int ticks) {
      return switch (power()) {
         case 0 -> ticks;
         case 1 -> ticks / 2;
         default -> 0;
      };
   }

   /** Guce bagli OLMAYAN rastgele tick (CPVP disi moduller icin). */
   public static int randRaw(float a, float b) {
      int lo = Math.round(Math.min(a, b));
      int hi = Math.round(Math.max(a, b));
      return hi > lo ? lo + ThreadLocalRandom.current().nextInt(hi - lo + 1) : lo;
   }

   /** [a, b] araliginda rastgele tick (CPVP gucune gore kisalir). */
   public static int rand(float a, float b) {
      return scale(randRaw(a, b));
   }

   /** Makrolar icin: taban gecikme + 0..macroJitter rastgele tick. */
   public static int delay(float base) {
      int j = Math.round(Config.macroJitter);
      return scale(Math.max(0, Math.round(base) + (j > 0 ? ThreadLocalRandom.current().nextInt(j + 1) : 0)));
   }

   /** Yuzde olasilikla "kacir" (insan hatasi); guclu modlarda hic kacirmaz. */
   public static boolean miss(float percent) {
      return power() == 0 && percent > 0.0F && ThreadLocalRandom.current().nextFloat() * 100.0F < percent;
   }

   /** Guce bagli olmayan "kacirma" (insan hatasi). */
   public static boolean missRaw(float percent) {
      return percent > 0.0F && ThreadLocalRandom.current().nextFloat() * 100.0F < percent;
   }

   /** Vanilla fare adimi: oyun yaw/pitch'i hassasiyete bagli en kucuk adimin (GCD) katlari olarak degistirir. */
   public static float gcdStep() {
      double f = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
      return (float)(f * f * f * 8.0 * 0.15);
   }

   /** Dönüş farkını fare adımının katına yuvarlar (gerçek fare hareketi gibi). */
   public static float quant(float delta) {
      float step = gcdStep();
      return step <= 0.0F ? delta : Math.round(delta / step) * step;
   }

   /** -amp..+amp arasi rastgele sapma. */
   public static float noise(float amp) {
      return (ThreadLocalRandom.current().nextFloat() - 0.5F) * 2.0F * amp;
   }

   /** Kamerayi tek tick'te hedef noktaya cevirir (cok kucuk rastgele sapmayla). */
   public static void snap(Vec3d pt) {
      ClientPlayerEntity p = mc.player;
      if (p == null) {
         return;
      }

      if (SilentRot.enabled()) {
         SilentRot.face(pt);
         return;
      }

      Vec3d eye = p.getEyePos();
      double dx = pt.x - eye.x;
      double dy = pt.y - eye.y;
      double dz = pt.z - eye.z;
      float yaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F + noise(0.15F);
      float pitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)))) + noise(0.1F);
      float cy = p.getYaw();
      float cp = p.getPitch();
      p.setYaw(cy + quant(MathHelper.wrapDegrees(yaw - cy)));
      p.setPitch(MathHelper.clamp(cp + quant(pitch - cp), -90.0F, 90.0F));
   }

   /**
    * Kamerayi (gercek yaw/pitch) hedef noktaya dogru yumusakca cevirir.
    * speed: tick basina en fazla derece. noiseAmp: hareket sirasinda rastgele sapma (derece).
    */
   public static void aim(Vec3d pt, float speed, float noiseAmp) {
      aim(pt, speed, noiseAmp, power());
   }

   /** CPVP gucunden bagimsiz, her zaman insan hizinda nisan (BaseAura kullanir). */
   public static void aimHuman(Vec3d pt, float speed, float noiseAmp) {
      aim(pt, speed, noiseAmp, 0);
   }

   private static void aim(Vec3d pt, float speed, float noiseAmp, int pw) {
      ClientPlayerEntity p = mc.player;
      if (p == null || speed <= 0.0F) {
         return;
      }

      if (SilentRot.enabled()) {
         // SilentRotate: kamera donmez, sunucuya hedefe bakan aci gider
         SilentRot.face(pt);
         return;
      }

      if (pw >= 1) {
         speed = Math.min(90.0F, speed * (pw == 1 ? 2.0F : 4.0F));
         noiseAmp = pw == 1 ? noiseAmp * 0.4F : 0.0F;
      }

      Vec3d eye = p.getEyePos();
      double dx = pt.x - eye.x;
      double dy = pt.y - eye.y;
      double dz = pt.z - eye.z;
      float wantYaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
      float wantPitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
      float dyaw = MathHelper.wrapDegrees(wantYaw - p.getYaw());
      float dpitch = wantPitch - p.getPitch();
      float dist = (float)Math.hypot(dyaw, dpitch);
      if (dist < 0.05F) {
         return;
      }

      // uzakken hizli, yaklasirken yavasla (insan eli gibi)
      float k = pw >= 2 ? 1.0F : (pw == 1 ? 0.75F : 0.5F);
      float sy = MathHelper.clamp(dyaw * k, -speed, speed);
      float sp = MathHelper.clamp(dpitch * k, -speed, speed);
      if (dist > 1.5F && noiseAmp > 0.0F) {
         sy += noise(noiseAmp);
         sp += noise(noiseAmp * 0.6F);
      }

      sy = quant(sy);
      sp = quant(sp);
      if (sy == 0.0F && sp == 0.0F) {
         return;
      }

      p.setYaw(p.getYaw() + sy);
      p.setPitch(MathHelper.clamp(p.getPitch() + sp, -90.0F, 90.0F));
   }
}
