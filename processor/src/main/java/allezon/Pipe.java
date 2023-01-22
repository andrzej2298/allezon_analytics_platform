package main.java.allezon;

import org.apache.kafka.common.serialization.Serdes;
import main.java.allezon.UserTagSerde;
import main.java.allezon.domain.UserTagEvent;
import main.java.allezon.domain.Action;
import main.java.allezon.UserTagProcessor;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

public class Pipe {

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "processor-pipe");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "10.112.128.104:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, UserTagSerde.class);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, String.valueOf(0));

        Map<String, String> changelogTopicConfig  = new HashMap<String, String>() {{
            put("cleanup.policy", "compact,delete");
            put("retention.ms", "900000");
            put("retention.bytes", "10000000000");
        }};
        StoreBuilder countStoreBuilder =
            Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore("persistent-counts"),
                Serdes.String(),
                Serdes.Long()
            )
            .withLoggingEnabled(changelogTopicConfig);

        Topology builder = new Topology();
        builder.addSource("Source", "mimuw")
            .addProcessor("Process", () -> new UserTagProcessor(), "Source")
            .addStateStore(countStoreBuilder, "Process");

        final KafkaStreams streams = new KafkaStreams(builder, props);
        final CountDownLatch latch = new CountDownLatch(1);

        // attach shutdown handler to catch control-c
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }
}
