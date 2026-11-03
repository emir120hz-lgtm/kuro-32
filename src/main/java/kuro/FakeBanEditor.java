package kuro;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * FakeBan duzenleyici: sahte ekranin baslik / sebep / tarih / itiraz satirini yazdigin
 * basit bir ayar ekrani. Degerler Config'e (kuro_settings.txt) kaydedilir. Tamamen yerel ve kozmetiktir.
 */
public final class FakeBanEditor extends Screen {
   private final Screen parent;
   private TextFieldWidget title;
   private TextFieldWidget reason;
   private TextFieldWidget date;
   private TextFieldWidget cause;
   private TextFieldWidget expiry;
   private TextFieldWidget note;
   private TextFieldWidget link;
   private int fieldX;
   private int fieldY0;
   private static final int ROW = 30;

   public FakeBanEditor(Screen parent) {
      super(Text.literal("FakeBan"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      int w = Math.min(340, this.width - 40);
      fieldX = this.width / 2 - w / 2;
      fieldY0 = Math.max(30, this.height / 2 - 111);

      title = field(0, w, Config.fbanTitle, "Bağlantı koptu");
      reason = field(1, w, Config.fbanReason, "Sunucudan yasaklandınız");
      cause = field(2, w, Config.fbanCause, "Hile Şüphesi");
      date = field(3, w, Config.fbanDate, "boş = bugünün tarihi (gg/aa/yyyy)");
      expiry = field(4, w, Config.fbanExpiry, "10 dakika");
      note = field(5, w, Config.fbanNote, "Ban açtırma başvurusu için");
      link = field(6, w, Config.fbanLink, "boş = alt satır gösterilmez (örn. kendi YouTube kanalın)");

      int by = fieldY0 + ROW * 7 + 4;
      int bw = (w - 8) / 2;
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Göster"), b -> {
         apply();
         Config.fbanMode = 0;
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
      Config.fbanTitle = title.getText();
      Config.fbanReason = reason.getText();
      Config.fbanCause = cause.getText();
      Config.fbanDate = date.getText();
      Config.fbanExpiry = expiry.getText();
      Config.fbanNote = note.getText();
      Config.fbanLink = link.getText();
      Config.save();
   }

   @Override
   public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
      super.render(ctx, mouseX, mouseY, delta);
      ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("FakeBan ayarları (Göster: sunucudan çıkıp ana menüde gösterir)"), this.width / 2, fieldY0 - 22, 0xFFFFFF);
      String[] labels = {"Başlık", "Ana satır (kırmızı)", "Sebep", "Banlanma tarihi", "Açılma tarihi", "Alt satır başlığı (sarı)", "Alt satır (kırmızı)"};
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
