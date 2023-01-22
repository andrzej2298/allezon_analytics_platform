package allezon;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.AerospikeException;
import com.aerospike.client.Key;
import com.aerospike.client.Value;
import com.aerospike.client.cdt.MapOperation;
import com.aerospike.client.cdt.MapPolicy;
import com.aerospike.client.cdt.MapReturnType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import allezon.domain.Action;
import allezon.domain.Aggregate;
import allezon.domain.AggregatesQueryResult;
import allezon.domain.UserProfileResult;
import allezon.domain.UserTagEvent;

@RestController
public class AllezonClient {

    private static final Logger log = LoggerFactory.getLogger(AllezonClient.class);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private NewTopic tagTopic;

    private static ObjectMapper objectMapper = new ObjectMapper();
    final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    static {
        objectMapper.registerModule(new JavaTimeModule());
    }
    private static final AerospikeClient client = new AerospikeClient("10.112.128.106", 3000);

    private void sendKafkaMessage(String cookie, String payload) {
        kafkaTemplate.send(tagTopic.name(), cookie, payload);
    }

    @PostMapping("/user_tags")
    public ResponseEntity<Void> addUserTag(@RequestBody(required = false) UserTagEvent userTag) {
        Key key = new Key("mimuw", "user_tags", userTag.cookie());
        var time = Value.get(userTag.time().toString());

        var serializedTag = new String();
        try {
            serializedTag = objectMapper.writeValueAsString(userTag);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("error parsing json: " + e.toString());
        }
        var value = Value.get(serializedTag);

        sendKafkaMessage(userTag.cookie(), serializedTag);

        try {
            client.operate(null, key,
                    MapOperation.put(new MapPolicy(), userTag.action().toString(), time, value),
                    MapOperation.removeByIndexRange(userTag.action().toString(), -200, 200, MapReturnType.INVERTED));
        } catch (AerospikeException e) {
            log.error("error getting data encountered: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.noContent().build();
    }

    private List<UserTagEvent> getTags(com.aerospike.client.Record record, String cookie, Instant from, Instant to,
            int limit, Action action) {
        var tags = record.getMap(action.toString());

        if (tags == null) {
            return new ArrayList<>();
        }

        return tags.values().stream()
                .map(l -> l.toString())
                .map(j -> {
                    try {
                        return objectMapper.readValue(j, UserTagEvent.class);
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        log.error("error parsing json: " + e.toString());
                        return null;
                    }
                })
                .filter(j -> j != null)
                .filter(j -> j.time().compareTo(from) >= 0 && j.time().compareTo(to) < 0)
                .sorted()
                .limit(limit <= 200 && limit >= 0 ? limit : 200)
                .collect(Collectors.toList());
    }

    public record TimeRange(Instant from, Instant to) {
        public TimeRange(String from, String to) {
            this(Instant.parse(from + "Z"), Instant.parse(to + "Z"));
        }
    }

    @PostMapping("/user_profiles/{cookie}")
    public ResponseEntity<UserProfileResult> getUserProfile(@PathVariable("cookie") String cookie,
            @RequestParam("time_range") String timeRangeStr,
            @RequestParam(defaultValue = "200") int limit,
            @RequestBody(required = false) UserProfileResult expectedResult) {

        String[] rangesStr = timeRangeStr.split("_");
        var ranges = new TimeRange(rangesStr[0], rangesStr[1]);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        List<UserTagEvent> views = new ArrayList<>(), buys = new ArrayList<>();
        try {
            Key key = new Key("mimuw", "user_tags", cookie);
            var record = client.get(null, key);
            views = getTags(record, cookie, ranges.from, ranges.to, limit, Action.VIEW);
            buys = getTags(record, cookie, ranges.from, ranges.to, limit, Action.BUY);
        } catch (AerospikeException e) {
            log.error("error getting data encountered: " + e.getMessage());
        }

        var result = new UserProfileResult(cookie, views != null ? views : new ArrayList<>(),
                buys != null ? buys : new ArrayList<>());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/aggregates")
    public ResponseEntity<AggregatesQueryResult> getAggregates(@RequestParam("time_range") String timeRangeStr,
            @RequestParam("action") Action action,
            @RequestParam("aggregates") List<Aggregate> aggregates,
            @RequestParam(value = "origin", required = false) String origin,
            @RequestParam(value = "brand_id", required = false) String brandId,
            @RequestParam(value = "category_id", required = false) String categoryId,
            @RequestBody(required = false) AggregatesQueryResult expectedResult) {
        String[] rangesStr = timeRangeStr.split("_");
        var ranges = new TimeRange(rangesStr[0], rangesStr[1]);

        List<Key> keys = new ArrayList<>();
        ArrayList<String> minutes = new ArrayList<>();

        for (Instant minute = ranges.from; minute.isBefore(ranges.to); minute = minute.plus(1, ChronoUnit.MINUTES)) {
            var minuteStr = formatter.format(minute);
            minutes.add(minuteStr);
            var key = minuteStr + "Z" +
                    "|" + Objects.toString(origin, "null") +
                    "|" + Objects.toString(brandId, "null") +
                    "|" + Objects.toString(categoryId, "null");
            keys.add(new Key("mimuw", "aggregates", key));
        }
        int bucketCount = minutes.size();

        var records = new com.aerospike.client.Record[] {};
        try {
            records = client.get(null, keys.toArray(new Key[0]));
        } catch (AerospikeException e) {
            log.error("error getting data encountered: " + e.getMessage());
        }
        List<String> columns = new ArrayList<>(Arrays.asList("1m_bucket", "action"));

        if (origin != null) {
            columns.add("origin");
        }
        if (brandId != null) {
            columns.add("brand_id");
        }
        if (categoryId != null) {
            columns.add("category_id");
        }

        for (var aggregate : aggregates) {
            columns.add(aggregate.toString());
        }

        var rows = new ArrayList<List<String>>();
        for (int rowId = 0; rowId < bucketCount; ++rowId) {
            List<String> row = new ArrayList<>(Arrays.asList(minutes.get(rowId), action.toStringUpper()));
            if (origin != null) {
                row.add(origin);
            }
            if (brandId != null) {
                row.add(brandId);
            }
            if (categoryId != null) {
                row.add(categoryId);
            }
            for (var aggregate : aggregates) {
                var record = records[rowId];
                var bin = (action == Action.BUY ? "b" : "v") + (aggregate == Aggregate.COUNT ? "c" : "s");
                row.add((record == null || record.getValue(bin) == null) ? "0" : String.valueOf(record.getLong(bin)));
            }
            rows.add(row);
        }

        return ResponseEntity.ok(new AggregatesQueryResult(columns, rows));
    }
}
