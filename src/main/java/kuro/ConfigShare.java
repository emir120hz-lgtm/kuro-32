package kuro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Config paylasma: tum ayarlar + tus atamalari + HUD duzeni tek bir kisa kod olarak disa/ice aktarilir
 * ("KURO1:" + gzip + base64). Ayrica kuro_configs/ klasorunde adlandirilmis profiller tutulur.
 * Ice aktarma yalnizca bilinen ayar alanlarini ve tus atamalarini uygular, baska hicbir sey calistirmaz.
 */
public final class ConfigShare {
   private static final String PREFIX = "KURO1:";
   private static final int MAX_TEXT = 256 * 1024;

   private ConfigShare() {
   }

   // ------------------------------------------------------------------ metin / kod

   public static String exportText() {
      StringBuilder sb = new StringBuilder("#kuro-config 1\n");
      sb.append(Config.dump());
      sb.append("[binds]\n");
      for (int i = 0; i < Kuro.NAMES.length; i++) {
         if (Kuro.binds[i] >= 0) {
            sb.append(Kuro.NAMES[i]).append('=').append(Kuro.binds[i]).append('\n');
         }
      }

      return sb.toString();
   }

   public static String exportCode() {
      try {
         ByteArrayOutputStream bo = new ByteArrayOutputStream();
         try (GZIPOutputStream gz = new GZIPOutputStream(bo)) {
            gz.write(exportText().getBytes(StandardCharsets.UTF_8));
         }

         return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bo.toByteArray());
      } catch (IOException e) {
         return "";
      }
   }

   /** Config kodunu uygular. Basariliysa null, degilse kullaniciya gosterilecek hata metni. */
   public static String importCode(String code) {
      if (code == null) {
         return "Panoda kod yok";
      }

      String c = code.trim().replaceAll("\\s+", "");
      if (!c.startsWith(PREFIX)) {
         return "Geçerli bir Kuro config kodu değil";
      }

      String text;
      try {
         byte[] raw = Base64.getUrlDecoder().decode(c.substring(PREFIX.length()));
         try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(raw))) {
            byte[] out = gz.readNBytes(MAX_TEXT + 1);
            if (out.length > MAX_TEXT) {
               return "Kod çok büyük";
            }

            text = new String(out, StandardCharsets.UTF_8);
         }
      } catch (IllegalArgumentException | IOException e) {
         return "Kod bozuk veya eksik kopyalanmış";
      }

      return applyText(text);
   }

   private static String applyText(String text) {
      List<String> settings = new ArrayList<>();
      List<String> binds = new ArrayList<>();
      boolean inBinds = false;
      for (String line : text.split("\\r?\\n")) {
         String l = line.trim();
         if (l.isEmpty() || l.startsWith("#")) {
            continue;
         }

         if (l.equals("[binds]")) {
            inBinds = true;
         } else {
            (inBinds ? binds : settings).add(l);
         }
      }

      if (settings.isEmpty()) {
         return "Kodda ayar bulunamadı";
      }

      Config.parse(settings);
      Arrays.fill(Kuro.binds, -1);
      for (String b : binds) {
         int eq = b.indexOf('=');
         if (eq <= 0) {
            continue;
         }

         String name = b.substring(0, eq).trim();
         for (int i = 0; i < Kuro.NAMES.length; i++) {
            if (Kuro.NAMES[i].equals(name)) {
               try {
                  Kuro.binds[i] = Integer.parseInt(b.substring(eq + 1).trim());
               } catch (NumberFormatException ignored) {
               }
            }
         }
      }

      Config.save();
      Kuro.saveBinds();
      ClearRender.rebuild();
      return null;
   }

   // ------------------------------------------------------------------ profiller

   private static Path dir() {
      return FabricLoader.getInstance().getGameDir().resolve("kuro_configs");
   }

   /** Profil adini dosya adina uygun hale getirir (harf, rakam, _ ve -; en fazla 24 karakter). */
   public static String sanitize(String name) {
      String s = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9_-]", "");
      return s.length() > 24 ? s.substring(0, 24) : s;
   }

   public static List<String> profiles() {
      List<String> out = new ArrayList<>();
      Path d = dir();
      if (!Files.isDirectory(d)) {
         return out;
      }

      try (Stream<Path> st = Files.list(d)) {
         st.map(p -> p.getFileName().toString())
            .filter(n -> n.endsWith(".txt"))
            .map(n -> n.substring(0, n.length() - 4))
            .filter(n -> !n.isEmpty() && n.equals(sanitize(n)))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .forEach(out::add);
      } catch (IOException ignored) {
      }

      return out;
   }

   public static boolean saveProfile(String name) {
      String n = sanitize(name);
      if (n.isEmpty()) {
         return false;
      }

      try {
         Files.createDirectories(dir());
         Files.writeString(dir().resolve(n + ".txt"), exportText());
         return true;
      } catch (IOException e) {
         return false;
      }
   }

   public static String loadProfile(String name) {
      String n = sanitize(name);
      if (n.isEmpty()) {
         return "Geçersiz profil adı";
      }

      try {
         return applyText(Files.readString(dir().resolve(n + ".txt")));
      } catch (IOException e) {
         return "Profil okunamadı";
      }
   }

   public static boolean deleteProfile(String name) {
      String n = sanitize(name);
      try {
         return !n.isEmpty() && Files.deleteIfExists(dir().resolve(n + ".txt"));
      } catch (IOException e) {
         return false;
      }
   }
}
