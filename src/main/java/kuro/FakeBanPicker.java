package kuro;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * FakeBan mod secici: FakeBan'a sag tiklayinca acilir. Alt alta iki kutu: ChickenSMP ve DonutSMP.
 * Birine tiklayinca o mod secilir (tusa basinca bu mod gosterilir) ve o modun metin duzenleyicisi acilir.
 */
public final class FakeBanPicker extends Screen {
   private final Screen parent;

   public FakeBanPicker(Screen parent) {
      super(Text.literal("FakeBan"));
      this.parent = parent;
   }

   /** Modun duzenleme ekrani (0 = ChickenSMP, 1 = DonutSMP). */
   public static Screen editorFor(int mode, Screen parent) {
      return mode == 1 ? new FakeBanDonutEditor(parent) : new FakeBanEditor(parent);
   }

   @Override
   protected void init() {
      int w = Math.min(220, this.width - 40);
      int x = this.width / 2 - w / 2;
      int y = this.height / 2 - 30;
      this.addDrawableChild(ButtonWidget.builder(label("ChickenSMP", 0), b -> choose(0)).dimensions(x, y, w, 24).build());
      this.addDrawableChild(ButtonWidget.builder(label("DonutSMP", 1), b -> choose(1)).dimensions(x, y + 30, w, 24).build());
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Geri"), b -> close()).dimensions(x, y + 66, w, 20).build());
   }

   private static Text label(String name, int mode) {
      boolean sel = Config.fbanMode == mode;
      return sel ? Text.literal("> " + name + " <").formatted(Formatting.GREEN) : Text.literal(name);
   }

   private void choose(int mode) {
      Config.fbanMode = mode;
      Config.save();
      this.client.setScreen(editorFor(mode, parent));
   }

   @Override
   public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
      super.render(ctx, mouseX, mouseY, delta);
      ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("FakeBan modu seç"), this.width / 2, this.height / 2 - 48, 0xFFFFFF);
      ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Tuşa basınca seçili mod gösterilir"), this.width / 2, this.height / 2 + 94, 0xAAAAAA);
   }

   @Override
   public void close() {
      this.client.setScreen(parent);
   }
}
