package at.ac.tuwien.ifs.query;

import org.apache.lucene.index.PostingsEnum;

/**
 * Simple tuple that contains a <code>{@link PostingsEnum}</code> and a weight associated with it
 */
public final class PostingsEnumWeightTuple {

    public final PostingsEnum postingsEnum;
    public final float weight;

    public PostingsEnumWeightTuple(PostingsEnum postingsEnum, float weight) {
        this.postingsEnum = postingsEnum;
        this.weight = weight;
    }
}
