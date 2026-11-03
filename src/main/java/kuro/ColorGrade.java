package kuro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * ColorGrading (basit surum): Anubis'in shader tabanli ColorGrading'inin yaklasigi.
 * Gercek post-process yerine ekran uzerine yari saydam katmanlar cizer:
 * parlaklik (beyaz/siyah katman), sicaklik (turuncu/mavi katman) ve vinyet (kenar karartma).
 * Shader/pipeline kullanmadigi icin 1.21.4'te guvenle calisir; HUD da hafifce etkilenir.
 */
public final class ColorGrade {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private static final int RINGS = 16;

   private ColorGrade() {
   }

   public static void hud(DrawContext dc, RenderTickCounter tc) {
      if (!Kuro.on[Kuro.COLORGRADE] || mc.player == null || mc.world == null) {
         return;
      }

      int w = dc.getScaledWindowWidth();
      int h = dc.getScaledWindowHeight();

      // Parlaklik: >1 beyaz, <1 siyah katman
      float e = Config.cgExposure - 1.0F;
      if (Math.abs(e) > 0.01F) {
         int a = alpha(Math.min(0.45F, Math.abs(e) * 0.55F));
         dc.fill(0, 0, w, h, (a << 24) | (e > 0.0F ? 0xFFFFFF : 0x000000));
      }

      // Sicaklik: + turuncu, - mavi
      float wm = Config.cgWarmth;
      if (Math.abs(wm) > 0.01F) {
         int a = alpha(Math.min(0.2F, Math.abs(wm) * 0.14F));
         dc.fill(0, 0, w, h, (a << 24) | (wm > 0.0F ? 0xFF8C28 : 0x3C8CFF));
      }

      // Vinyet: kenarlardan merkeze dogru azalan halkalar
      float vs = Config.cgVignette;
      if (vs > 0.01F) {
         int depth = (int)(Math.min(w, h) * 0.5F * Config.cgVigSize);
         int t = Math.max(1, depth / RINGS);
         for (int i = 0; i < RINGS; i++) {
            float f = 1.0F - (float)i / RINGS;
            int a = alpha(vs * 0.85F * f * f);
            if (a <= 0) {
               continue;
            }

            int col = a << 24;
            int x1 = i * t;
            int y1 = i * t;
            int x2 = w - i * t;
            int y2 = h - i * t;
            if (x2 - x1 <= 2 * t || y2 - y1 <= 2 * t) {
               break;
            }

            dc.fill(x1, y1, x2, y1 + t, col);
            dc.fill(x1, y2 - t, x2, y2, col);
            dc.fill(x1, y1 + t, x1 + t, y2 - t, col);
            dc.fill(x2 - t, y1 + t, x2, y2 - t, col);
         }
      }
   }

   private static int alpha(float f) {
      return Math.max(0, Math.min(255, (int)(f * 255.0F)));
   }
}
