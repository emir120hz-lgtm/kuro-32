package kuro.mixin;

import kuro.ScoreboardEdit;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FakeScoreboard: sidebar cizilirken bayrak kaldir + baslik metnini degistir. kuro.extra.mixins.json (require=0). */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
   // CustomCrosshair: vanilla artisini gizle (kendi imlecimiz Fx.hud'da cizilir)
   @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
   private void kuro$noCross(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter, CallbackInfo ci) {
      if (kuro.Kuro.on[kuro.Kuro.CROSSHAIR]) {
         ci.cancel();
      }
   }

   @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), require = 0)
   private void kuro$sbStart(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
      ScoreboardEdit.inSidebar = true;
      ScoreboardEdit.noteSidebar();
   }

   @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("RETURN"), require = 0)
   private void kuro$sbEnd(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
      ScoreboardEdit.inSidebar = false;
   }

   @Redirect(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/ScoreboardObjective;getDisplayName()Lnet/minecraft/text/Text;"), require = 0)
   private Text kuro$sbTitle(ScoreboardObjective objective) {
      Text t = objective.getDisplayName();
      return ScoreboardEdit.active() ? ScoreboardEdit.apply(t) : t;
   }
}
