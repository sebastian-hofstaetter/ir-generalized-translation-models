package at.ac.tuwien.ifs.mocks;

import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.BytesRef;

/**
 * Slim mock to get the given values of the <code>score<()/code> method.
 */
public class SimScorerMock extends Similarity.SimScorer {

    private int doc;
    private float freq;

    @Override
    public float score(int doc, float freq) {
        this.doc = doc;
        this.freq = freq;
        return 0;
    }

    @Override
    public float computeSlopFactor(int distance) {
        return 0;
    }

    @Override
    public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
        return 0;
    }

    public int getDoc() {
        return doc;
    }

    public float getFreq() {
        return freq;
    }
}
