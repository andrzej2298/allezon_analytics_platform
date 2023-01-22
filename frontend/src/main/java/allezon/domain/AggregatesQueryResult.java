package allezon.domain;

import java.util.List;

public record AggregatesQueryResult(List<String> columns, List<List<String>> rows) {
}
