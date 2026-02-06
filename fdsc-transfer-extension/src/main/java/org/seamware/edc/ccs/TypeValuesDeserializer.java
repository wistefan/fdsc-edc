package org.seamware.edc.ccs;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// Json-LD flattening might produce different "looks" of type_values.
public class TypeValuesDeserializer extends JsonDeserializer<List<List<String>>> {

    @Override
    public List<List<String>> deserialize(JsonParser p,
                                          DeserializationContext ctxt)
            throws IOException {

        ObjectCodec codec = p.getCodec();
        JsonNode node = codec.readTree(p);

        List<List<String>> result = new ArrayList<>();

        if (node.isTextual()) {
            // "type_values": "value"
            result.add(List.of(node.asText()));
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                if (element.isTextual()) {
                    // "type_values": ["value"]
                    result.add(List.of(element.asText()));
                } else if (element.isArray()) {
                    // "type_values": [["value"], ["other"]]
                    List<String> inner = new ArrayList<>();
                    for (JsonNode innerNode : element) {
                        inner.add(innerNode.asText());
                    }
                    result.add(inner);
                }
            }
        }

        return result;
    }
}
