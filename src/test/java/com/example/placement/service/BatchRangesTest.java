package com.example.placement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class BatchRangesTest {

    @Test
    void parsesSingleAndMultipleRanges() {
        assertEquals(List.of(new BatchRanges.Range(2022, 2026)), BatchRanges.parse("2022-2026"));
        assertEquals(List.of(new BatchRanges.Range(2022, 2024), new BatchRanges.Range(2026, 2028)),
                BatchRanges.parse("2022-2024, 2026-2028"));
        assertEquals(List.of(new BatchRanges.Range(2022, 2024), new BatchRanges.Range(2026, 2028)),
                BatchRanges.parse("  2022-2024 ,  2026-2028  "));
        assertEquals(List.of(new BatchRanges.Range(2022, 2022)), BatchRanges.parse("2022"));
        assertEquals(List.of(), BatchRanges.parse(null));
        assertEquals(List.of(), BatchRanges.parse("  "));
        assertEquals(List.of(), BatchRanges.parse("All"));
    }

    @Test
    void normalizesReversedRanges() {
        assertEquals(List.of(new BatchRanges.Range(2022, 2026)), BatchRanges.parse("2026-2022"));
    }

    @Test
    void gapYearIsNotCoveredByMultipleRanges() {
        // The bug this guards: collapsing to min/max would admit the 2025 batch.
        String spec = "2022-2024, 2026-2028";
        assertFalse(BatchRanges.overlapsAny(spec, "2025"), "2025 must not be eligible");
        assertTrue(BatchRanges.overlapsAny(spec, "2023"));
        assertTrue(BatchRanges.overlapsAny(spec, "2027"));
        assertFalse(BatchRanges.overlapsAny(spec, "2029"));
    }

    @Test
    void overlapsHandlesSingleYearAndPartialOverlap() {
        assertTrue(BatchRanges.overlapsAny("2022-2026", "2022"));
        assertTrue(BatchRanges.overlapsAny("2022-2026", "2025-2027"));
        assertTrue(BatchRanges.overlapsAny("2021-2025", "2022-2026"));
        assertFalse(BatchRanges.overlapsAny("2027-2030", "2022-2026"));
        assertFalse(BatchRanges.overlapsAny("", "2022-2026"));
        assertFalse(BatchRanges.overlapsAny("2022-2026", ""));
    }

    @Test
    void validatesEveryRangeInTheSpec() {
        assertTrue(BatchRanges.isValidSpec(null));
        assertTrue(BatchRanges.isValidSpec(""));
        assertTrue(BatchRanges.isValidSpec("2022-2026"));
        assertTrue(BatchRanges.isValidSpec("2022-2024, 2026-2028"));
        assertTrue(BatchRanges.isValidSpec(" 2022-2024 , 2026-2028 "));
        assertTrue(BatchRanges.isValidSpec("2022-2024,2026-2028,2030-2031"));
        assertFalse(BatchRanges.isValidSpec("2026-2022"), "From Year after To Year must fail");
        assertFalse(BatchRanges.isValidSpec("2022-2024, 2028-2026"), "any reversed range must fail");
        assertFalse(BatchRanges.isValidSpec("2022"), "a single year is not a range");
        assertFalse(BatchRanges.isValidSpec("2022-2024,"), "trailing comma must fail");
        assertFalse(BatchRanges.isValidSpec("abc-def"));
        assertFalse(BatchRanges.isValidSpec("2022-2024, All"));
    }

    @Test
    void normalizeProducesCanonicalCommaSeparatedForm() {
        assertEquals("2022-2024, 2026-2028", BatchRanges.normalize("2022-2024,2026-2028"));
        assertEquals("2022-2026", BatchRanges.normalize(" 2022-2026 "));
        assertEquals("2022-2026", BatchRanges.normalize("2026-2022"));
        assertEquals("", BatchRanges.normalize(""));
        assertEquals("", BatchRanges.normalize(null));
    }
}
