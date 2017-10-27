package at.ac.tuwien.ifs.query;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.search.*;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.search.similarities.Similarity;

/**
 * This <code>{@link AugmentedTermScorer}</code> scores a document based on a main term and a set of weighted similar terms
 * Every document that is in at least one <code>{@link PostingsEnum}</code> is found and then scored with all other frequencies
 * at that position, based on their weights. The main term gets the weight 1 assigned to it.
 */
public class AugmentedTermScorer extends Scorer {
    private final PostingsEnumWeightTuple[] postings;
    private final Similarity.SimScorer docScorer;

    private final MultiDocIdSetIterator iterator;

    /**
     * Construct an <code>query.{@link AugmentedTermScorer}</code>.
     *
     * @param weight
     *          The weight of the <code>Term</code> in the query.
     * @param mainTerm
     *          An iterator over the documents matching the main <code>Term</code>.
     * @param similarPostings
     *          A list of <code>PostingsEnumWeightTuple</code>: term iterator, weight pairs
     * @param docScorer
     *          The <code>Similarity.SimScorer</code> implementation
     *          to be used for score computations.
     */
    public AugmentedTermScorer(Weight weight, PostingsEnum mainTerm, List<PostingsEnumWeightTuple> similarPostings, Similarity.SimScorer docScorer) {
        super(weight);

        this.postings = new PostingsEnumWeightTuple[similarPostings.size() + 1];
        this.postings[0] = new PostingsEnumWeightTuple(mainTerm,1f);
        for (int i = 0; i < similarPostings.size(); i++) {
            this.postings[i + 1] = similarPostings.get(i);
        }

        this.iterator = new MultiDocIdSetIterator(this.postings);

        this.docScorer = docScorer;
    }

    @Override
    public int docID() {
        return this.iterator.docID();
    }

    /**
     * Attention (!!) this is a rounded frequency sum, use <code>{@link #exactFreq()}</code> for the exact frequency
     * at the current <code>{@link #docID()}</code>
     */
    @Override
    public int freq() throws IOException {
        return Math.round(exactFreq());
    }

    /**
     * Returns the exact floating point sum of all weighted frequencies for the current <code>{@link #docID()}</code>
     */
    public float exactFreq() throws IOException {
        float sum = 0;
        for (PostingsEnumWeightTuple enumWeightTuple : postings) {
            if (enumWeightTuple.postingsEnum.docID() == docID()) {
                sum += enumWeightTuple.postingsEnum.freq() * enumWeightTuple.weight;
            }
        }
        return sum;
    }

    @Override
    public DocIdSetIterator iterator() {
        return iterator;
    }

    /**
     * The score function combines the term frequencies of all <code>{@link PostingsEnum}</code> entries for
     * the current <code>{@link #docID()}docID()</code>
     * See <code>{@link #exactFreq()}</code> for the sum computation
     */
    @Override
    public float score() throws IOException {
        assert docID() != DocIdSetIterator.NO_MORE_DOCS;
        return docScorer.score(docID(), exactFreq());
    }

    /** Returns a string representation of this <code>AugmentedTermScorer</code>. */
    @Override
    public String toString() { return "scorer(" + weight + ")[" + super.toString() + "]"; }
}
