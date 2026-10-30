package com.tidy.kit.codec;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * URL 百分号编码/解码(RFC 3986),固定 UTF-8,无状态可直接静态调用。
 *
 * 编码:保留未保留字符 {@code A-Za-z0-9-_.~},其余全部按 UTF-8 转成大写 {@code %XX};
 * 空格转 {@code %20} 而不是 {@code +}(那是表单编码的私有约定,不是 URL 的)。
 * 语义等同 JavaScript 的 encodeURIComponent,即"把一个值安全地塞进 URL"。
 *
 * 解码:只认 {@code %XX},不把 {@code +} 当空格 —— 很多链接里的加号是真加号,
 * 按表单规则解会把 {@code a+b} 错解成 {@code a b}。遇到不合法的百分号序列
 * 原样保留而不是报错,免得粘来一段混排文本就什么都做不了。
 */
public final class UrlCodec {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private UrlCodec() {
    }

    public static String encode(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if (isUnreserved(c)) {
                sb.append((char) c);
            } else {
                sb.append('%').append(HEX[c >> 4]).append(HEX[c & 0x0F]);
            }
        }
        return sb.toString();
    }

    public static String decode(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(text.length());
        StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '%' && i + 2 < text.length() && isHex(text.charAt(i + 1)) && isHex(text.charAt(i + 2))) {
                flushLiteral(literal, bytes);
                bytes.write(Integer.parseInt(text.substring(i + 1, i + 3), 16));
                i += 3;
            } else {
                // 连续的字面量先攒着,整体按 UTF-8 落进字节流,代理对才不会被子节拆开
                literal.append(c);
                i++;
            }
        }
        flushLiteral(literal, bytes);
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void flushLiteral(StringBuilder literal, ByteArrayOutputStream bytes) {
        if (literal.length() == 0) {
            return;
        }
        bytes.writeBytes(literal.toString().getBytes(StandardCharsets.UTF_8));
        literal.setLength(0);
    }

    private static boolean isUnreserved(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '-' || c == '_' || c == '.' || c == '~';
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
