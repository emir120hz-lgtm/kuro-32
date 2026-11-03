package kuro;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * SpotifyHUD veri kaynagi: yerel Spotify masaustu uygulamasindan calan sarkiyi okur (hesap girisi / API anahtari yok).
 * Windows: Spotify penceresinin basligi ("Sanatci - Sarki"). macOS: AppleScript. Linux: playerctl (MPRIS).
 * Sorgu arka plan thread'inde yapilir, oyunu yavaslatmaz.
 */
public final class Spotify {
   public static volatile boolean running;   // Spotify acik mi
   public static volatile boolean playing;   // su an caliyor mu
   public static volatile String title = "";
   public static volatile String artist = "";
   public static volatile float progress = -1.0F; // 0..1, bilinmiyorsa -1

   private static volatile boolean busy;
   private static long lastPoll;
   private static final String OS = System.getProperty("os.name", "").toLowerCase();

   private Spotify() {
   }

   /** Modul aciksa her tick cagrilir; araliklarla arka planda sorgu baslatir. */
   public static void tick() {
      long now = System.currentTimeMillis();
      long every = OS.contains("win") ? 3000L : 1500L;
      if (busy || now - lastPoll < every) {
         return;
      }

      lastPoll = now;
      busy = true;
      Thread t = new Thread(() -> {
         try {
            poll();
         } catch (Exception ignored) {
         } finally {
            busy = false;
         }
      }, "kuro-spotify");
      t.setDaemon(true);
      t.start();
   }

   private static void poll() throws Exception {
      if (OS.contains("win")) {
         pollWindows();
      } else if (OS.contains("mac")) {
         pollMac();
      } else {
         pollLinux();
      }
   }

   private static void pollWindows() throws Exception {
      String ps = "[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
         + "$p=Get-Process Spotify -ErrorAction SilentlyContinue;"
         + "if(-not $p){'NONE'}else{$w=$p|Where-Object{$_.MainWindowTitle}|Select-Object -First 1;if($w){'T:'+$w.MainWindowTitle}else{'T:'}}";
      String enc = Base64.getEncoder().encodeToString(ps.getBytes(StandardCharsets.UTF_16LE));
      String out = run("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", enc);
      if (out == null || out.startsWith("NONE") || out.isBlank()) {
         running = false;
         playing = false;
         return;
      }

      running = true;
      String t = out.startsWith("T:") ? out.substring(2).trim() : out.trim();
      if (t.isEmpty() || t.equals("Spotify") || t.startsWith("Spotify ") || t.equals("Advertisement")) {
         playing = false; // duraklatildi / reklam: son sarki bilgisi kalir
         return;
      }

      int i = t.indexOf(" - ");
      if (i > 0) {
         artist = t.substring(0, i).trim();
         title = t.substring(i + 3).trim();
      } else {
         artist = "";
         title = t;
      }

      playing = true;
      progress = -1.0F; // pencere basligindan sure okunamaz
   }

   private static void pollMac() throws Exception {
      String script = "if application \"Spotify\" is running then\n"
         + "tell application \"Spotify\"\n"
         + "if player state is stopped then return \"STOP\"\n"
         + "return (artist of current track) & \"|||\" & (name of current track) & \"|||\" & (player state as string) & \"|||\" & (duration of current track) & \"|||\" & (player position)\n"
         + "end tell\nelse\nreturn \"NONE\"\nend if";
      String out = run("osascript", "-e", script);
      parseDelimited(out, 1000.0, 1.0);
   }

   private static void pollLinux() throws Exception {
      String out = run("playerctl", "-p", "spotify", "metadata", "--format",
         "{{artist}}|||{{title}}|||{{status}}|||{{mpris:length}}|||{{position}}");
      parseDelimited(out, 1_000_000.0, 1_000_000.0);
   }

   /** "sanatci|||sarki|||durum|||uzunluk|||konum" bicimini isler (birimler: uzunluk = lenDiv/sn, konum = posDiv/sn). */
   private static void parseDelimited(String out, double lenDiv, double posDiv) {
      if (out == null || out.isBlank() || out.startsWith("NONE") || out.startsWith("STOP") || !out.contains("|||")) {
         running = out != null && out.startsWith("STOP");
         playing = false;
         return;
      }

      String[] p = out.trim().split("\\|\\|\\|", -1);
      if (p.length < 3) {
         running = false;
         playing = false;
         return;
      }

      running = true;
      artist = p[0].trim();
      title = p[1].trim();
      playing = p[2].trim().equalsIgnoreCase("playing");
      progress = -1.0F;
      if (p.length >= 5) {
         try {
            double len = Double.parseDouble(p[3].trim().replace(',', '.')) / lenDiv;
            double pos = Double.parseDouble(p[4].trim().replace(',', '.')) / posDiv;
            if (len > 0.0) {
               progress = (float)Math.max(0.0, Math.min(1.0, pos / len));
            }
         } catch (NumberFormatException ignored) {
         }
      }
   }

   private static String run(String... cmd) {
      try {
         Process pr = new ProcessBuilder(cmd).redirectErrorStream(true).start();
         ByteArrayOutputStream bo = new ByteArrayOutputStream();
         try (InputStream in = pr.getInputStream()) {
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0 && bo.size() < 8192) {
               bo.write(buf, 0, n);
            }
         }

         if (!pr.waitFor(5, TimeUnit.SECONDS)) {
            pr.destroyForcibly();
            return null;
         }

         return bo.toString(StandardCharsets.UTF_8).trim();
      } catch (Exception e) {
         return null; // komut yok (playerctl kurulu degil vb.)
      }
   }
}
