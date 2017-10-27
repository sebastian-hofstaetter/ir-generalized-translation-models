package at.ac.tuwien.ifs.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Internal use only, needed to convert json to lucene term format
 */
public class ItemDeserializer extends StdDeserializer<ApiReturnModel> {

    public ItemDeserializer() {
        this(null);
    }

    public ItemDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public ApiReturnModel deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {

        JsonNode node = jp.getCodec().readTree(jp);
        List<ApiReturnItem> outputTemp = new ArrayList<>();

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

        while (fields.hasNext()){
            Map.Entry<String, JsonNode> curr = fields.next();
            ApiReturnItem item = new ApiReturnItem();

            item.mainTerm = curr.getKey();

            JsonNode stringlist = curr.getValue().get(0);
            item.similarTerms = new String[stringlist.size()];
            for (int i = 0; i < stringlist.size();i++) {
                item.similarTerms[i] = stringlist.get(i).asText();
            }

            JsonNode weightlist = curr.getValue().get(1);
            item.similarWeights = new float[weightlist.size()];
            for (int i = 0; i < weightlist.size();i++) {
                item.similarWeights[i] = weightlist.get(i).floatValue();
            }

            outputTemp.add(item);
        }

        return new ApiReturnModel(outputTemp.toArray(new ApiReturnItem[0]));
    }
}