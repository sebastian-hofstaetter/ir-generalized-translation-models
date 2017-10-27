package at.ac.tuwien.ifs.query;

import at.ac.tuwien.ifs.mocks.PostingsEnumMock;
import org.apache.lucene.search.DocIdSetIterator;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * Tests the correct behavior of the rolling iteration + weighted sums
 */
public class MultiDocIdSetIteratorTest {

    /**
     * Tests only with a single <code>{@link PostingsEnumMock}</code> as basis for the <code>{@link MultiDocIdSetIterator}</code>
     */
    @Test
    public void test_SingleEntry() throws IOException {

        // arrange
        PostingsEnumMock mainPostings = new PostingsEnumMock(new int[]{1, 2, 4}, new int[]{1, 1, 1});

        MultiDocIdSetIterator postingsEnum = new MultiDocIdSetIterator(
                new PostingsEnumWeightTuple[]{
                        new PostingsEnumWeightTuple(mainPostings,0)
                });

        // act + assert
        postingsEnum.nextDoc();
        Assert.assertEquals(1, postingsEnum.docID());

        postingsEnum.nextDoc();
        Assert.assertEquals(2, postingsEnum.docID());

        postingsEnum.nextDoc();
        Assert.assertEquals(4, postingsEnum.docID());
    }


    /**
     * Tests only with a single empty <code>{@link PostingsEnumMock}</code> as basis for the <code>{@link MultiDocIdSetIterator}</code>
     */
    @Test
    public void test_SingleEmpty() throws IOException {

        // arrange
        PostingsEnumMock mainPostings = new PostingsEnumMock(new int[0], new int[0]);

        MultiDocIdSetIterator postingsEnum = new MultiDocIdSetIterator(
                new PostingsEnumWeightTuple[]{
                        new PostingsEnumWeightTuple(mainPostings,0)
                });

        // act + assert
        postingsEnum.nextDoc();
        Assert.assertEquals(DocIdSetIterator.NO_MORE_DOCS, postingsEnum.docID());
        Assert.assertEquals(DocIdSetIterator.NO_MORE_DOCS, mainPostings.docID());
    }
    
    /**
     * Tests with two <code>{@link PostingsEnumMock}</code>s as basis for the <code>{@link MultiDocIdSetIterator}</code>
     */
    @Test
    public void test_TwoEntries() throws IOException {

        // arrange
        PostingsEnumMock mainPostings = new PostingsEnumMock(new int[]{1, 2, 6}, new int[]{1, 1, 1});
        PostingsEnumMock secondPostings = new PostingsEnumMock(new int[]{2, 3, 4, 6}, new int[]{1, 1, 1, 1});

        MultiDocIdSetIterator postingsEnum = new MultiDocIdSetIterator(
                new PostingsEnumWeightTuple[]{
                        new PostingsEnumWeightTuple(mainPostings,0),
                        new PostingsEnumWeightTuple(secondPostings,0)
                });

        // act + assert
        postingsEnum.nextDoc(); // 1
        Assert.assertEquals(1, postingsEnum.docID());
        Assert.assertEquals(1, mainPostings.docID());

        postingsEnum.nextDoc(); // 2 + 2
        Assert.assertEquals(2, postingsEnum.docID());
        Assert.assertEquals(2, mainPostings.docID());
        Assert.assertEquals(2, secondPostings.docID());

        postingsEnum.nextDoc(); // 3
        Assert.assertEquals(3, postingsEnum.docID());
        Assert.assertEquals(3, secondPostings.docID());
        Assert.assertTrue(3 != mainPostings.docID());

        postingsEnum.nextDoc(); // 4
        Assert.assertEquals(4, postingsEnum.docID());
        Assert.assertEquals(4, secondPostings.docID());
        Assert.assertTrue(4 != mainPostings.docID());

        postingsEnum.nextDoc(); // 6 + 6
        Assert.assertEquals(6, postingsEnum.docID());
        Assert.assertEquals(6, mainPostings.docID());
        Assert.assertEquals(6, secondPostings.docID());

    }


}