package kuro;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * FakeBan / DonutSMP duzenleyici: baslik, sebep (kirmizi), tarih etiketi, tarih, itiraz satiri ve link
 * (kendi YouTube kanalin gibi) alanlarini duzenler. Degerler Config'e kaydedilir. Ban ID yoktur.
 */
public final class FakeBanDonutEditor extends Screen {
   private final Screen parent;
   private TextFieldWidget title;
   private TextFieldWidget reason;
   private TextFieldWidget dateLabel;
   private TextFieldWidget date;
   private TextFieldWidget appeal;
   private TextFieldWidget link;
   private int fieldX;
   private int fieldY0;
   private static final int ROW = 30;

   public FakeBanDonutEditor(Screen parent) {
      super(Text.literal("FakeBan DonutSMP"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      int w = Math.min(340, this.width - 40);
      fieldX = this.width / 2 - w / 2;
      fieldY0 = Math.max(30, this.height / 2 - 96);

      title = field(0, w, Config.fdTitle, "Failed to connect to the server");
      reason = field(1, w, Config.fdReason, "You are banned for botting DonutSMP.");
      dateLabel = field(2, w, Config.fdDateLabel, "Date:");
      date = field(3, w, Config.fdDate, "boş = bugünün tarihi (gg/aa/yyyy)");
      appeal = field(4, w, Config.fdAppeal, "boş = itiraz satırı gösterilmez");
      link = field(5, w, Config.fdLink, "kendi YouTube kanalın (boş = gösterilmez)");

      int by = fieldY0 + ROW * 6 + 4;
      int bw = (w - 8) / 2;
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Göster"), b -> {
         apply();
         Config.fbanMode = 1;
         Config.save();
         FakeBan.show();
      }).dimensions(fieldX, by, bw, 20).build());
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Kaydet ve kapat"), b -> close())
         .dimensions(fieldX + bw + 8, by, bw, 20).build());
   }

   private TextFieldWidget field(int row, int w, String value, String hint) {
      TextFieldWidget f = new TextFieldWidget(this.textRenderer, fieldX, fieldY0 + row * ROW + 12, w, 20, Text.literal(hint));
      f.setMaxLength(200);
      f.setPlaceholder(Text.literal(hint));
      f.setText(value == null ? "" : value);
      this.addDrawableChild(f);
      return f;
   }

   private void apply() {
      Config.fdTitle = title.getText();
      Config.fdReason = reason.getText();
      Config.fdDateLabel = dateLabel.getText();
      Config.fdDate = date.getText();
      Config.fdAppeal = appeal.getText();
      Config.fdLink = link.getText();
      Config.save();
   }

   @Override
   public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
      super.render(ctx, mouseX, mouseY, delta);
      ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("DonutSMP ayarları (Göster: sunucudan çıkıp ana menüde gösterir)"), this.width / 2, fieldY0 - 22, 0xFFFFFF);
      String[] labels = {"Başlık", "Sebep (kırmızı)", "Tarih etiketi (gri)", "Tarih (beyaz)", "İtiraz satırı (gri)", "Link / YouTube (beyaz)"};
      for (int i = 0; i < labels.length; i++) {
         ctx.drawTextWithShadow(this.textRenderer, labels[i], fieldX, fieldY0 + i * ROW + 1, 0xAAAAAA);
      }
   }

   @Override
   public void removed() {
      apply();
   }

   @Override
   public void close() {
      this.client.setScreen(parent);
   }
}
