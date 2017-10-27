package at.ac.tuwien.ifs.api;

import org.apache.lucene.index.Term;
import at.ac.tuwien.ifs.query.TermWeightTuple;

/**
 * Single term with associated weighted similar term tuples
 */
public class SimilarTermModel {

    public final Term queryTerm;
    public final TermWeightTuple[] similarTerms;

    public SimilarTermModel(Term queryTerm, TermWeightTuple[] similarTerms) {
        this.queryTerm = queryTerm;
        this.similarTerms = similarTerms;
    }
}
