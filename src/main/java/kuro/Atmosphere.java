package kuro;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * AtmosphereSky: Anubis'in shader gokyuzu (Space / Northern Lights / Ringed Planet / Black Hole).
 * Anubis'in fragment shader'i neredeyse oldugu gibi kullanilir; Minecraft'in shader sistemine
 * girmeden kendi GL programiyla, tam ekran ucgen olarak WorldRenderEvents.LAST'ta cizilir.
 * Derinlik testi sayesinde sadece bos (gokyuzu) piksellere yazar; arazi, bulut ve esyalar uste kalir.
 *
 * Optimizasyon: "Cozunurluk" 1'in altindaysa gokyuzu kucuk bir FBO'ya cizilip derinlik testiyle ekrana
 * buyutulerek basilir (agir modlarda GPU yuku cozunurlukle orantili duser). 1'de FBO kullanilmaz.
 */
public final class Atmosphere {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   // nightZenith, nightHorizon, twilight, auroraLow, auroraHigh, milkyWay  (Anubis temalari: Borealis, Nebula, Anubis, Frost, Crimson)
   private static final int[][] THEMES = {
      {198414, 729648, 13073302, 3080090, 9063935, 13163263},
      {459791, 2233916, 14053304, 16732120, 5991679, 15124735},
      {657157, 2890512, 15239498, 16757306, 16730670, 16769200},
      {264724, 1454154, 11057384, 8382719, 15135999, 14544639},
      {721926, 3018260, 15227490, 16724821, 16751164, 16765640}
   };

   private static int prog;
   private static int blit;
   private static int vao;
   private static boolean failed;
   private static int uLook, uAmounts, uLow, uHigh, uBand, uZenith, uHorizon, uInvView, uTanHalf, uBlitTex;

   private static int fbo;
   private static int tex;
   private static int fboW;
   private static int fboH;
   private static boolean lowFailed;

   private static float time;
   private static long lastNanos;
   private static final float[] inv = new float[9];
   private static final Matrix3f rot = new Matrix3f();
   private static final int[] vp = new int[4];
   private static final int[] vp0 = new int[4];
   private static float dayAmt;
   private static int uExtra;
   private static boolean errLogged;
   private static boolean okLogged;

   private Atmosphere() {
   }

   public static void render(WorldRenderContext ctx) {
      if (!Kuro.on[Kuro.ATMOSPHERE] || failed || mc.world == null || mc.player == null) {
         return;
      }

      if (mc.world.getRegistryKey() != World.OVERWORLD) {
         return;
      }

      MatrixStack ms = ctx.matrixStack();
      Matrix4f proj = ctx.projectionMatrix();
      if (ms == null || proj == null) {
         return;
      }

      long now = System.nanoTime();
      float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0E9F, 0.1F);
      lastNanos = now;
      time = (time + dt * Config.skSpeed) % 4096.0F;

      float vis = visibility(ctx.tickCounter().getTickDelta(false));
      if (vis < 0.002F) {
         return;
      }

      if (!init()) {
         return;
      }

      int mode = Math.max(0, Math.min(3, Math.round(Config.skMode)));
      int[] th = THEMES[Math.max(0, Math.min(THEMES.length - 1, Math.round(Config.skTheme)))];
      rot.set(ms.peek().getPositionMatrix()).transpose().get(inv);
      float tanY = 1.0F / proj.m11();
      float tanX = 1.0F / proj.m00();

      float scale = Math.max(0.25F, Math.min(1.0F, Config.skScale));
      boolean low = scale < 0.99F && !lowFailed;

      // --- GL durumunu kaydet (MC'nin GlStateManager onbellegi tutarli kalsin diye ayni degerlere geri doner) ---
      int prevProg = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
      int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
      boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
      int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
      boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
      boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
      int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
      int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
      int srcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
      int dstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
      boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
      int prevFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
      GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp0);
      int mainFbo = prevFbo;
      int prevTex = 0;
      int prevUnit = 0;
      boolean usedLow = false;
      try {
         // Cizimin kesinlikle ana framebuffer'a (derinlik dokusuyla birlikte) gitmesini sagla
         GL11.glGetError();
         mc.getFramebuffer().beginWrite(true);
         mainFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
         GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
         if (low) {
            prevUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            if (ensureTarget(scale, mainFbo, prevTex)) {
               usedLow = true;
            } else {
               GL13.glActiveTexture(prevUnit);
            }
         }

         GL11.glDisable(GL11.GL_CULL_FACE);
         GL30.glBindVertexArray(vao);
         if (usedLow) {
            // 1) kucuk hedefe gokyuzunu ciz (karistirma ve derinlik yok; alfa = gorunurluk)
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
            GL11.glViewport(0, 0, fboW, fboH);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL20.glUseProgram(prog);
            uniforms(mode, vis, th, tanX, tanY, (float)fboW / Math.max(1, vp[2]));
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

            // 2) ana hedefe, sadece bos piksellere, buyutup bas
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mainFbo);
            GL11.glViewport(vp[0], vp[1], vp[2], vp[3]);
            setupBlend();
            GL20.glUseProgram(blit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL20.glUniform1i(uBlitTex, 0);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
         } else {
            setupBlend();
            GL20.glUseProgram(prog);
            uniforms(mode, vis, th, tanX, tanY, 1.0F);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
         }

         int err = GL11.glGetError();
         if (err != 0 && !errLogged) {
            errLogged = true;
            Kuro.LOGGER.warn("[kuro] AtmosphereSky GL hatasi 0x{} (mode={}, low={}, fb={}, vp={}x{})", Integer.toHexString(err), mode, usedLow, mainFbo, vp[2], vp[3]);
         } else if (err == 0 && !okLogged) {
            okLogged = true;
            Kuro.LOGGER.info("[kuro] AtmosphereSky ilk kare cizildi (mode={}, low={}, fb={}, vp={}x{}, vis={})", mode, usedLow, mainFbo, vp[2], vp[3], vis);
         }
      } catch (RuntimeException ex) {
         failed = true;
         Kuro.LOGGER.warn("[kuro] AtmosphereSky cizimi basarisiz, kapatildi", ex);
      } finally {
         if (usedLow) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
            GL13.glActiveTexture(prevUnit);
         }

         GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevFbo);
         GL11.glViewport(vp0[0], vp0[1], vp0[2], vp0[3]);
         GL30.glBindVertexArray(prevVao);
         GL20.glUseProgram(prevProg);
         setCap(GL11.GL_DEPTH_TEST, depth);
         GL11.glDepthFunc(depthFunc);
         GL11.glDepthMask(depthMask);
         setCap(GL11.GL_BLEND, blend);
         GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcA, dstA);
         setCap(GL11.GL_CULL_FACE, cull);
      }
   }

   private static void setupBlend() {
      GL11.glEnable(GL11.GL_DEPTH_TEST);
      GL11.glDepthFunc(GL11.GL_LEQUAL);
      GL11.glDepthMask(false);
      GL11.glEnable(GL11.GL_BLEND);
      GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
   }

   private static void uniforms(int mode, float vis, int[] th, float tanX, float tanY, float pixelScale) {
      GL20.glUniform4f(uLook, mode, vis, time, Config.skBright);
      GL20.glUniform4f(uExtra, dayAmt, 0.0F, 0.0F, 0.0F);
      GL20.glUniform4f(uAmounts, Config.skStars, Config.skEffect, Config.skBand, pixelScale);
      rgb(uLow, th[3]);
      rgb(uHigh, th[4]);
      rgb(uBand, th[5]);
      rgb(uZenith, th[0]);
      rgb(uHorizon, th[1]);
      GL20.glUniformMatrix3fv(uInvView, false, inv);
      GL20.glUniform2f(uTanHalf, tanX, tanY);
   }

   /** Kucuk hedef (FBO + doku). Boyut degisince yeniden kurulur; olmazsa dusuk cozunurluk kapatilir. */
   private static boolean ensureTarget(float scale, int prevFbo, int prevTex) {
      int w = Math.max(16, Math.round(vp[2] * scale));
      int h = Math.max(16, Math.round(vp[3] * scale));
      if (fbo != 0 && w == fboW && h == fboH) {
         return true;
      }

      releaseTarget();
      try {
         tex = GL11.glGenTextures();
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
         GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer)null);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
         fbo = GL30.glGenFramebuffers();
         GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
         GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
         boolean ok = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
         GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevFbo);
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
         if (!ok) {
            throw new IllegalStateException("FBO tamamlanmadi");
         }

         fboW = w;
         fboH = h;
         return true;
      } catch (RuntimeException ex) {
         GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevFbo);
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
         releaseTarget();
         lowFailed = true;
         Kuro.LOGGER.warn("[kuro] AtmosphereSky dusuk cozunurluk hedefi kurulamadi, tam cozunurluge donuldu", ex);
         return false;
      }
   }

   private static void releaseTarget() {
      if (fbo != 0) {
         GL30.glDeleteFramebuffers(fbo);
      }

      if (tex != 0) {
         GL11.glDeleteTextures(tex);
      }

      fbo = 0;
      tex = 0;
      fboW = 0;
      fboH = 0;
   }

   /** Anubis: night lerp(daytime..1) * (0.4 + 0.6 * yagmursuzluk) */
   private static float visibility(float td) {
      long t = Math.floorMod(mc.world.getLevelProperties().getTimeOfDay(), 24000L);
      double elev = Math.cos((t - 6000.0) / 24000.0 * Math.PI * 2.0);
      float x = (float)Math.max(0.0, Math.min(1.0, (0.12 - elev) / 0.34));
      float night = x * x * (3.0F - 2.0F * x);
      float day = Math.max(0.0F, Math.min(1.0F, Config.skDay));
      float rain = 1.0F - Math.max(0.0F, Math.min(1.0F, mc.world.getRainGradient(td)));
      dayAmt = 1.0F - night;
      return (day + (1.0F - day) * night) * (0.4F + 0.6F * rain);
   }

   private static void rgb(int loc, int c) {
      GL20.glUniform4f(loc, ((c >> 16) & 255) / 255.0F, ((c >> 8) & 255) / 255.0F, (c & 255) / 255.0F, 1.0F);
   }

   private static void setCap(int cap, boolean on) {
      if (on) {
         GL11.glEnable(cap);
      } else {
         GL11.glDisable(cap);
      }
   }

   private static boolean init() {
      if (prog != 0) {
         return true;
      }

      try {
         String vsh = read("/assets/kuro/sky/sky.vsh");
         int p = link(vsh, read("/assets/kuro/sky/sky.fsh"));
         int b = link(vsh, read("/assets/kuro/sky/blit.fsh"));
         uLook = GL20.glGetUniformLocation(p, "Look");
         uAmounts = GL20.glGetUniformLocation(p, "Amounts");
         uLow = GL20.glGetUniformLocation(p, "ColorLow");
         uHigh = GL20.glGetUniformLocation(p, "ColorHigh");
         uBand = GL20.glGetUniformLocation(p, "ColorBand");
         uZenith = GL20.glGetUniformLocation(p, "Zenith");
         uHorizon = GL20.glGetUniformLocation(p, "Horizon");
         uInvView = GL20.glGetUniformLocation(p, "InvView");
         uTanHalf = GL20.glGetUniformLocation(p, "TanHalf");
         uExtra = GL20.glGetUniformLocation(p, "Extra");
         uBlitTex = GL20.glGetUniformLocation(b, "Tex");
         int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
         vao = GL30.glGenVertexArrays();
         GL30.glBindVertexArray(prevVao);
         blit = b;
         prog = p;
         return true;
      } catch (Exception | LinkageError ex) {
         failed = true;
         Kuro.LOGGER.warn("[kuro] AtmosphereSky shader derlenemedi, kapatildi", ex);
         return false;
      }
   }

   private static int link(String vsSrc, String fsSrc) {
      int vs = compile(GL20.GL_VERTEX_SHADER, vsSrc);
      int fs = 0;
      try {
         fs = compile(GL20.GL_FRAGMENT_SHADER, fsSrc);
         int p = GL20.glCreateProgram();
         GL20.glAttachShader(p, vs);
         GL20.glAttachShader(p, fs);
         GL20.glLinkProgram(p);
         if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(p);
            GL20.glDeleteProgram(p);
            throw new IllegalStateException("link: " + log);
         }

         return p;
      } finally {
         GL20.glDeleteShader(vs);
         if (fs != 0) {
            GL20.glDeleteShader(fs);
         }
      }
   }

   private static int compile(int type, String srcText) {
      int s = GL20.glCreateShader(type);
      GL20.glShaderSource(s, srcText);
      GL20.glCompileShader(s);
      if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
         String log = GL20.glGetShaderInfoLog(s);
         GL20.glDeleteShader(s);
         throw new IllegalStateException("compile: " + log);
      }

      return s;
   }

   private static String read(String path) throws java.io.IOException {
      try (InputStream in = Atmosphere.class.getResourceAsStream(path)) {
         if (in == null) {
            throw new java.io.FileNotFoundException(path);
         }

         return new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
   }
}
