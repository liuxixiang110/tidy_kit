package com.you.jsonformatter.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * URL 编码/解码单测(脱离 IDE):未保留字符白名单、UTF-8 多字节、大写十六进制、宽松解码。
 */
class UrlCodecTest {

    @Test
    void keepsUnreservedCharacters() {
        String plain = "aZ0-_.~";
        assertEquals(plain, UrlCodec.encode(plain));
    }

    @Test
    void encodesReservedAndSpaceAsUpperHex() {
        // 空格必须是 %20 而不是 +;+ / ? & = 都要转义,才能安全塞进查询串
        assertEquals("a%20b%2Fc%3Fd%26e%3Df%2Bg", UrlCodec.encode("a b/c?d&e=f+g"));
    }

    @Test
    void encodesChineseAsUtf8Bytes() {
        assertEquals("%E4%B8%AD", UrlCodec.encode("中"));
        assertEquals("%E4%BD%A0%E5%A5%BD", UrlCodec.encode("你好"));
    }

    @Test
    void encodesControlCharacters() {
        assertEquals("a%0Ab%09c", UrlCodec.encode("a\nb\tc"));
    }

    @Test
    void encodesEmojiWithoutSplittingSurrogates() {
        assertEquals("%F0%9F%98%80", UrlCodec.encode("\uD83D\uDE00"));
    }

    @Test
    void decodeHandlesLowerHexEscapes() {
        assertEquals("中", UrlCodec.decode("%e4%b8%ad"));
    }

    @Test
    void roundTripPreservesMixedContent() {
        String source = "查询: 你好 world? a+b=1&x=中文 😀 100%";
        assertEquals(source, UrlCodec.decode(UrlCodec.encode(source)));
    }

    @Test
    void decodeKeepsPlusAsLiteralPlus() {
        // 表单编码会把空格写成 +,但 URL 里的 + 常常是值的一部分,默认不动它
        assertEquals("a+b", UrlCodec.decode("a+b"));
        assertEquals("a b", UrlCodec.decode("a%20b"));
    }

    @Test
    void decodeLeavesMalformedEscapeAlone() {
        assertEquals("100%", UrlCodec.decode("100%"));
        assertEquals("%zz", UrlCodec.decode("%zz"));
        assertEquals("%2", UrlCodec.decode("%2"));
        assertEquals("%", UrlCodec.decode("%"));
    }

    @Test
    void decodePassesThroughLiteralNonAscii() {
        // 混排文本里既有中文又有转义序列,不能整段解不了
        assertEquals("中文", UrlCodec.decode("%E4%B8%AD文"));
    }

    @Test
    void emptyAndNullBecomeEmpty() {
        assertEquals("", UrlCodec.encode(""));
        assertEquals("", UrlCodec.encode(null));
        assertEquals("", UrlCodec.decode(""));
        assertEquals("", UrlCodec.decode(null));
    }
}
