package main.java.allezon;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import main.java.allezon.domain.UserTagEvent;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;

public class UserTagSerializer implements Serializer<UserTagEvent> {
    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public byte[] serialize(String topic, UserTagEvent event) {
        try {
            return objectMapper.writeValueAsString(event).getBytes();
        } catch (Exception e) {
            throw new SerializationException("serialization error" + e.toString());
        }
    }

    @Override
    public void close() {
    }
}
