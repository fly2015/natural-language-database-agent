package com.nlda.format;

import java.util.List;
import java.util.Map;

public record TableResult(
        List<String> columns,
        List<Map<String, Object>> rows
) {
}
