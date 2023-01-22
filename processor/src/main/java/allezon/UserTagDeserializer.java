package main.java.allezon;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import main.java.allezon.domain.UserTagEvent;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;

public class UserTagDeserializer implements Deserializer<UserTagEvent> {
    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public UserTagEvent deserialize(String topic, byte[] data) {
        try {
            if (data == null){
                System.out.println("can't deserialize null");
                return null;
            }
            return objectMapper.readValue(new String(data), UserTagEvent.class);
        } catch (Exception e) {
            throw new SerializationException("deserialization error: " + e.toString());
        }
    }

    @Override
    public void close() {
    }
}
