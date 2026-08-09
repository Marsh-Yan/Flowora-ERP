package com.flowora.erp.search;

import java.util.List;

public final class SearchDtos {
    private SearchDtos() {
    }

    public record SearchResponse(List<SearchResult> results) {
    }

    public record SearchResult(String type, String id, String title, String subtitle, String route) {
    }
}
