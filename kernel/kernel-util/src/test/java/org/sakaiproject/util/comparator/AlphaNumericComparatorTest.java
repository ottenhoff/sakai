package org.sakaiproject.util.comparator;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class AlphaNumericComparatorTest {
    private AlphaNumericComparator alphaNumeric = new AlphaNumericComparator();

    @Test
    public void alphanumericCompare() {
        List<String> rawData = Arrays.asList("X19", "X10", "X25", "X111", "X2", "X1", "X", "0", "X242+141.55", null, "A11", "B12", "a", "11720217301000", "b", "9999372036854775807");
        List<String> expectedSort = Arrays.asList(null, "0", "11720217301000", "9999372036854775807", "a", "A11", "b", "B12", "X", "X1", "X2", "X10", "X19", "X25", "X111", "X242+141.55");

        //rawData.stream().sorted(alphaNumeric).forEach(System.out::println);
        rawData.sort(alphaNumeric);

        assertEquals(rawData, expectedSort);

        // Nulls first
        assertEquals(rawData.get(0), null);
        assertEquals(rawData.get(1), "0");
    }
}
