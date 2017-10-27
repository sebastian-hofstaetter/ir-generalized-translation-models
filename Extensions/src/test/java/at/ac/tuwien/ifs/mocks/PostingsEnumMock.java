package at.ac.tuwien.ifs.mocks;

import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;

/**
 * Simple mock for the <code>{@link PostingsEnum}</code> class. Uses arrays for different values.
 */
public class PostingsEnumMock extends PostingsEnum {

    private int[] ids;
    private int[] frequencies;
    private int currentPosition = -1;

    public PostingsEnumMock(int[] ids, int[] frequencies){

        if(ids.length != frequencies.length){
            throw new IllegalArgumentException("ids & frequencies must have same length");
        }

        this.ids = ids;
        this.frequencies = frequencies;
    }

    @Override
    public int freq() throws IOException {

        if(currentPosition == -1){
            return -1;
        }

        if(currentPosition >= this.frequencies.length){
            throw new RuntimeException("Iterator is already exhausted !");
        }

        return this.frequencies[currentPosition];
    }

    @Override
    public int nextPosition() throws IOException {
        return -1;
    }

    @Override
    public int startOffset() throws IOException {
        return -1;
    }

    @Override
    public int endOffset() throws IOException {
        return -1;
    }

    @Override
    public BytesRef getPayload() throws IOException {
        return null;
    }

    @Override
    public int docID() {

        if(currentPosition == -1){
            return -1;
        }

        if(currentPosition == this.ids.length){
            return NO_MORE_DOCS;
        }

        return ids[currentPosition];
    }

    @Override
    public int nextDoc() throws IOException {
        if(currentPosition >= this.ids.length - 1){
            currentPosition ++;
            return NO_MORE_DOCS;
        }

        currentPosition ++;

        return this.ids[currentPosition];
    }

    @Override
    public int advance(int target) throws IOException {
        return slowAdvance(target);
    }

    @Override
    public long cost() {
        return 0;
    }
}
