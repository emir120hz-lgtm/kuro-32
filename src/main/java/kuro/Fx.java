package kuro;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

/** Gorsel moduller: TargetESP, HitParticles, PearlTrajectory, JumpCircles, LowHealthWarning, CustomCrosshair. */
public final class Fx {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   private Fx() {
   }

   // ------------------------------------------------------------------ vurus takibi
   private static Entity lastTarget;
   private static long lastHitMs;

   /** attackEntity kancasi: hedef halkasi ve vurus parcaciklari. */
   public static void onAttack(Entity target) {
      if (!(target instanceof LivingEntity) || mc.world == null) {
         return;
      }

      lastTarget = target;
      lastHitMs = System.currentTimeMillis();
      if (Kuro.on[Kuro.HITPARTICLES]) {
         double h = target.getHeight();
         for (int i = 0; i < 10; i++) {
            double rx = (Math.random() - 0.5) * target.getWidth();
            double ry = Math.random() * h;
            double rz = (Math.random() - 0.5) * target.getWidth();
            mc.world.addParticle(ParticleTypes.CRIT, target.getX() + rx, target.getY() + ry, target.getZ() + rz,
               rx * 0.6, 0.15 + Math.random() * 0.2, rz * 0.6);
         }

         mc.world.addParticle(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY() + h * 0.6, target.getZ(), 0.0, 0.1, 0.0);
      }
   }

   // ------------------------------------------------------------------ JumpCircles
   private record Circle(Vec3d pos, long start) {
   }

   private static final List<Circle> circles = new ArrayList<>();
   private static final Map<UUID, Boolean> wasGround = new HashMap<>();

   public static void tick() {
      if (mc.world == null) {
         return;
      }

      long now = System.currentTimeMillis();
      circles.removeIf(c -> now - c.start > 900L);
      if (!Kuro.on[Kuro.JUMPCIRCLES]) {
         wasGround.clear();
         return;
      }

      for (AbstractClientPlayerEntity pl : mc.world.getPlayers()) {
         boolean g = pl.isOnGround();
         Boolean was = wasGround.get(pl.getUuid());
         if (was != null && was && !g && pl.getVelocity().y > 0.2 && circles.size() < 64) {
            circles.add(new Circle(pl.getPos(), now));
         }

         wasGround.put(pl.getUuid(), g);
      }
   }

   // ------------------------------------------------------------------ dunya cizimi (Render.render icinden, sistem cizgi modu)
   public static boolean anyWorld() {
      return Kuro.on[Kuro.TARGETESP] || Kuro.on[Kuro.PEARLTRAJ] || Kuro.on[Kuro.JUMPCIRCLES];
   }

   public static void world(BufferBuilder b, Matrix4f m, Vec3d cam, float td) {
      long now = System.currentTimeMillis();
      if (Kuro.on[Kuro.TARGETESP] && lastTarget != null) {
         if (!lastTarget.isAlive() || now - lastHitMs > 8000L || mc.world == null) {
            lastTarget = null;
         } else {
            Vec3d p = lastTarget.getLerpedPos(td);
            double h = lastTarget.getHeight();
            double r = Math.max(0.45, lastTarget.getWidth() * 0.85);
            double t = (now % 1600L) / 1600.0;
            double y1 = p.y + h * (0.5 + 0.5 * Math.sin(t * Math.PI * 2.0));
            double y2 = p.y + h * (0.5 + 0.5 * Math.sin(t * Math.PI * 2.0 + Math.PI));
            float pulse = 0.6F + 0.4F * (float)Math.sin(now / 150.0);
            circle(b, m, p.x, y1, p.z, r, 32, 1.0F, 0.2F * pulse, 0.2F * pulse, 1.0F);
            circle(b, m, p.x, y2, p.z, r, 32, 1.0F, 1.0F, 1.0F, 0.8F);
            circle(b, m, p.x, p.y + 0.02, p.z, r * 1.1, 32, 1.0F, 0.2F, 0.2F, 0.5F);
         }
      }

      if (Kuro.on[Kuro.JUMPCIRCLES]) {
         for (Circle c : circles) {
            double prog = (now - c.start) / 900.0;
            if (prog < 0.0 || prog > 1.0) {
               continue;
            }

            circle(b, m, c.pos.x, c.pos.y + 0.03, c.pos.z, 0.3 + 1.6 * prog, 36, 0.4F, 0.8F, 1.0F, (float)(1.0 - prog));
         }
      }

      if (Kuro.on[Kuro.PEARLTRAJ]) {
         pearl(b, m, td);
      }
   }

   private static void circle(BufferBuilder b, Matrix4f m, double cx, double cy, double cz, double r, int seg, float rr, float g, float bl, float a) {
      double px = cx + r;
      double pz = cz;
      for (int i = 1; i <= seg; i++) {
         double ang = i * (Math.PI * 2.0 / seg);
         double nx = cx + Math.cos(ang) * r;
         double nz = cz + Math.sin(ang) * r;
         Render.line(b, m, px, cy, pz, nx, cy, nz, rr, g, bl, a);
         px = nx;
         pz = nz;
      }
   }

   // ------------------------------------------------------------------ PearlTrajectory
   private static void pearl(BufferBuilder b, Matrix4f m, float td) {
      ClientPlayerEntity p = mc.player;
      if (p == null || mc.world == null) {
         return;
      }

      if (!(p.getMainHandStack().isOf(Items.ENDER_PEARL) || p.getOffHandStack().isOf(Items.ENDER_PEARL))) {
         return;
      }

      Vec3d pos = p.getLerpedPos(td).add(0.0, p.getStandingEyeHeight() - 0.1, 0.0);
      Vec3d vel = p.getRotationVec(td).multiply(1.5);
      for (int i = 0; i < 120; i++) {
         Vec3d next = pos.add(vel);
         var hit = mc.world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
         boolean stop = hit.getType() == HitResult.Type.BLOCK;
         Vec3d end = stop ? hit.getPos() : next;
         Render.line(b, m, pos.x, pos.y, pos.z, end.x, end.y, end.z, 0.3F, 1.0F, 0.7F, 1.0F);
         if (stop) {
            Render.box(b, m, end.x - 0.15, end.y - 0.15, end.z - 0.15, end.x + 0.15, end.y + 0.15, end.z + 0.15, 0.3F, 1.0F, 0.7F, 1.0F);
            return;
         }

         pos = next;
         vel = vel.multiply(0.99).add(0.0, -0.03, 0.0);
      }
   }

   // ------------------------------------------------------------------ HUD: LowHealthWarning + CustomCrosshair
   public static void hud(DrawContext dc, RenderTickCounter tc) {
      ClientPlayerEntity p = mc.player;
      if (p == null || mc.world == null || mc.options.hudHidden) {
         return;
      }

      int w = dc.getScaledWindowWidth();
      int h = dc.getScaledWindowHeight();
      if (Kuro.on[Kuro.LOWHP] && !p.isCreative() && !p.isSpectator()) {
         float hp = p.getHealth() + p.getAbsorptionAmount();
         if (hp <= 8.0F) {
            float sev = (8.0F - hp) / 8.0F;
            float pulse = 0.65F + 0.35F * (float)Math.sin(System.currentTimeMillis() / 220.0);
            int a = (int)(Math.min(1.0F, 0.25F + sev) * pulse * 150.0F);
            int strong = (a << 24) | 0xE01010;
            int none = 0x00E01010;
            int band = Math.max(20, h / 6);
            dc.fillGradient(0, 0, w, band, strong, none);
            dc.fillGradient(0, h - band, w, h, none, strong);
            int side = Math.max(4, w / 60);
            dc.fill(0, 0, side, h, (a / 2 << 24) | 0xE01010);
            dc.fill(w - side, 0, w, h, (a / 2 << 24) | 0xE01010);
         }
      }

      if (Kuro.on[Kuro.CROSSHAIR] && mc.options.getPerspective().isFirstPerson() && mc.currentScreen == null) {
         int cx = w / 2;
         int cy = h / 2;
         int gap = 3;
         int len = 5;
         int col = 0xFFFFFFFF;
         int sh = 0xAA000000;
         dc.fill(cx - gap - len - 1, cy - 1, cx - gap + 1, cy + 2, sh);
         dc.fill(cx + gap - 1, cy - 1, cx + gap + len + 1, cy + 2, sh);
         dc.fill(cx - 1, cy - gap - len - 1, cx + 2, cy - gap + 1, sh);
         dc.fill(cx - 1, cy + gap - 1, cx + 2, cy + gap + len + 1, sh);
         dc.fill(cx - gap - len, cy, cx - gap, cy + 1, col);
         dc.fill(cx + gap, cy, cx + gap + len, cy + 1, col);
         dc.fill(cx, cy - gap - len, cx + 1, cy - gap, col);
         dc.fill(cx, cy + gap, cx + 1, cy + gap + len, col);
      }
   }
}
