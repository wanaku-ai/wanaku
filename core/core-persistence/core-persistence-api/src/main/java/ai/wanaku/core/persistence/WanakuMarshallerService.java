package ai.wanaku.core.persistence;

import ai.wanaku.api.exceptions.WanakuException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Service class responsible for marshalling and unmarshalling objects to and from JSON format.
 * This class provides functionality to convert Java objects to JSON strings and vice versa.
 */
public class WanakuMarshallerService {

    private ObjectMapper mapper;

    public WanakuMarshallerService() {
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Converts a Java object to its JSON string representation.
     *
     * @param object The object to be marshalled to JSON
     * @return A JSON string representation of the object
     * @throws WanakuException If marshalling fails
     */
    public String marshal(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new WanakuException("Marshal failed", e);
        }
    }

    /**
     * Converts a JSON string to a list of Java objects of the specified type.
     *
     * @param json The JSON string to be unmarshalled
     * @param clazz The class type to convert the JSON elements to
     * @param <T> The generic type of the objects in the resulting list
     * @return A list of objects of type T parsed from the JSON string
     * @throws WanakuException If unmarshalling fails
     */
    public <T> List<T> unmarshal(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (JsonProcessingException e) {
            throw new WanakuException("Unmarshal failed", e);
        }
    }

    /**
     * Converts a JSON string to a single Java object of the specified type.
     *
     * @param json The JSON string to be unmarshalled
     * @param clazz The class type to convert the JSON to
     * @param <T> The generic type of the resulting object
     * @return An object of type T parsed from the JSON string
     * @throws WanakuException If unmarshalling fails
     */
    public <T> T unmarshalOne(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new WanakuException("Unmarshal failed", e);
        }
    }
}
