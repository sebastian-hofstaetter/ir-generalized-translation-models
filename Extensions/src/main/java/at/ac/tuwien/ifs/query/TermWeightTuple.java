package at.ac.tuwien.ifs.query;

import org.apache.lucene.index.Term;

/**
 * Simple tuple that contains a <code>{@link Term}</code> and an associated weight
 */
public final class TermWeightTuple {

    public final Term term;
    public final float weight;

    public TermWeightTuple(Term term, float weight) {
        this.term = term;
        this.weight = weight;
    }
}
