package kuro.ui;

import java.text.Normalizer;
import java.util.Locale;
import kuro.Kuro;

/** Menu arama yardimcisi: Turkce karakterleri / buyuk-kucuk harfi yok sayar, ad ve aciklamada arar. */
final class Search {
   private Search() {
   }

   static String norm(String s) {
      if (s == null) {
         return "";
      }

      String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
      return n.toLowerCase(Locale.ROOT).replace('ı', 'i').trim();
   }

   /** Metin (zaten normalize edilmis) tum kelimeleri iceriyor mu. */
   private static boolean hasAll(String hay, String[] toks) {
      for (String t : toks) {
         if (!hay.contains(t)) {
            return false;
         }
      }

      return true;
   }

   /** 0 = ad ile baslar, 1 = adda gecer, 2 = aciklamada gecer, -1 = eslesmez. */
   static int rank(String name, String desc, String query) {
      String q = norm(query);
      if (q.isEmpty()) {
         return -1;
      }

      String[] toks = q.split("\\s+");
      String n = norm(name);
      if (n.startsWith(q)) {
         return 0;
      }

      if (hasAll(n, toks)) {
         return 1;
      }

      if (desc != null && !desc.isEmpty() && hasAll(n + " " + norm(desc), toks)) {
         return 2;
      }

      return -1;
   }

   static int rankMod(int id, String query) {
      return rank(Kuro.NAMES[id], id < Kuro.DESCS.length ? Kuro.DESCS[id] : "", query);
   }
}
