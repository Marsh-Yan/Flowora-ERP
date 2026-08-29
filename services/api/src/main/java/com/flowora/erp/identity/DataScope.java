package com.flowora.erp.identity;

public enum DataScope {
    ALL,
    DEPARTMENT,
    SELF,
    ASSIGNED;

    public static DataScope mostPermissive(Iterable<String> values) {
        DataScope selected = ASSIGNED;
        for (String value : values) {
            DataScope candidate = DataScope.valueOf(value);
            if (candidate.ordinal() < selected.ordinal()) {
                selected = candidate;
            }
        }
        return selected;
    }
}
