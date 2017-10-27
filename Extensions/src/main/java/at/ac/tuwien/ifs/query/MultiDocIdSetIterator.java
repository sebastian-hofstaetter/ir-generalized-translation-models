package at.ac.tuwien.ifs.query;

import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.search.DocIdSetIterator;

import java.io.IOException;

/**
 * Wraps multiple <code>{@link PostingsEnum}</code> classes and exposes a single combined iterator.
 * This PostingsEnum iterates through all docId's in all <code>{@link PostingsEnum}</code> instances in ascending order.
 * No document will be left out, each underlining iterator will only be iterated once until it is exhausted.
 *
 * If all iterators are exhausted, this class will return {@link DocIdSetIterator#NO_MORE_DOCS}.
 *
 * <remarks>
 * This iterator, does nothing more with the given postings enumerations than to iterate through them.
 * The iterator only prepares the docId for the <code>{@link AugmentedTermScorer}</code>. The scorer class utilizes
 * the frequency + weight information of the <code>{@link PostingsEnumWeightTuple}</code> data structure.
 * </remarks>
 */
public class MultiDocIdSetIterator extends DocIdSetIterator {

    private int currentDocId = -1;

    private PostingsEnumWeightTuple[] enumWeightTuples;

    public MultiDocIdSetIterator(PostingsEnumWeightTuple[] enumWeightTuples) {
        this.enumWeightTuples = enumWeightTuples;
    }

    /**
     * Returns the following:
     * <ul>
     * <li><code>-1</code> if {@link #nextDoc()} or
     * {@link #advance(int)} were not called yet.
     * <li>{@link #NO_MORE_DOCS} if the iterator has exhausted.
     * <li>Otherwise it should return the doc ID it is currently on.
     * </ul>
     * <p>
     *
     * @since 2.9
     */
    @Override
    public int docID() {
        return currentDocId;
    }

    /**
     * Advances to the next document in the set and returns the doc it is
     * currently on, or {@link #NO_MORE_DOCS} if there are no more docs in the
     * set.<br>
     * <p>
     * <b>NOTE:</b> after the iterator has exhausted you should not call this
     * method, as it may result in unpredicted behavior.
     *
     * @since 2.9
     */
    @Override
    public int nextDoc() throws IOException {

        //
        // find the next higher, but smallest delta doc id
        // among the list of iterators (without moving them!)
        //
        int smallestNextDocId = NO_MORE_DOCS;

        for (PostingsEnumWeightTuple enumWeightTuple : enumWeightTuples) {
            int id = enumWeightTuple.postingsEnum.docID();

            if(id == NO_MORE_DOCS){
                continue;
            }

            // We always move the smallest postings forward - this is enough to keep all postings in sync.
            // The currentDocId is the smallest id possible at this point, and we move the
            // postingEnum to the next doc, this next doc might or might not be
            // the smallest one right now, but will eventually be picked up in a future iteration by the next if
            // when the other docs overtook it.
            if(id == currentDocId){
                id = enumWeightTuple.postingsEnum.nextDoc();
                if(id == NO_MORE_DOCS){
                    continue;
                }
            }

            // check if the current posting enum is one of the next currentDocs
            if(id < smallestNextDocId && id > currentDocId){
                smallestNextDocId = id;
            }
        }

        currentDocId = smallestNextDocId;

        return currentDocId;
    }

    /**
     * Advances to the first beyond the current whose document number is greater
     * than or equal to <i>target</i>, and returns the document number itself.
     * Exhausts the iterator and returns {@link #NO_MORE_DOCS} if <i>target</i>
     * is greater than the highest document number in the set.
     * <p>
     * The behavior of this method is <b>undefined</b> when called with
     * <code> target &le; current</code>, or after the iterator has exhausted.
     * Both cases may result in unpredicted behavior.
     * <p>
     * When <code> target &gt; current</code> it behaves as if written:
     * <p>
     * <pre class="prettyprint">
     * int advance(int target) {
     * int doc;
     * while ((doc = nextDoc()) &lt; target) {
     * }
     * return doc;
     * }
     * </pre>
     * <p>
     * Some implementations are considerably more efficient than that.
     * <p>
     * <b>NOTE:</b> this method may be called with {@link #NO_MORE_DOCS} for
     * efficiency by some Scorers. If your implementation cannot efficiently
     * determine that it should exhaust, it is recommended that you check for that
     * value in each call to this method.
     * <p>
     *
     * @param target
     * @since 2.9
     */
    @Override
    public int advance(int target) throws IOException {
        return slowAdvance(target);
    }

    /**
     * Returns the estimated cost of this {@link DocIdSetIterator}.
     * <p>
     * This is generally an upper bound of the number of documents this iterator
     * might match, but may be a rough heuristic, hardcoded value, or otherwise
     * completely inaccurate.
     */
    @Override
    public long cost() {
        long sum = 0;
        for (PostingsEnumWeightTuple enumWeightTuple : enumWeightTuples) {
            sum += enumWeightTuple.postingsEnum.cost();
        }
        return sum;
    }
}
