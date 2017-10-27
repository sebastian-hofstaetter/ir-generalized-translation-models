package at.ac.tuwien.ifs.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Internal use only, needed to convert json to lucene term format
 *
 * The similar terms and weights have the same index / length
 */
@JsonDeserialize(using = ItemDeserializer.class)
public class ApiReturnModel{

    public ApiReturnItem[] items;

    ApiReturnModel(ApiReturnItem[] apiReturnItems) {
        items = apiReturnItems;
    }
}