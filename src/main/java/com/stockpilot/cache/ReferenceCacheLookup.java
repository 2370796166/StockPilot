package com.stockpilot.cache;

public record ReferenceCacheLookup<T>(boolean hit, boolean found, T value) {
    public static <T> ReferenceCacheLookup<T> miss() {
        return new ReferenceCacheLookup<>(false, false, null);
    }

    public static <T> ReferenceCacheLookup<T> missingValue() {
        return new ReferenceCacheLookup<>(true, false, null);
    }

    public static <T> ReferenceCacheLookup<T> found(T value) {
        return new ReferenceCacheLookup<>(true, true, value);
    }
}
