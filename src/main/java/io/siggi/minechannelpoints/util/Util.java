package io.siggi.minechannelpoints.util;

import com.google.gson.JsonElement;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;

public class Util {
    private Util() {
    }

    public static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copy(in, out);
        return out.toByteArray();
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[4096];
        int c;
        while ((c = in.read(buffer, 0, buffer.length)) != -1) {
            out.write(buffer, 0, c);
        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xff;
            if (b < 16) {
                sb.append("0");
            }
            sb.append(Integer.toString(b, 16));
        }
        return sb.toString();
    }

    public static String getString(JsonElement element) {
        return element == null ? null : element.getAsString();
    }
}
