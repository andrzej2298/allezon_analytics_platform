package main.java.allezon;

import com.aerospike.client.AerospikeException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.time.temporal.ChronoUnit;
import com.aerospike.client.Key;
import com.aerospike.client.Value;
import com.aerospike.client.Bin;
import main.java.allezon.domain.UserTagEvent;
import main.java.allezon.domain.Action;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.KeyValue;
import com.aerospike.client.AerospikeClient;
import com.aerospike.client.exp.ExpOperation;
import com.aerospike.client.exp.Exp; 
import com.aerospike.client.BatchWrite;
import com.aerospike.client.BatchRecord;
import com.aerospike.client.exp.ExpWriteFlags;
import com.aerospike.client.exp.ExpWriteFlags;
import com.aerospike.client.Operation;
import com.aerospike.client.policy.BatchWritePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserTagProcessor implements Processor<String, UserTagEvent, String, String> {
    private KeyValueStore<String, Long> kvStore;
    private static final AerospikeClient client = new AerospikeClient("10.112.128.106", 3000);
    private static final Logger log = LoggerFactory.getLogger(UserTagProcessor.class);

    @Override
    public void init(final ProcessorContext<String, String> context) {
        log.info("initialising processor");
        context.schedule(Duration.ofSeconds(15), PunctuationType.WALL_CLOCK_TIME, timestamp -> {
            long startTime = System.nanoTime();
            var itemsToDelete = new ArrayList<KeyValue<String, Long>>();
            var batchRecords = new ArrayList<BatchRecord>();
            try (final KeyValueIterator<String, Long> iter = kvStore.all()) {
                while (iter.hasNext()) {
                    final KeyValue<String, Long> entry = iter.next();
                    context.forward(new Record<>(entry.key, entry.value.toString(), timestamp));
                    itemsToDelete.add(new KeyValue<>(entry.key, null));
                    String[] keyAndBin = entry.key.split("\n");
                    var key = keyAndBin[0];
                    var bin = keyAndBin[1];
                    var newValueExpression = Exp.build(
                        Exp.cond(
                            Exp.binExists(bin), Exp.add(Exp.intBin(bin), Exp.val(entry.value)), // add if exists
                            Exp.val(entry.value) // otherwise create new value
                            ));
                    var operation = Operation.array(ExpOperation.write(bin, newValueExpression, ExpWriteFlags.DEFAULT));
                    var policy = new BatchWritePolicy();
                    policy.expiration = 86400; // keep aggregates in db for a day
                    batchRecords.add(new BatchWrite(policy, new Key("mimuw", "aggregates", key), operation));

                    if (batchRecords.size() == 5000) {
                        try {
                            client.operate(null, batchRecords);
                        } catch (AerospikeException e) {
                            log.error("error getting data encountered: " + e.getMessage());
                        }
                        batchRecords.clear();
                    }
                }
            }
            if (batchRecords.size() > 0) {
                try {
                    client.operate(null, batchRecords);
                } catch (AerospikeException e) {
                    log.error("error getting data encountered: " + e.getMessage());
                }
            }

            kvStore.putAll(itemsToDelete);
            long endTime = System.nanoTime();
            log.info("processor ran for " + String.valueOf((endTime - startTime) / 1000000) + " ms");
        });
        kvStore = context.getStateStore("persistent-counts");
    }

    @Override
    public void process(final Record<String, UserTagEvent> record) {
        var tag = record.value();

        for (var origin : new String[]{tag.origin(), "null"}) {
            for (var brand : new String[]{tag.productInfo().brandId(), "null"}) {
                for (var category : new String[]{tag.productInfo().categoryId(), "null"}) {
                    var key = (tag.time().truncatedTo(ChronoUnit.MINUTES).toString()) + "|" + origin + "|" + brand + "|" + category;
                    var actionPrefix = (tag.action() == Action.BUY ? "b" : "v");
                    var countKey = key + "\n" + actionPrefix + "c";
                    var sumKey = key + "\n" + actionPrefix + "s";

                    final Long oldCountValue = kvStore.get(countKey);
                    if (oldCountValue == null) {
                        kvStore.put(countKey, 1L);
                    } else {
                        kvStore.put(countKey, oldCountValue + 1L);
                    }
                    final Long oldSumValue = kvStore.get(sumKey);
                    if (oldSumValue == null) {
                        kvStore.put(sumKey, Long.valueOf(tag.productInfo().price()));
                    } else {
                        kvStore.put(sumKey, Long.valueOf(oldSumValue + tag.productInfo().price()));
                    }
                }
            }
        }

    }

    @Override
    public void close() {}
}
