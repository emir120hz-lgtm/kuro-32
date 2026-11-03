package kuro;

import com.mojang.blaze3d.systems.RenderSystem;
import kuro.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * 3D HUD: ekranin iki yaninda dunyada "yuzen" perspektifli paneller.
 * Paneller kalin bir levhadir (on yuz, arka yuz, kenarlar), icerdeki cubuklar one dogru cikintilidir,
 * kafani cevirince hafifce geriden gelir (sallanma) ve hafifce yukari asagi salinir.
 * Sol panel: can, aclik, zirh dayanikliligi. Sag panel: FPS, ping, koordinat, totem, kristal, eldeki esya.
 */
public final class Hud3D {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   private static final double PANEL_W = 0.46;
   private static final double PANEL_H = 0.46;
   private static final double PANEL_T = 0.035;
   private static final double TEXT_PX = 0.0032;

   private static float lagYaw;
   private static float lagPitch;
   private static boolean haveLag;
   private static long lastNanos;
   private static float time;

   private Hud3D() {
   }

   /** Bir panelin dunya ici cercevesi (merkez + sag / yukari / "ileri" eksenleri). */
   private static final class Frame {
      Vec3d c;
      Vec3d pr;
      Vec3d pu;
      Vec3d pf;
      double sc;
      Vec3d at(double lx, double ly, double out) {
         // out: levhanin on yuzunden kameraya dogru uzaklik (panel birimi degil, dunya birimi)
         return c.add(pr.multiply(lx * sc)).add(pu.multiply(ly * sc)).add(pf.multiply(-(PANEL_T * sc * 0.5 + out * sc)));
      }
   }

   public static void render(WorldRenderContext ctx) {
      if (!Kuro.on[Kuro.HUD3D] || mc.world == null || mc.player == null || mc.options.hudHidden) {
         return;
      }

      MatrixStack ms = ctx.matrixStack();
      if (ms == null) {
         return;
      }

      boolean left = Config.h3Left >= 0.5F;
      boolean right = Config.h3Right >= 0.5F;
      if (!left && !right) {
         return;
      }

      long now = System.nanoTime();
      float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0E9F, 0.1F);
      lastNanos = now;
      time += dt;

      float yawDeg = ctx.camera().getYaw();
      float pitchDeg = ctx.camera().getPitch();
      if (!haveLag) {
         lagYaw = yawDeg;
         lagPitch = pitchDeg;
         haveLag = true;
      }

      float k = Math.min(1.0F, dt * 7.0F);
      lagYaw += MathHelper.wrapDegrees(yawDeg - lagYaw) * k;
      lagPitch += (pitchDeg - lagPitch) * k;
      float dYaw = MathHelper.clamp(MathHelper.wrapDegrees(yawDeg - lagYaw), -30.0F, 30.0F) * Config.h3Sway;
      float dPitch = MathHelper.clamp(pitchDeg - lagPitch, -30.0F, 30.0F) * Config.h3Sway;

      // kamera ekseni (dunya koordinatinda): ileri / sag / yukari
      double yr = Math.toRadians(yawDeg);
      Vec3d f = Vec3d.fromPolar(pitchDeg, yawDeg);
      Vec3d r = new Vec3d(-Math.cos(yr), 0.0, -Math.sin(yr));
      Vec3d u = r.crossProduct(f).normalize();

      double d = Config.h3Dist;
      double sc = Config.h3Scale;
      double shiftX = -d * Math.tan(Math.toRadians(dYaw)) * 0.6;
      double shiftY = d * Math.tan(Math.toRadians(dPitch)) * 0.6;
      double bob = Math.sin(time * 1.3) * 0.004;

      Frame lf = null;
      Frame rf = null;
      if (left) {
         lf = frame(f, r, u, d, sc, -1.0, shiftX, shiftY + bob, dYaw);
      }

      if (right) {
         rf = frame(f, r, u, d, sc, 1.0, shiftX, shiftY - bob, dYaw);
      }

      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      RenderSystem.disableDepthTest();
      RenderSystem.disableCull();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();

      ms.push();
      Matrix4f m = ms.peek().getPositionMatrix();
      float alpha = Math.max(0.1F, Math.min(1.0F, Config.h3Alpha / 100.0F));
      int accent = Theme.accent() & 0xFFFFFF;

      BufferBuilder b = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
      if (lf != null) {
         shell(b, m, lf, alpha, accent);
         statusBars(b, m, lf, alpha, accent);
         gem(b, m, lf, accent);
      }

      if (rf != null) {
         shell(b, m, rf, alpha, accent);
         infoBars(b, m, rf, accent);
      }

      Render.flush(b);

      VertexConsumerProvider.Immediate vc = mc.getBufferBuilders().getEntityVertexConsumers();
      if (lf != null) {
         statusText(m, vc, lf, accent);
      }

      if (rf != null) {
         infoText(m, vc, rf, accent);
      }

      vc.draw();
      ms.pop();

      RenderSystem.enableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.disableBlend();
   }

   /** sgn: -1 sol panel, +1 sag panel. Panel kameraya dogru (icerde) hafifce doner. */
   private static Frame frame(Vec3d f, Vec3d r, Vec3d u, double d, double sc, double sgn, double shiftX, double shiftY, float dYaw) {
      double x = sgn * Config.h3Side + shiftX;
      double y = Config.h3Height + shiftY;
      double phi = Math.toRadians(-sgn * Config.h3Tilt - dYaw * 0.5);
      double cp = Math.cos(phi);
      double sp = Math.sin(phi);
      Frame fr = new Frame();
      fr.c = f.multiply(d).add(r.multiply(x)).add(u.multiply(y));
      fr.pr = r.multiply(cp).add(f.multiply(sp));
      fr.pu = u;
      fr.pf = f.multiply(cp).subtract(r.multiply(sp));
      fr.sc = sc;
      return fr;
   }

   // ------------------------------------------------------------------ geometri

   private static void vtx(BufferBuilder b, Matrix4f m, Vec3d p, int rgb, float a) {
      b.vertex(m, (float)p.x, (float)p.y, (float)p.z).color(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, a);
   }

   private static void quad(BufferBuilder b, Matrix4f m, Vec3d p1, Vec3d p2, Vec3d p3, Vec3d p4, int rgb, float a) {
      vtx(b, m, p1, rgb, a);
      vtx(b, m, p2, rgb, a);
      vtx(b, m, p3, rgb, a);
      vtx(b, m, p4, rgb, a);
   }

   private static int scale(int rgb, float k) {
      int r = Math.min(255, (int)(((rgb >> 16) & 0xFF) * k));
      int g = Math.min(255, (int)(((rgb >> 8) & 0xFF) * k));
      int bl = Math.min(255, (int)((rgb & 0xFF) * k));
      return (r << 16) | (g << 8) | bl;
   }

   /** Kalin levha: arka yuz, 4 kenar, on yuz. c merkez, pr/pu/pf eksenler (pf: ileri, on yuz kameraya bakar). */
   private static void slab(BufferBuilder b, Matrix4f m, Vec3d c, Vec3d pr, Vec3d pu, Vec3d pf,
                            double w, double h, double t, int front, float fa, int back, float ba, int side, float sa) {
      Vec3d hx = pr.multiply(w * 0.5);
      Vec3d hy = pu.multiply(h * 0.5);
      Vec3d hz = pf.multiply(t * 0.5);
      Vec3d lbf = c.subtract(hx).subtract(hy).subtract(hz);
      Vec3d rbf = c.add(hx).subtract(hy).subtract(hz);
      Vec3d rtf = c.add(hx).add(hy).subtract(hz);
      Vec3d ltf = c.subtract(hx).add(hy).subtract(hz);
      Vec3d lbb = c.subtract(hx).subtract(hy).add(hz);
      Vec3d rbb = c.add(hx).subtract(hy).add(hz);
      Vec3d rtb = c.add(hx).add(hy).add(hz);
      Vec3d ltb = c.subtract(hx).add(hy).add(hz);

      quad(b, m, lbb, rbb, rtb, ltb, back, ba);
      quad(b, m, rbf, rtf, rtb, rbb, side, sa);
      quad(b, m, lbf, lbb, ltb, ltf, side, sa);
      quad(b, m, ltf, ltb, rtb, rtf, scale(side, 1.25F), sa);
      quad(b, m, lbf, rbf, rbb, lbb, scale(side, 0.8F), sa);
      quad(b, m, lbf, rbf, rtf, ltf, front, fa);
   }

   /** Panelin govdesi: koyu cam levha + ust kenarda vurgu rengi seridi. */
   private static void shell(BufferBuilder b, Matrix4f m, Frame fr, float alpha, int accent) {
      Vec3d pn = fr.pf;
      slab(b, m, fr.c, fr.pr, fr.pu, pn, PANEL_W * fr.sc, PANEL_H * fr.sc, PANEL_T * fr.sc,
         0x0C0C12, alpha, 0x050508, Math.min(1.0F, alpha + 0.15F), scale(accent, 0.55F), 0.9F);
      // ust serit (one dogru hafif cikinti)
      float e = 0.012F;
      Vec3d tc = fr.at(0.0, PANEL_H * 0.5 - 0.012, e * 0.5);
      slab(b, m, tc, fr.pr, fr.pu, pn, (PANEL_W - 0.02) * fr.sc, 0.012 * fr.sc, e * fr.sc,
         accent, 1.0F, scale(accent, 0.5F), 1.0F, scale(accent, 0.8F), 1.0F);
   }

   /** Onde duran 3D cubuk. (lx, ly) panel yerel koordinati, w/h panel birimi, e cikinti. */
   private static void bar(BufferBuilder b, Matrix4f m, Frame fr, double lx, double ly, double w, double h, double e, int rgb, float a) {
      Vec3d c = fr.at(lx, ly, e * 0.5);
      slab(b, m, c, fr.pr, fr.pu, fr.pf, w * fr.sc, h * fr.sc, e * fr.sc, rgb, a, scale(rgb, 0.45F), a, scale(rgb, 0.7F), a);
   }

   /** Bos cubuk arka plani (cukur). */
   private static void trough(BufferBuilder b, Matrix4f m, Frame fr, double lx, double ly, double w, double h) {
      bar(b, m, fr, lx, ly, w, h, 0.004, 0x1A1A24, 0.95F);
   }

   // ------------------------------------------------------------------ sol panel: durum

   private static int hpColor(float frac) {
      return Config.hsb(120.0F * Math.max(0.0F, Math.min(1.0F, frac)), 0.85F, 1.0F);
   }

   private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

   private static float armorFrac(ClientPlayerEntity p, EquipmentSlot s) {
      ItemStack st = p.getEquippedStack(s);
      if (st.isEmpty()) {
         return -1.0F;
      }

      if (st.getMaxDamage() <= 0) {
         return 1.0F;
      }

      return 1.0F - (float)st.getDamage() / (float)st.getMaxDamage();
   }

   private static void statusBars(BufferBuilder b, Matrix4f m, Frame fr, float alpha, int accent) {
      ClientPlayerEntity p = mc.player;
      float maxHp = Math.max(1.0F, p.getMaxHealth());
      float hp = p.getHealth();
      float abs = p.getAbsorptionAmount();
      float food = p.getHungerManager().getFoodLevel() / 20.0F;

      double bw = 0.38;
      double x0 = -bw * 0.5;
      // can
      trough(b, m, fr, 0.0, 0.085, bw, 0.034);
      float hf = Math.max(0.0F, Math.min(1.0F, hp / maxHp));
      if (hf > 0.001F) {
         double w = bw * hf;
         bar(b, m, fr, x0 + w * 0.5, 0.085, w, 0.034, 0.016, hpColor(hf), 1.0F);
      }

      if (abs > 0.01F) {
         double w = bw * Math.min(1.0F, abs / maxHp);
         bar(b, m, fr, x0 + w * 0.5, 0.060, w, 0.012, 0.016, 0xFFD24A, 1.0F);
      }

      // aclik
      trough(b, m, fr, 0.0, 0.002, bw, 0.026);
      if (food > 0.001F) {
         double w = bw * food;
         bar(b, m, fr, x0 + w * 0.5, 0.002, w, 0.026, 0.014, 0xD98A3D, 1.0F);
      }

      // zirh: dikey 3D sutunlar
      double baseY = -0.205;
      double maxH = 0.125;
      for (int i = 0; i < 4; i++) {
         double cx = -0.15 + i * 0.10;
         trough(b, m, fr, cx, baseY + maxH * 0.5, 0.062, maxH);
         float fa = armorFrac(p, ARMOR[i]);
         if (fa >= 0.0F) {
            double h = Math.max(0.006, maxH * fa);
            bar(b, m, fr, cx, baseY + h * 0.5, 0.062, h, 0.022, hpColor(fa), 1.0F);
         }
      }
   }

   /** Sol ust kosede donen 3D elmas (oktahedron). */
   private static void gem(BufferBuilder b, Matrix4f m, Frame fr, int accent) {
      Vec3d g = fr.at(0.172, 0.175, 0.05);
      double rad = 0.032 * fr.sc;
      double hgt = 0.045 * fr.sc;
      double spin = time * 1.6;
      Vec3d top = g.add(fr.pu.multiply(hgt));
      Vec3d bot = g.subtract(fr.pu.multiply(hgt));
      Vec3d[] eq = new Vec3d[4];
      for (int i = 0; i < 4; i++) {
         double a = spin + i * Math.PI * 0.5;
         eq[i] = g.add(fr.pr.multiply(Math.cos(a) * rad)).add(fr.pf.multiply(Math.sin(a) * rad));
      }

      // 8 ucgen; uzaktan yakina siralayip ciz (derinlik testi kapali)
      Vec3d[][] tris = new Vec3d[8][];
      for (int i = 0; i < 4; i++) {
         Vec3d a = eq[i];
         Vec3d c2 = eq[(i + 1) % 4];
         tris[i] = new Vec3d[]{top, a, c2};
         tris[i + 4] = new Vec3d[]{bot, c2, a};
      }

      Integer[] order = new Integer[8];
      double[] depth = new double[8];
      for (int i = 0; i < 8; i++) {
         order[i] = i;
         Vec3d cen = tris[i][0].add(tris[i][1]).add(tris[i][2]).multiply(1.0 / 3.0);
         depth[i] = cen.subtract(g).dotProduct(fr.pf); // buyuk = uzak
      }

      java.util.Arrays.sort(order, (x, y) -> Double.compare(depth[y], depth[x]));
      for (int idx : order) {
         Vec3d[] t = tris[idx];
         // yuz parlakligi: dondukce degisir
         Vec3d n = t[1].subtract(t[0]).crossProduct(t[2].subtract(t[0])).normalize();
         double lit = 0.55 + 0.45 * Math.abs(n.dotProduct(fr.pr.add(fr.pu).normalize()));
         int col = scale(accent, (float)(0.55 + lit * 0.7));
         quad(b, m, t[0], t[1], t[2], t[2], col, 0.92F);
      }
   }

   private static void statusText(Matrix4f base, VertexConsumerProvider.Immediate vc, Frame fr, int accent) {
      ClientPlayerEntity p = mc.player;
      float hp = p.getHealth() + p.getAbsorptionAmount();
      text(base, vc, fr, -0.19, 0.178, "DURUM", 0xFF000000 | accent, 1.15, 0);
      text(base, vc, fr, -0.19, 0.125, "CAN", 0xFFB8B8C4, 1.0, 0);
      text(base, vc, fr, 0.19, 0.125, (Math.round(hp * 10.0F) / 10.0F) + "", 0xFFFFFFFF, 1.0, 2);
      text(base, vc, fr, -0.19, 0.040, "AÇLIK", 0xFFB8B8C4, 1.0, 0);
      text(base, vc, fr, 0.19, 0.040, p.getHungerManager().getFoodLevel() + "", 0xFFFFFFFF, 1.0, 2);
      text(base, vc, fr, -0.19, -0.040, "ZIRH", 0xFFB8B8C4, 1.0, 0);
      String[] lbl = {"K", "G", "P", "B"};
      for (int i = 0; i < 4; i++) {
         float fa = armorFrac(p, ARMOR[i]);
         String s = fa < 0.0F ? "-" : Math.round(fa * 100.0F) + "%";
         text(base, vc, fr, -0.15 + i * 0.10, -0.228, lbl[i] + " " + s, 0xFFE8E8F0, 0.7, 1);
      }
   }

   // ------------------------------------------------------------------ sag panel: bilgi

   private static int countItem(net.minecraft.item.Item item) {
      ClientPlayerEntity p = mc.player;
      int n = 0;
      for (int i = 0; i < 36; i++) {
         ItemStack s = p.getInventory().getStack(i);
         if (s.isOf(item)) {
            n += s.getCount();
         }
      }

      if (p.getOffHandStack().isOf(item)) {
         n += p.getOffHandStack().getCount();
      }

      return n;
   }

   private static int ping() {
      if (mc.getNetworkHandler() == null || mc.player == null) {
         return 0;
      }

      PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
      return e == null ? 0 : e.getLatency();
   }

   private static void infoBars(BufferBuilder b, Matrix4f m, Frame fr, int accent) {
      // eldeki esyanin dayanikliligi
      ItemStack held = mc.player.getMainHandStack();
      double bw = 0.38;
      trough(b, m, fr, 0.0, -0.205, bw, 0.026);
      if (!held.isEmpty() && held.getMaxDamage() > 0) {
         float fa = 1.0F - (float)held.getDamage() / (float)held.getMaxDamage();
         double w = bw * Math.max(0.0F, Math.min(1.0F, fa));
         if (w > 0.002) {
            bar(b, m, fr, -bw * 0.5 + w * 0.5, -0.205, w, 0.026, 0.014, hpColor(fa), 1.0F);
         }
      }

      // ping kalitesi: 4 kucuk dikey cubuk
      int ping = ping();
      int level = ping <= 0 ? 0 : ping < 60 ? 4 : ping < 120 ? 3 : ping < 200 ? 2 : 1;
      for (int i = 0; i < 4; i++) {
         double h = 0.018 + i * 0.012;
         int col = i < level ? 0x34D399 : 0x2A2A36;
         bar(b, m, fr, 0.150 + i * 0.016, 0.098 - 0.012 + h * 0.5, 0.010, h, 0.012, col, 1.0F);
      }
   }

   private static void infoText(Matrix4f base, VertexConsumerProvider.Immediate vc, Frame fr, int accent) {
      ClientPlayerEntity p = mc.player;
      text(base, vc, fr, -0.19, 0.178, "BİLGİ", 0xFF000000 | accent, 1.15, 0);
      text(base, vc, fr, -0.19, 0.115, "FPS", 0xFFB8B8C4, 1.0, 0);
      text(base, vc, fr, 0.09, 0.115, mc.getCurrentFps() + "", 0xFFFFFFFF, 1.0, 2);
      text(base, vc, fr, -0.19, 0.060, "PING", 0xFFB8B8C4, 1.0, 0);
      text(base, vc, fr, 0.09, 0.060, ping() + " ms", 0xFFFFFFFF, 1.0, 2);
      text(base, vc, fr, -0.19, 0.005, "TOTEM", 0xFFB8B8C4, 1.0, 0);
      text(base, vc, fr, 0.19, 0.005, countItem(Items.TOTEM_OF_UNDYING) + "", 0xFFFFD24A, 1.0, 2);
      text(base, vc, fr, -0.19, -0.050, "KRİSTAL", 0xFFB8B8C4, 1.0, 0);
      text(base, vc, fr, 0.19, -0.050, countItem(Items.END_CRYSTAL) + "", 0xFFE879F9, 1.0, 2);
      text(base, vc, fr, 0.0, -0.110, Math.round(p.getX()) + "  " + Math.round(p.getY()) + "  " + Math.round(p.getZ()), 0xFFFFFFFF, 0.9, 1);
      ItemStack held = p.getMainHandStack();
      String name = held.isEmpty() ? "Boş el" : held.getName().getString();
      if (name.length() > 22) {
         name = name.substring(0, 21) + "…";
      }

      text(base, vc, fr, 0.0, -0.165, name, 0xFFE8E8F0, 0.85, 1);
   }

   // ------------------------------------------------------------------ yazi

   /** align: 0 sol, 1 orta, 2 sag. (lx, ly) yazinin dikey ortasi; ps yazi carpani. */
   private static void text(Matrix4f base, VertexConsumerProvider.Immediate vc, Frame fr, double lx, double ly, String s, int argb, double k, int align) {
      TextRenderer tr = mc.textRenderer;
      Text t = Text.literal(s);
      double ps = TEXT_PX * fr.sc * k;
      Vec3d pos = fr.at(lx, ly, 0.0045).add(fr.pu.multiply(4.0 * ps));
      float w = tr.getWidth(t);
      float ox = align == 1 ? -w / 2.0F : align == 2 ? -w : 0.0F;
      Matrix4f model = new Matrix4f(
         (float)(fr.pr.x * ps), (float)(fr.pr.y * ps), (float)(fr.pr.z * ps), 0.0F,
         (float)(-fr.pu.x * ps), (float)(-fr.pu.y * ps), (float)(-fr.pu.z * ps), 0.0F,
         (float)(fr.pf.x * ps), (float)(fr.pf.y * ps), (float)(fr.pf.z * ps), 0.0F,
         (float)pos.x, (float)pos.y, (float)pos.z, 1.0F
      );
      Matrix4f mat = new Matrix4f(base).mul(model);
      tr.draw(t, ox, 0.0F, argb, false, mat, vc, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
   }
}
