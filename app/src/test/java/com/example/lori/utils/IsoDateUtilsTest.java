package com.example.lori.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Date;

public class IsoDateUtilsTest {

    @Test
    public void format_epochZero_returnsUtcWithMillis() {
        assertEquals("1970-01-01T00:00:00.000Z", IsoDateUtils.format(new Date(0L)));
    }

    @Test
    public void parse_withoutFraction_returnsExactSeconds() {
        //2026-10-10T04:59:40Z = 1791608380 giây kể từ epoch
        assertEquals(1791608380000L, IsoDateUtils.parse("2026-10-10T04:59:40Z").getTime());
    }

    @Test
    public void parse_withMicroseconds_keepsOnlyMillis() {
        assertEquals(1791608380123L, IsoDateUtils.parse("2026-10-10T04:59:40.123456Z").getTime());
    }

    @Test
    public void parse_withShortFraction_padsMillis() {
        assertEquals(1791608380500L, IsoDateUtils.parse("2026-10-10T04:59:40.5Z").getTime());
    }

    @Test
    public void formatThenParse_roundTripKeepsMillis() {
        Date original = new Date(1791608380789L);
        Date parsed = IsoDateUtils.parse(IsoDateUtils.format(original));
        assertNotNull(parsed);
        assertEquals(original.getTime(), parsed.getTime());
    }

    @Test
    public void parse_invalidInput_returnsNull() {
        assertNull(IsoDateUtils.parse(null));
        assertNull(IsoDateUtils.parse(""));
        assertNull(IsoDateUtils.parse("abc"));
        //Thiếu chữ Z ở cuối: không phải dạng Backend trả về
        assertNull(IsoDateUtils.parse("2026-10-10T04:59:40"));
        assertNull(IsoDateUtils.parse("2026-13-40T04:59:40Z"));
    }
}