package me.ag2s.epublib.util.zip;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for AndroidZipEntry, covering bugs fixed in the codebase.
 */
public class AndroidZipEntryTest {

    /**
     * Bug fix: readEntries() was calling setTime(dostime) which treats the
     * argument as a Unix epoch timestamp, but dostime is a DOS-format packed
     * integer. The fix changed the call to setDOSTime(dostime).
     * <p>
     * This test verifies that setDOSTime correctly stores the raw DOS time
     * and getTime() can reconstruct a valid epoch timestamp from it.
     */
    @Test
    public void testSetDOSTimePreservesValue() {
        AndroidZipEntry entry = new AndroidZipEntry("test.html", 9);

        // DOS time for 2023-06-15 14:30:00
        // year-1980=43 => 43<<25, month=6=>6<<21, day=15=>15<<16,
        // hour=14=>14<<11, min=30=>30<<5, sec=0=>0>>1
        int dosTime = (43 << 25) | (6 << 21) | (15 << 16) | (14 << 11) | (30 << 5) | 0;

        entry.setDOSTime(dosTime);

        // Verify getDOSTime returns the same value
        assertEquals("setDOSTime should preserve the raw DOS time value",
                dosTime, entry.getDOSTime());

        // Verify getTime() returns a valid epoch timestamp (positive, reasonable range)
        long epochTime = entry.getTime();
        assertEquals("getTime should return a valid epoch time from DOS time",
                1686837000000L / 1000, epochTime);
    }

    /**
     * Verify that setTime (Unix epoch) and setDOSTime (DOS format) produce
     * consistent results when given equivalent timestamps.
     */
    @Test
    public void testSetTimeVsSetDOSTimeConsistency() {
        AndroidZipEntry entryDos = new AndroidZipEntry("test1.html", 10);
        AndroidZipEntry entryUnix = new AndroidZipEntry("test2.html", 10);

        // Use a known DOS time
        int dosTime = (43 << 25) | (6 << 21) | (15 << 16) | (14 << 11) | (30 << 5) | 0;

        entryDos.setDOSTime(dosTime);

        // Get the Unix epoch time from the DOS entry
        long unixTime = entryDos.getTime();

        // Set the same time via setTime on the other entry
        entryUnix.setTime(unixTime);

        // Both should return the same DOS time (within 2-second resolution)
        assertEquals("setTime and setDOSTime should produce consistent DOS times",
                entryDos.getDOSTime() >> 1, entryUnix.getDOSTime() >> 1);
    }
}
