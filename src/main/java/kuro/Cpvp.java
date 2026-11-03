package kuro;

import kuro.mixin.MinecraftClientAccessor;
import kuro.ui.Toasts;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** CPvP yardimcilari: FastXP, WTap, AutoReplenish, Surround. */
public final class Cpvp {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   private Cpvp() {
   }

   // ------------------------------------------------------------------ FastXP
   /** XP sisesi elindeyken sag tik bekleme suresini sifirlar (hizli atis). */
   public static void fastXp() {
      ClientPlayerEntity p = mc.player;
      if (p == null) {
         return;
      }

      if (p.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE) || p.getOffHandStack().isOf(Items.EXPERIENCE_BOTTLE)) {
         ((MinecraftClientAccessor)mc).kuro$setItemUseCooldown(0);
      }
   }

   // ------------------------------------------------------------------ WTap
   /** Vurustan hemen once sprint'i sifirla: sunucu "yeni kosu" gordugu icin ekstra knockback verir. */
   public static void wtap() {
      ClientPlayerEntity p = mc.player;
      if (p == null || mc.getNetworkHandler() == null || !p.isSprinting()) {
         return;
      }

      mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
      mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_SPRINTING));
   }

   // ------------------------------------------------------------------ AutoReplenish
   private static int repCd;

   /** Hotbar'daki bir yigin azalinca envanterdeki ayni esyadan tamamlar (envanter ekrani kapaliyken). */
   public static void replenish() {
      ClientPlayerEntity p = mc.player;
      if (p == null || mc.world == null || mc.currentScreen != null || mc.interactionManager == null) {
         return;
      }

      if (repCd > 0) {
         repCd--;
         return;
      }

      PlayerInventory inv = p.getInventory();
      for (int h = 0; h < 9; h++) {
         ItemStack hs = inv.getStack(h);
         if (hs.isEmpty() || hs.getMaxCount() <= 1 || hs.getCount() > Math.min(16, hs.getMaxCount() / 2)) {
            continue;
         }

         for (int i = 9; i < 36; i++) {
            ItemStack is = inv.getStack(i);
            if (is.isEmpty() || !ItemStack.areItemsAndComponentsEqual(hs, is)) {
               continue;
            }

            int sync = p.playerScreenHandler.syncId;
            // ekran-handler indeksleri: ana envanter 9-35 ayni, hotbar 36-44
            mc.interactionManager.clickSlot(sync, i, 0, SlotActionType.PICKUP, p);
            mc.interactionManager.clickSlot(sync, 36 + h, 0, SlotActionType.PICKUP, p);
            if (!p.playerScreenHandler.getCursorStack().isEmpty()) {
               mc.interactionManager.clickSlot(sync, i, 0, SlotActionType.PICKUP, p);
            }

            repCd = 6;
            return;
         }
      }

      repCd = 4;
   }

   // ------------------------------------------------------------------ Surround
   private static BlockPos anchor;
   private static int srCd;
   private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

   public static void surroundOff() {
      anchor = null;
   }

   /** Ayaklarinin dort yanina obsidyen koyar; bulundugun bloktan ayrilirsan kendini kapatir. */
   public static void surround() {
      ClientPlayerEntity p = mc.player;
      if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) {
         return;
      }

      BlockPos here = p.getBlockPos();
      if (anchor == null) {
         anchor = here;
      } else if (!anchor.equals(here)) {
         Kuro.on[Kuro.SURROUND] = false;
         anchor = null;
         Toasts.push(Kuro.NAMES[Kuro.SURROUND], false, "Yerinden ayrıldın, kapandı");
         return;
      }

      if (srCd > 0) {
         srCd--;
         return;
      }

      PlayerInventory inv = p.getInventory();
      int slot = -1;
      for (int i = 0; i < 9; i++) {
         if (inv.getStack(i).isOf(Items.OBSIDIAN)) {
            slot = i;
            break;
         }
      }

      if (slot < 0) {
         return;
      }

      int prev = inv.selectedSlot;
      int placed = 0;
      for (Direction d : SIDES) {
         if (placed >= 2) {
            break;
         }

         BlockPos bp = here.offset(d);
         BlockState st = mc.world.getBlockState(bp);
         if (!st.isReplaceable() || !mc.world.getOtherEntities(p, new Box(bp)).isEmpty()) {
            continue;
         }

         BlockPos below = bp.down();
         BlockState bs = mc.world.getBlockState(below);
         if (bs.isAir() || !bs.isFullCube(mc.world, below)) {
            continue;
         }

         if (inv.selectedSlot != slot) {
            inv.selectedSlot = slot;
         }

         Vec3d hit = new Vec3d(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5);
         BlockHitResult bhr = new BlockHitResult(hit, Direction.UP, below, false);
         mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, bhr);
         p.swingHand(Hand.MAIN_HAND);
         placed++;
      }

      if (inv.selectedSlot != prev) {
         inv.selectedSlot = prev;
      }

      srCd = placed > 0 ? 1 : 2;
   }
}
