package at.ac.tuwien.ifs.api;

import org.apache.lucene.index.Term;
import at.ac.tuwien.ifs.query.TermWeightTuple;

/**
 * A mock for the api access, that can be used to test the system, without an accessible api access
 * - it adds the term "bla" with .5 as weight to every term ...
 */
public class SimilarityApiMock implements ISimilarityApi {

    public static TermWeightTuple similarTerm(String field){
        return new TermWeightTuple(new Term(field, "bla"), .5f);
    }

    @Override
    public SimilarTermModel[] GetSimilarTerms(String field, String[] queryTerms) {

        SimilarTermModel[] output = new SimilarTermModel[queryTerms.length];
        for (int i = 0; i < queryTerms.length; i++) {
            TermWeightTuple[] similar = new TermWeightTuple[1];
            similar[0] = similarTerm(field);

            output[i] = new SimilarTermModel(new Term(field, queryTerms[i]), similar);
        }

        return output;
    }
}
