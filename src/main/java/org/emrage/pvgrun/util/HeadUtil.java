package org.emrage.pvgrun.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java. net.URI;
import java. net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net. http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HeadUtil {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient. Redirect.ALWAYS)
            . build();

    private static final Map<UUID, Component> HEAD_CACHE = new ConcurrentHashMap<>();

    private HeadUtil() {}

    public static Component getHead(Player player) {
        UUID playerUUID = player.getUniqueId();
        return HEAD_CACHE.computeIfAbsent(playerUUID, uuid -> {
            String url = String.valueOf(player.getPlayerProfile().getTextures().getSkin());
            return getHeadFromUrl(url).rotate(-90).asComponent();
        });
    }

    public static void clearHeadCache(Player player) {
        UUID playerUUID = player.getUniqueId();
        HEAD_CACHE.remove(playerUUID);
    }

    public static HeadImage getHeadFromUrl(String url) {
        String[] pixels = getPixels(url);
        return new HeadImage(pixels);
    }

    private static String[] getPixels(String url) {
        String[] out = new String[64];

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "PvGRun-Plugin/1.0")
                    .GET()
                    .build();

            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                return fallback(out);
            }

            BufferedImage skin = ImageIO.read(new ByteArrayInputStream(response.body()));
            if (skin == null) {
                return fallback(out);
            }

            BufferedImage base = skin.getSubimage(8, 8, 8, 8);
            BufferedImage over = skin.getSubimage(40, 8, 8, 8);

            int i = 0;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int rgb = base.getRGB(x, y);
                    int rgbO = over.getRGB(x, y);
                    if ((rgbO >>> 24) != 0x00) {
                        rgb = rgbO;
                    }
                    String hex = String.format("#%06X", (rgb & 0xFFFFFF));
                    out[i++] = hex;
                }
            }

            return out;

        } catch (Exception e) {
            return fallback(out);
        }
    }

    private static String[] fallback(String[] out) {
        for (int i = 0; i < 64; i++) {
            out[i] = (i % 2 == 0) ? "#FF00FF" : "#000000";
        }
        return out;
    }

    public record HeadImage(String[] pixels) {
        public HeadImage(String[] pixels) {
            if (pixels == null || pixels.length != 64) {
                this.pixels = fallback(new String[64]);
            } else {
                this. pixels = new String[64];
                System.arraycopy(pixels, 0, this.pixels, 0, 64);
            }
        }

        public HeadImage rotate(int degrees) {
            int norm = ((degrees % 360) + 360) % 360;
            int steps = (norm / 90) % 4;
            if (steps == 0) return this;

            String[] buf = new String[64];
            String[] src = this.pixels;

            for (int s = 0; s < steps; s++) {
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        int oldIndex = y * 8 + x;
                        int nx = 7 - y;
                        int newIndex = x * 8 + nx;
                        buf[newIndex] = src[oldIndex];
                    }
                }
                System.arraycopy(buf, 0, src, 0, 64);
            }
            System.arraycopy(src, 0, this.pixels, 0, 64);
            return this;
        }

        public Component asComponent() {
            Component comp = Component.empty();
            int i = 0;
            for (int y = 0; y < 8; y++) {
                for (int col = 0; col < 8; col++) {
                    char base = (char) ('\uF000' + (col + 1));
                    char extra = (col == 7 && i != 63) ? '\uF101' : '\uF102';
                    String text = "" + base + extra;
                    comp = comp.append(Component.text(text).color(TextColor.fromHexString(pixels[i])));
                    i++;
                }
            }
            return comp.append(Component.text(""). font(Key.key("minecraft:default")));
        }
    }
}