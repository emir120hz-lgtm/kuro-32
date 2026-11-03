package kuro.ui;

import kuro.Kuro;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * HUD duzenleyici: filigran, modul listesi, radar, Spotify gibi tum HUD ogelerini fareyle surukleyip istedigin yere koyarsin.
 * Sag tik: ogeyi sifirla, R: hepsini sifirla, Esc: kaydet ve kapat. Konumlar kuro_settings.txt'ye kaydedilir.
 */
public final class HudEditor extends Screen {
   private static final float SNAP = 6.0F;
   private static final float MARGIN = 8.0F;

   private final Screen parent;
   private int drag = -1;
   private float startMx;
   private float startMy;
   private float startOx;
   private float startOy;
   private float uiScale = 1.0F;
   private float lw;
   private float lh;
   private long last = System.nanoTime();

   public HudEditor(Screen parent) {
      super(Text.literal("HUD"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      Hud.editing = true;
   }

   @Override
   public void removed() {
      Hud.editing = false;
      Hud.saveLayout();
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
   }

   @Override
   public void close() {
      this.client.setScreen(parent);
   }

   private float mouseX() {
      Window w = Kuro.mc.getWindow();
      return (float)(Kuro.mc.mouse.getX() * ((double)w.getFramebufferWidth() / (double)w.getWidth()) / uiScale);
   }

   private float mouseY() {
      Window w = Kuro.mc.getWindow();
      return (float)(Kuro.mc.mouse.getY() * ((double)w.getFramebufferHeight() / (double)w.getHeight()) / uiScale);
   }

   private int pick(float mx, float my) {
      for (int i = Hud.EL_COUNT - 1; i >= 0; i--) {
         float[] r = Hud.rect(i, lw, lh);
         if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) {
            return i;
         }
      }

      return -1;
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      Window win = Kuro.mc.getWindow();
      int fw = win.getFramebufferWidth();
      int fh = win.getFramebufferHeight();
      if (fw <= 0 || fh <= 0 || !Nvg.ensure()) {
         context.drawCenteredTextWithShadow(this.textRenderer, "HUD düzenleyici: sürükle, Esc ile kapat", this.width / 2, 20, 0xFFFFFF);
         return;
      }

      context.draw();
      long now = System.nanoTime();
      float dt = Math.min(0.1F, (now - last) / 1.0E9F);
      last = now;
      uiScale = Theme.scale(fh);
      lw = fw / uiScale;
      lh = fh / uiScale;
      Theme.update(dt);

      Nvg.begin(lw, lh, uiScale);
      try {
         draw();
      } finally {
         Nvg.end();
      }
   }

   private void draw() {
      int accent = Theme.accent();
      float mx = mouseX();
      float my = mouseY();
      int hover = drag >= 0 ? drag : pick(mx, my);

      Nvg.rect(0.0F, 0.0F, lw, lh, 0.0F, Theme.alpha(0x000000, 0.30F));
      Nvg.line(lw * 0.5F, 0.0F, lw * 0.5F, lh, 1.0F, 0x10FFFFFF);
      Nvg.line(0.0F, lh * 0.5F, lw, lh * 0.5F, 1.0F, 0x10FFFFFF);

      for (int i = 0; i < Hud.EL_COUNT; i++) {
         float[] r = Hud.rect(i, lw, lh);
         boolean active = i == drag;
         boolean hov = i == hover;
         Nvg.rect(r[0], r[1], r[2], r[3], 5.0F, Theme.a(accent, active ? 0.28F : hov ? 0.18F : 0.08F));
         Nvg.border(r[0], r[1], r[2], r[3], 5.0F, active ? 1.6F : 1.0F, Theme.a(accent, active ? 1.0F : hov ? 0.9F : 0.55F));
         float ly = r[1] >= 14.0F ? r[1] - 5.0F : r[1] + r[3] + 11.0F;
         Nvg.textL(Hud.EL_NAMES[i], r[0] + 2.0F, ly, 10.0F, true, hov ? Theme.TEXT : accent);
      }

      Nvg.textC("HUD düzenleyici", lw * 0.5F, 26.0F, 17.0F, true, accent);
      Nvg.textC("Sürükle: taşı  ·  Sağ tık: öğeyi sıfırla  ·  R: hepsini sıfırla  ·  Esc: kaydet ve çık", lw * 0.5F, 44.0F, 10.5F, false, Theme.TEXT_DIM);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      float mx = mouseX();
      float my = mouseY();
      int i = pick(mx, my);
      if (i < 0) {
         return true;
      }

      if (button == 0) {
         drag = i;
         startMx = mx;
         startMy = my;
         startOx = Hud.offX(i);
         startOy = Hud.offY(i);
      } else if (button == 1) {
         Hud.setOff(i, 0.0F, 0.0F);
         Hud.saveLayout();
      }

      return true;
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (drag < 0) {
         return true;
      }

      float[] base = Hud.baseRect(drag, lw, lh);
      float w = base[2];
      float h = base[3];
      float nx = base[0] + startOx + (mouseX() - startMx);
      float ny = base[1] + startOy + (mouseY() - startMy);

      // kenara / ortaya yapis
      if (Math.abs(nx - MARGIN) < SNAP) {
         nx = MARGIN;
      } else if (Math.abs(nx + w - (lw - MARGIN)) < SNAP) {
         nx = lw - MARGIN - w;
      } else if (Math.abs(nx + w * 0.5F - lw * 0.5F) < SNAP) {
         nx = lw * 0.5F - w * 0.5F;
      }

      if (Math.abs(ny - MARGIN) < SNAP) {
         ny = MARGIN;
      } else if (Math.abs(ny + h - (lh - MARGIN)) < SNAP) {
         ny = lh - MARGIN - h;
      } else if (Math.abs(ny + h * 0.5F - lh * 0.5F) < SNAP) {
         ny = lh * 0.5F - h * 0.5F;
      }

      // ekranin disina cikmasin
      nx = Math.max(0.0F, Math.min(lw - w, nx));
      ny = Math.max(0.0F, Math.min(lh - h, ny));
      Hud.setOff(drag, nx - base[0], ny - base[1]);
      return true;
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (drag >= 0) {
         drag = -1;
         Hud.saveLayout();
      }

      return true;
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == GLFW.GLFW_KEY_R) {
         Hud.resetAll();
         Hud.saveLayout();
         return true;
      }

      return super.keyPressed(keyCode, scanCode, modifiers);
   }
}
