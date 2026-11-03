package kuro.ui;

import static org.lwjgl.nanovg.NanoVG.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kuro.Config;
import kuro.Freecam;
import kuro.Freelook;
import kuro.Kuro;
import kuro.Spotify;
import net.minecraft.client.util.Window;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Oyun ici HUD (NanoVG): filigran, modul listesi, kisayol paneli, bilgi cubugu,
 * kamera modu gostergesi ve bildirimler.
 */
public final class Hud {
   private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
   private static final String[] DIRS = new String[]{"Güney +Z", "Batı -X", "Kuzey -Z", "Doğu +X"}; // yaw: 0=Güney 90=Batı 180=Kuzey 270=Doğu

   private static long last = System.nanoTime();
   private static final Anim[] LIST_A = new Anim[Kuro.NAMES.length];
   private static final Anim[] KB_A = new Anim[Kuro.NAMES.length];
   private static final Anim WM_IN = new Anim(0.0F);
   private static final Anim CAM_A = new Anim(0.0F);
   private static float camLabelAge;

   private static double lastX;
   private static double lastZ;
   private static boolean haveLast;
   private static float speed;
   private static int slowTick;
   private static String fpsStr = "0 fps";
   private static String pingStr = "";
   private static String timeStr = "";
   private static String xyzStr = "";
   private static String dirStr = "";
   private static String speedStr = "";
   private static final List<String> SEGS = new ArrayList<>();
   private static final Integer[] ORDER = new Integer[Kuro.NAMES.length];
   private static final float[] WIDTHS = new float[Kuro.NAMES.length];

   static {
      for (int i = 0; i < LIST_A.length; i++) {
         LIST_A[i] = new Anim(0.0F);
         KB_A[i] = new Anim(0.0F);
      }
   }

   private Hud() {
   }

   // ------------------------------------------------------------------ HUD duzeni (kullanici konumlari)
   public static boolean editing;
   public static final String[] EL_NAMES = {"Filigran", "Kısayol paneli", "Modül listesi", "Bilgi çubuğu", "TargetHUD", "Radar", "SpotifyHUD"};
   public static final int EL_COUNT = EL_NAMES.length;
   private static final float[] OX = new float[EL_COUNT];
   private static final float[] OY = new float[EL_COUNT];
   private static final float[] BX = new float[EL_COUNT];
   private static final float[] BY = new float[EL_COUNT];
   private static final float[] BW = new float[EL_COUNT];
   private static final float[] BH = new float[EL_COUNT];
   private static String loadedLayout;

   private static void loadLayout() {
      String l = Config.hudLayout == null ? "" : Config.hudLayout;
      if (l.equals(loadedLayout)) {
         return;
      }

      loadedLayout = l;
      java.util.Arrays.fill(OX, 0.0F);
      java.util.Arrays.fill(OY, 0.0F);
      String[] parts = l.split(";");
      for (int i = 0; i < parts.length && i < EL_COUNT; i++) {
         String[] xy = parts[i].split(",");
         if (xy.length == 2) {
            try {
               float x = Float.parseFloat(xy[0]);
               float y = Float.parseFloat(xy[1]);
               if (Float.isFinite(x) && Float.isFinite(y)) {
                  OX[i] = x;
                  OY[i] = y;
               }
            } catch (NumberFormatException ignored) {
            }
         }
      }
   }

   public static void saveLayout() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < EL_COUNT; i++) {
         if (i > 0) {
            sb.append(';');
         }

         sb.append(Math.round(OX[i])).append(',').append(Math.round(OY[i]));
      }

      Config.hudLayout = sb.toString();
      loadedLayout = Config.hudLayout;
      Config.save();
   }

   public static float offX(int id) {
      loadLayout();
      return OX[id];
   }

   public static float offY(int id) {
      loadLayout();
      return OY[id];
   }

   public static void setOff(int id, float x, float y) {
      loadLayout();
      OX[id] = x;
      OY[id] = y;
   }

   public static void resetAll() {
      loadLayout();
      java.util.Arrays.fill(OX, 0.0F);
      java.util.Arrays.fill(OY, 0.0F);
   }

   private static void bounds(int id, float x, float y, float w, float h) {
      BX[id] = x;
      BY[id] = y;
      BW[id] = w;
      BH[id] = h;
   }

   /** Ogenin kaydirmasiz (varsayilan) kutusu: {x, y, w, h}. Bu karede cizilmediyse makul bir varsayilan. */
   public static float[] baseRect(int id, float lw, float lh) {
      if (BW[id] > 0.0F) {
         return new float[]{BX[id], BY[id], BW[id], BH[id]};
      }

      return switch (id) {
         case 0 -> new float[]{12.0F, 12.0F, 230.0F, 26.0F};
         case 1 -> new float[]{12.0F, 50.0F, 158.0F, 26.0F};
         case 2 -> new float[]{lw - 12.0F - 110.0F, 12.0F, 110.0F, 22.0F};
         case 3 -> new float[]{lw - 12.0F - 190.0F, lh - 38.0F, 190.0F, 26.0F};
         case 4 -> new float[]{lw * 0.5F + 30.0F, lh * 0.5F + 30.0F, 160.0F, 44.0F};
         case 5 -> new float[]{12.0F, lh - 12.0F - Config.radarSize, Config.radarSize, Config.radarSize};
         default -> new float[]{lw - 12.0F - 220.0F, lh - 12.0F - 26.0F - 8.0F - 48.0F, 220.0F, 48.0F};
      };
   }

   /** Kaydirma dahil ekrandaki kutu: {x, y, w, h}. */
   public static float[] rect(int id, float lw, float lh) {
      float[] r = baseRect(id, lw, lh);
      r[0] += offX(id);
      r[1] += offY(id);
      return r;
   }

   private static void begin(int id) {
      nvgSave(Nvg.ctx);
      nvgTranslate(Nvg.ctx, OX[id], OY[id]);
   }

   private static void end() {
      nvgRestore(Nvg.ctx);
   }

   /** Her client tick'inde: yatay hizi olc (blok/sn). */
   public static void tick() {
      MinecraftClient mc = Kuro.mc;
      if (mc.player == null) {
         haveLast = false;
         return;
      }

      double x = mc.player.getX();
      double z = mc.player.getZ();
      if (haveLast) {
         float v = (float)(Math.hypot(x - lastX, z - lastZ) * 20.0);
         speed += (v - speed) * 0.25F;
      }

      lastX = x;
      lastZ = z;
      haveLast = true;

      if (Kuro.on[Kuro.SPOTIFY]) {
         Spotify.tick();
      }

      // metinleri kare basina degil tick basina uret (string/format allocation'i azalir)
      double py = mc.player.getY();
      int dir = ((int)Math.floor(mc.player.getYaw() * 4.0F / 360.0F + 0.5F)) & 3;
      xyzStr = (int)Math.floor(x) + "  " + (int)Math.floor(py) + "  " + (int)Math.floor(z);
      dirStr = DIRS[dir];
      speedStr = ((int)(speed * 10.0F) / 10.0F) + " b/s";
      if (slowTick++ % 10 == 0) {
         fpsStr = mc.getCurrentFps() + " fps";
         int ping = ping(mc);
         pingStr = ping > 0 ? ping + " ms" : "";
         timeStr = LocalTime.now().format(TIME);
      }
   }

   public static void render(DrawContext dc, RenderTickCounter tickCounter) {
      MinecraftClient mc = Kuro.mc;
      if (mc.player == null || mc.world == null || mc.options.hudHidden) {
         return;
      }

      if (!Nvg.ensure()) {
         return;
      }

      Window win = mc.getWindow();
      int fw = win.getFramebufferWidth();
      int fh = win.getFramebufferHeight();
      if (fw <= 0 || fh <= 0) {
         return;
      }

      if (!needsDraw()) {
         last = System.nanoTime();
         return;
      }

      // Minecraft'in bekleyen cizimlerini bitir, sonra NanoVG ile ustune ciz
      dc.draw();

      long now = System.nanoTime();
      float dt = Math.min(0.1F, (now - last) / 1.0E9F);
      last = now;

      float s = Theme.scale(fh);
      float lw = fw / s;
      float lh = fh / s;
      Theme.update(dt);

      loadLayout();
      java.util.Arrays.fill(BW, 0.0F);
      Nvg.begin(lw, lh, s);
      try {
         if (Config.watermark) {
            begin(0);
            watermark(mc, lw, dt);
            end();
         } else {
            WM_IN.snap(0.0F);
         }

         if (Config.keybinds) {
            begin(1);
            keybinds(dt);
            end();
         }

         if (Config.moduleList) {
            begin(2);
            moduleList(lw, dt);
            end();
         }

         cameraBadge(lw, dt);

         if (Config.info) {
            begin(3);
            infoBar(mc, lw, lh);
            end();
         }

         if (Kuro.on[Kuro.TARGETHUD]) {
            begin(4);
            targetHud(mc, lw, lh, dt);
            end();
         }

         java.util.Arrays.fill(CORNER_USED, 0.0F);
         partial = tickCounter.getTickDelta(true);
         begin(5);
         radar(mc, lw, lh, dt);
         end();
         begin(6);
         spotify(mc, lw, lh, dt);
         end();

         Toasts.render(lw, lh, dt);
      } finally {
         Nvg.end();
      }
   }

   // ------------------------------------------------------------------ TargetHUD
   private static net.minecraft.entity.player.PlayerEntity thTarget;
   private static float thTime;
   private static final Anim TH_IN = new Anim(0.0F);
   private static final Anim TH_HP = new Anim(1.0F);

   private static void targetHud(MinecraftClient mc, float lw, float lh, float dt) {
      net.minecraft.entity.player.PlayerEntity t = null;
      if (mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult ehr
         && ehr.getEntity() instanceof net.minecraft.entity.player.PlayerEntity pe
         && pe.isAlive()) {
         t = pe;
         thTarget = pe;
         thTime = 3.0F;
      } else if (thTarget != null && thTime > 0.0F) {
         thTime -= dt;
         if (thTarget.isAlive() && thTarget.squaredDistanceTo(mc.player) < 400.0) {
            t = thTarget;
         }
      }

      TH_IN.to(t != null ? 1.0F : 0.0F).tick(dt, 12.0F);
      if (TH_IN.v <= 0.01F || thTarget == null) {
         return;
      }

      net.minecraft.entity.player.PlayerEntity d = thTarget;
      float hp = d.getHealth() + d.getAbsorptionAmount();
      float ratio = Anim.clamp(hp / Math.max(1.0F, d.getMaxHealth()), 0.0F, 1.0F);
      TH_HP.to(ratio).tick(dt, 10.0F);
      float w = 160.0F;
      float h = 44.0F;
      float x = lw * 0.5F + 30.0F;
      float y = lh * 0.5F + 30.0F;
      float e = Anim.easeOutCubic(TH_IN.v);
      bounds(4, x, y, w, h);

      nvgSave(Nvg.ctx);
      nvgGlobalAlpha(Nvg.ctx, e);
      nvgTranslate(Nvg.ctx, 0.0F, (1.0F - e) * 8.0F);
      Nvg.glow(x, y + 2.0F, w, h, 5.0F, 14.0F, 0x59000000);
      Nvg.rect(x, y, w, h, 5.0F, Theme.HUD_BG);
      Nvg.border(x, y, w, h, 5.0F, 1.0F, 0x12FFFFFF);
      Nvg.rectGradH(x + 4.0F, y, w - 8.0F, 2.0F, 1.0F, Theme.accent(), Theme.accent2());
      Nvg.textL(d.getName().getString(), x + 10.0F, y + 15.0F, 13.0F, true, Theme.TEXT);
      Nvg.textR(Float.toString(Math.round(hp * 10.0F) / 10.0F), x + w - 10.0F, y + 15.0F, 12.0F, false, Theme.TEXT_DIM);
      Nvg.rect(x + 10.0F, y + 28.0F, w - 20.0F, 6.0F, 3.0F, 0xFF26262B);
      Nvg.rect(x + 10.0F, y + 28.0F, Math.max(4.0F, (w - 20.0F) * TH_HP.v), 6.0F, 3.0F, Theme.mix(Theme.RED, Theme.GREEN, TH_HP.v));
      nvgRestore(Nvg.ctx);
   }

   // ------------------------------------------------------------------ Radar / SpotifyHUD ortak yerlesim
   private static final float[] CORNER_USED = new float[4];
   private static float partial;
   private static final Anim RADAR_A = new Anim(0.0F);
   private static final Anim SP_A = new Anim(0.0F);

   /** Kose (0 sol-alt, 1 sag-alt, 2 sol-ust, 3 sag-ust) icin kutunun sol-ust konumunu hesaplar ve o koseyi doldurur. */
   private static float[] place(int corner, float w, float h, float lw, float lh) {
      corner = Math.max(0, Math.min(3, corner));
      boolean right = corner == 1 || corner == 3;
      boolean bottom = corner <= 1;
      float used = CORNER_USED[corner];
      float x = right ? lw - 12.0F - w : 12.0F;
      float y;
      if (bottom) {
         // sag-altta bilgi cubugu (26px) var
         float base = corner == 1 ? 12.0F + 26.0F + 8.0F : 12.0F;
         y = lh - base - h - used;
      } else {
         float listH = 0.0F;
         if (corner == 3 && Config.moduleList) {
            for (Anim an : LIST_A) {
               listH += 22.0F * an.v;
            }
         }

         float base = corner == 2 ? 50.0F : 12.0F + listH + 6.0F;
         y = base + used;
      }

      CORNER_USED[corner] += h + 8.0F;
      return new float[]{x, y};
   }

   private static String fitText(String s, float size, boolean bold, float maxW) {
      if (Nvg.textWidth(s, size, bold) <= maxW) {
         return s;
      }

      int n = s.length();
      while (n > 1 && Nvg.textWidth(s.substring(0, n) + "…", size, bold) > maxW) {
         n--;
      }

      return s.substring(0, n).trim() + "…";
   }

   // ------------------------------------------------------------------ Radar

   private static void radar(MinecraftClient mc, float lw, float lh, float dt) {
      RADAR_A.to(Kuro.on[Kuro.RADAR] || editing ? 1.0F : 0.0F).tick(dt, 12.0F);
      if (RADAR_A.v <= 0.01F || mc.player == null || mc.world == null) {
         return;
      }

      float e = Anim.easeOutCubic(RADAR_A.v);
      float size = Anim.clamp(Config.radarSize, 60.0F, 160.0F);
      float r = size * 0.5F;
      float[] pos = place((int)Config.radarCorner, size, size, lw, lh);
      float cx = pos[0] + r;
      float cy = pos[1] + r;
      bounds(5, pos[0], pos[1], size, size);
      float range = Math.max(10.0F, Config.radarRange);
      int accent = Theme.accent();

      nvgSave(Nvg.ctx);
      nvgGlobalAlpha(Nvg.ctx, e);
      Nvg.glow(cx - r, cy - r + 2.0F, size, size, r, 14.0F, 0x59000000);
      Nvg.circle(cx, cy, r, Theme.HUD_BG);
      Nvg.circleStroke(cx, cy, r - 0.5F, 1.0F, Theme.a(accent, 0.55F));
      Nvg.circleStroke(cx, cy, r * 0.5F, 1.0F, 0x14FFFFFF);
      Nvg.line(cx - r + 3.0F, cy, cx + r - 3.0F, cy, 1.0F, 0x14FFFFFF);
      Nvg.line(cx, cy - r + 3.0F, cx, cy + r - 3.0F, 1.0F, 0x14FFFFFF);

      float yaw = net.minecraft.util.math.MathHelper.lerpAngleDegrees(partial, mc.player.prevYaw, mc.player.getYaw());
      double rad = Math.toRadians(yaw);
      double fx = -Math.sin(rad);
      double fz = Math.cos(rad);
      double rx = -Math.cos(rad);
      double rz = -Math.sin(rad);

      // kuzey isareti (oyuncunun yonune gore doner): kuzey = -Z
      double nUp = -fz;
      double nRight = -rz;
      Nvg.textC("N", cx + (float)(nRight * (r - 8.0F)), cy - (float)(nUp * (r - 8.0F)), 9.5F, true, Theme.TEXT_DIM);

      // gorus acisi (sabit, yukari bakar)
      float fov = 35.0F * (float)(Math.PI / 180.0);
      Nvg.line(cx, cy, cx - (float)Math.sin(fov) * (r - 4.0F), cy - (float)Math.cos(fov) * (r - 4.0F), 1.0F, Theme.a(accent, 0.30F));
      Nvg.line(cx, cy, cx + (float)Math.sin(fov) * (r - 4.0F), cy - (float)Math.cos(fov) * (r - 4.0F), 1.0F, Theme.a(accent, 0.30F));

      double px = mc.player.prevX + (mc.player.getX() - mc.player.prevX) * partial;
      double pz = mc.player.prevZ + (mc.player.getZ() - mc.player.prevZ) * partial;
      boolean mobs = Config.radarMobs >= 0.5F;
      boolean names = Config.radarNames >= 0.5F;
      double r2 = (double)range * range;
      int count = 0;

      for (net.minecraft.entity.Entity en : mc.world.getEntities()) {
         if (en == mc.player || !en.isAlive() || count > 250) {
            continue;
         }

         boolean isPlayer = en instanceof net.minecraft.entity.player.PlayerEntity;
         if (!isPlayer && !(mobs && en instanceof net.minecraft.entity.mob.MobEntity)) {
            continue;
         }

         double ex = en.prevX + (en.getX() - en.prevX) * partial;
         double ez = en.prevZ + (en.getZ() - en.prevZ) * partial;
         double dx = ex - px;
         double dz = ez - pz;
         if (dx * dx + dz * dz > r2) {
            continue;
         }

         count++;
         double up = dx * fx + dz * fz;
         double right = dx * rx + dz * rz;
         float sx = cx + (float)(right / range * (r - 6.0F));
         float sy = cy - (float)(up / range * (r - 6.0F));
         int col;
         if (isPlayer) {
            col = Theme.RED;
         } else if (en instanceof net.minecraft.entity.mob.HostileEntity) {
            col = 0xFFFBBF24;
         } else {
            col = 0xFF9CA3AF;
         }

         Nvg.circle(sx, sy, isPlayer ? 3.2F : 2.4F, col);
         if (names && isPlayer) {
            Nvg.textC(en.getName().getString(), sx, sy + 9.0F, 8.5F, false, Theme.TEXT);
         }
      }

      // sen: ortada ok
      Nvg.circle(cx, cy, 3.0F, accent);
      nvgRestore(Nvg.ctx);
   }

   // ------------------------------------------------------------------ SpotifyHUD

   private static void spotify(MinecraftClient mc, float lw, float lh, float dt) {
      SP_A.to(Kuro.on[Kuro.SPOTIFY] || editing ? 1.0F : 0.0F).tick(dt, 12.0F);
      if (SP_A.v <= 0.01F) {
         return;
      }

      float e = Anim.easeOutCubic(SP_A.v);
      boolean bar = Config.spProgress >= 0.5F && Spotify.progress >= 0.0F;
      float w = 220.0F;
      float h = bar ? 56.0F : 48.0F;
      float[] pos = place((int)Config.spCorner, w, h, lw, lh);
      float x = pos[0];
      float y = pos[1];
      bounds(6, x, y, w, h);
      int green = 0xFF1DB954;

      String title;
      String sub;
      if (!Spotify.running) {
         title = "Spotify açık değil";
         sub = "Masaüstü uygulamasını aç";
      } else if (Spotify.title.isEmpty()) {
         title = "Çalan şarkı yok";
         sub = Spotify.playing ? "" : "Duraklatıldı";
      } else {
         title = Spotify.title;
         sub = Spotify.artist.isEmpty() ? (Spotify.playing ? "" : "Duraklatıldı") : Spotify.artist + (Spotify.playing ? "" : "  ·  duraklatıldı");
      }

      nvgSave(Nvg.ctx);
      nvgGlobalAlpha(Nvg.ctx, e);
      nvgTranslate(Nvg.ctx, (x < lw * 0.5F ? -1.0F : 1.0F) * (1.0F - e) * 16.0F, 0.0F);
      Nvg.glow(x, y + 2.0F, w, h, 6.0F, 14.0F, 0x59000000);
      Nvg.rect(x, y, w, h, 6.0F, Theme.HUD_BG);
      Nvg.border(x, y, w, h, 6.0F, 1.0F, 0x12FFFFFF);
      Nvg.rectGradH(x + 4.0F, y, w - 8.0F, 2.0F, 1.0F, green, Theme.a(green, 0.2F));

      // sol: Spotify yesili daire + ses cubuklari
      float ix = x + 10.0F + 15.0F;
      float iy = y + 12.0F + 15.0F;
      Nvg.circle(ix, iy, 15.0F, green);
      for (int i = 0; i < 3; i++) {
         float ph = (float)(System.nanoTime() / 1.0E9 * 5.0 + i * 1.7);
         float bh = Spotify.playing && Spotify.running ? 5.0F + 6.0F * (0.5F + 0.5F * (float)Math.sin(ph)) : 6.0F;
         Nvg.rect(ix - 7.0F + i * 5.5F, iy + 6.0F - bh, 3.5F, bh, 1.5F, 0xFF0B0B10);
      }

      float tx = x + 10.0F + 30.0F + 10.0F;
      float maxW = x + w - 10.0F - tx;
      Nvg.textL(fitText(title, 12.5F, true, maxW), tx, y + 18.0F, 12.5F, true, Theme.TEXT);
      Nvg.textL(fitText(sub, 10.5F, false, maxW), tx, y + 33.0F, 10.5F, false, Theme.TEXT_DIM);

      if (bar) {
         float bx = x + 10.0F;
         float bw = w - 20.0F;
         Nvg.rect(bx, y + h - 10.0F, bw, 3.0F, 1.5F, 0xFF26262B);
         Nvg.rect(bx, y + h - 10.0F, Math.max(3.0F, bw * Spotify.progress), 3.0F, 1.5F, green);
      }

      nvgRestore(Nvg.ctx);
   }

   /** Cizilecek hicbir sey yoksa NanoVG karesi (GL durumu kaydet/geri yaz) hic acilmaz. */
   private static boolean needsDraw() {
      if (editing || Config.watermark || Config.info || Toasts.active() || RADAR_A.v > 0.0F || SP_A.v > 0.0F || Kuro.on[Kuro.RADAR] || Kuro.on[Kuro.SPOTIFY]) {
         return true;
      }

      for (int i = 0; i < Kuro.NAMES.length; i++) {
         if ((Kuro.on[i] && !Kuro.isAction(i)) || LIST_A[i].v > 0.0F || KB_A[i].v > 0.0F) {
            return true;
         }
      }

      return false;
   }

   // ------------------------------------------------------------------ filigran

   private static void watermark(MinecraftClient mc, float lw, float dt) {
      WM_IN.to(1.0F).tick(dt, 6.0F);
      float e = Anim.easeOutCubic(WM_IN.v);

      List<String> segs = SEGS;
      segs.clear();
      segs.add(mc.getSession().getUsername());
      segs.add(fpsStr);
      if (!pingStr.isEmpty()) {
         segs.add(pingStr);
      }

      segs.add(timeStr);

      nvgSave(Nvg.ctx);
      nvgGlobalAlpha(Nvg.ctx, e);
      nvgTranslate(Nvg.ctx, 0.0F, -10.0F * (1.0F - e));
      bar(lw, 12.0F, 12.0F, false, "kuro", segs, 0);
      nvgRestore(Nvg.ctx);
   }

   private static int ping(MinecraftClient mc) {
      if (mc.getNetworkHandler() == null) {
         return 0;
      }

      UUID id = mc.player.getUuid();
      PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(id);
      return entry == null ? 0 : entry.getLatency();
   }

   /** Ust vurgu cizgili, ince ayiricili yatay bilgi cubugu. */
   private static void bar(float lw, float x, float y, boolean right, String brand, List<String> segs, int id) {
      float h = 26.0F;
      float pad = 10.0F;
      float brandW = Nvg.textWidth(brand, 14.0F, true);
      float w = pad + brandW;
      for (String s : segs) {
         w += 20.0F + Nvg.textWidth(s, 12.5F, false);
      }

      w += pad;
      if (right) {
         x = lw - x - w;
      }

      bounds(id, x, y, w, h);
      int a1 = Theme.accent();
      int a2 = Theme.accent2();
      Nvg.glow(x, y + 2.0F, w, h, 5.0F, 14.0F, 0x59000000);
      Nvg.rect(x, y, w, h, 5.0F, Theme.HUD_BG);
      Nvg.border(x, y, w, h, 5.0F, 1.0F, 0x12FFFFFF);
      Nvg.rectGradH(x + 4.0F, y, w - 8.0F, 2.0F, 1.0F, a1, a2);

      float cx = x + pad;
      float cy = y + h * 0.5F + 1.0F;
      Nvg.textL(brand, cx, cy, 14.0F, true, a1);
      cx += brandW;
      for (String s : segs) {
         Nvg.line(cx + 10.0F, y + 8.0F, cx + 10.0F, y + h - 6.0F, 1.0F, 0x1FFFFFFF);
         cx += 20.0F;
         cx += Nvg.textL(s, cx, cy, 12.5F, false, Theme.TEXT);
      }
   }

   // ------------------------------------------------------------------ kisayol paneli

   private static void keybinds(float dt) {
      int n = Kuro.NAMES.length;
      float total = 0.0F;
      float maxA = 0.0F;
      float w = 158.0F;
      for (int i = 0; i < n; i++) {
         boolean show = Kuro.on[i] && !Kuro.isAction(i);
         KB_A[i].to(show ? 1.0F : 0.0F).tick(dt, 14.0F);
         total += 22.0F * KB_A[i].v;
         maxA = Math.max(maxA, KB_A[i].v);
         if (KB_A[i].v > 0.0F) {
            w = Math.max(w, 40.0F + Nvg.textWidth(Kuro.NAMES[i], 12.5F, false) + 40.0F);
         }
      }

      if (maxA <= 0.0F) {
         return;
      }

      float x = 12.0F;
      float y = 50.0F;
      float headH = 26.0F;
      float h = headH + total + 6.0F * maxA;
      float e = Anim.easeOutCubic(maxA);
      bounds(1, x, y, w, h);

      nvgSave(Nvg.ctx);
      nvgGlobalAlpha(Nvg.ctx, e);
      Nvg.glow(x, y + 2.0F, w, h, 5.0F, 14.0F, 0x59000000);
      Nvg.rect(x, y, w, h, 5.0F, Theme.HUD_BG);
      Nvg.border(x, y, w, h, 5.0F, 1.0F, 0x12FFFFFF);
      Nvg.rectGradH(x + 4.0F, y, w - 8.0F, 2.0F, 1.0F, Theme.accent(), Theme.accent2());
      Nvg.textL("Kısayollar", x + 10.0F, y + headH * 0.5F + 1.0F, 12.5F, true, Theme.TEXT);

      float ry = y + headH;
      for (int i = 0; i < n; i++) {
         float a = KB_A[i].v;
         if (a <= 0.0F) {
            continue;
         }

         float rh = 22.0F * a;
         nvgSave(Nvg.ctx);
         nvgGlobalAlpha(Nvg.ctx, e * a);
         float cy = ry + rh * 0.5F;
         Nvg.circle(x + 14.0F, cy, 2.5F, Theme.accent());
         Nvg.textL(Kuro.NAMES[i], x + 24.0F, cy, 12.5F, false, Theme.TEXT);
         String key = Keys.name(Kuro.binds[i]);
         Nvg.textR("[" + key + "]", x + w - 10.0F, cy, 11.5F, false, Theme.TEXT_DIM);
         nvgRestore(Nvg.ctx);
         ry += rh;
      }

      nvgRestore(Nvg.ctx);
   }

   // ------------------------------------------------------------------ modul listesi

   private static void moduleList(float lw, float dt) {
      int n = Kuro.NAMES.length;
      Integer[] order = ORDER;
      float[] widths = WIDTHS;
      for (int i = 0; i < n; i++) {
         order[i] = i;
         boolean show = Kuro.on[i] && !Kuro.isAction(i);
         LIST_A[i].to(show ? 1.0F : 0.0F).tick(dt, 13.0F);
         widths[i] = Nvg.textWidth(Kuro.NAMES[i], 13.0F, true);
      }

      java.util.Arrays.sort(order, (a, b) -> Float.compare(WIDTHS[b], WIDTHS[a]));

      float y = 12.0F;
      float listW = 0.0F;
      for (int k = 0; k < n; k++) {
         int i = order[k];
         float a = LIST_A[i].v;
         if (a <= 0.0F) {
            continue;
         }

         float e = Anim.easeOutCubic(a);
         float rh = 22.0F;
         float w = widths[i] + 18.0F;
         float x = lw - 12.0F - w + (1.0F - e) * (w + 16.0F);

         nvgSave(Nvg.ctx);
         nvgGlobalAlpha(Nvg.ctx, e);
         if (Config.hudGlass) {
            Theme.glassBox(x, y, w, rh - 2.0F, 6.0F, Theme.accent());
         } else {
            Nvg.rect(x, y, w, rh - 2.0F, 4.0F, Theme.HUD_BG);
            Nvg.rectGradV(x + w - 3.0F, y + 3.0F, 2.0F, rh - 8.0F, 1.0F, Theme.accent(), Theme.accent2());
         }

         Nvg.textL(Kuro.NAMES[i], x + 8.0F, y + (rh - 2.0F) * 0.5F + 0.5F, 13.0F, true, Theme.TEXT);
         nvgRestore(Nvg.ctx);

         y += rh * a;
         listW = Math.max(listW, w);
      }

      if (listW > 0.0F) {
         bounds(2, lw - 12.0F - listW, 12.0F, listW, y - 12.0F);
      }
   }

   // ------------------------------------------------------------------ kamera gostergesi

   private static void cameraBadge(float lw, float dt) {
      boolean fc = Freecam.active;
      boolean fl = Freelook.active;
      CAM_A.to(fc || fl ? 1.0F : 0.0F).tick(dt, 12.0F);
      if (CAM_A.v <= 0.0F) {
         return;
      }

      String label = fc ? "FREECAM" : "FREELOOK";
      String extra = fc ? "hız " + (Math.round(Config.freecamSpeed * 10.0F) / 10.0F) : null;
      float e = Anim.easeOutCubic(CAM_A.v);
      float tw = Nvg.textWidth(label, 12.5F, true);
      float ew = extra == null ? 0.0F : Nvg.textWidth(extra, 11.5F, false) + 14.0F;
      float w = tw + ew + 26.0F;
      float h = 24.0F;
      float x = (lw - w) * 0.5F;
      float y = 14.0F + (1.0F - e) * -12.0F;

      nvgSave(Nvg.ctx);
      nvgGlobalAlpha(Nvg.ctx, e);
      Nvg.glow(x, y + 2.0F, w, h, 12.0F, 14.0F, 0x59000000);
      Nvg.rect(x, y, w, h, 12.0F, Theme.HUD_BG);
      Nvg.border(x, y, w, h, 12.0F, 1.0F, Theme.a(Theme.accent(), 0.55F));
      Nvg.circle(x + 12.0F, y + h * 0.5F, 3.0F, Theme.accent());
      Nvg.textL(label, x + 22.0F, y + h * 0.5F + 0.5F, 12.5F, true, Theme.TEXT);
      if (extra != null) {
         Nvg.textR(extra, x + w - 10.0F, y + h * 0.5F + 0.5F, 11.5F, false, Theme.TEXT_DIM);
      }

      nvgRestore(Nvg.ctx);
   }

   // ------------------------------------------------------------------ bilgi cubugu

   private static void infoBar(MinecraftClient mc, float lw, float lh) {
      List<String> segs = SEGS;
      segs.clear();
      segs.add(xyzStr);
      segs.add(dirStr);
      segs.add(speedStr);
      bar(lw, 12.0F, lh - 12.0F - 26.0F, true, "XYZ", segs, 3);
   }
}
