package main.java.allezon;

import java.util.Map;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.Deserializer;
import main.java.allezon.domain.UserTagEvent;
import main.java.allezon.UserTagSerializer;
import main.java.allezon.UserTagDeserializer;

public class UserTagSerde implements Serde<UserTagEvent> {
    private UserTagSerializer serializer = new UserTagSerializer();
    private UserTagDeserializer deserializer = new UserTagDeserializer();

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        serializer.configure(configs, isKey);
        deserializer.configure(configs, isKey);
    }

    @Override
    public void close() {
        serializer.close();
        deserializer.close();
    }

    @Override
    public Serializer<UserTagEvent> serializer() {
        return serializer;
    }

    @Override
    public Deserializer<UserTagEvent> deserializer() {
        return deserializer;
    }
}
