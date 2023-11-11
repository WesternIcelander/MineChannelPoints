package io.siggi.minechannelpoints.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class UrlUtils {
    private UrlUtils() {
    }
    public static String urlEncodeMap(Map<String, String> map) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!builder.isEmpty()) builder.append("&");
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null) continue;
            builder.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
            builder.append("=");
            builder.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        return builder.toString();
    }
}
