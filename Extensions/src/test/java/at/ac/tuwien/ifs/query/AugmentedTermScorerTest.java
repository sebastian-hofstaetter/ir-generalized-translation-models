package at.ac.tuwien.ifs.query;

import at.ac.tuwien.ifs.mocks.PostingsEnumMock;
import at.ac.tuwien.ifs.mocks.SimScorerMock;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains unit tests that test the correct behavior of the rolling iterators & sums
 * of the <code>{@link AugmentedTermScorer}</code> class
 */
public class AugmentedTermScorerTest {

    /**
     * Tests only with a single term <code>{@link PostingsEnumMock}</code> and with an empty list of similar terms
     */
    @Test
    public void test_singleTerm() throws IOException {

        // arrange
        PostingsEnumMock mainPostings = new PostingsEnumMock(new int[]{1, 2, 4}, new int[]{2, 5, 1});
        SimScorerMock simScorer = new SimScorerMock();

        AugmentedTermScorer scorer = new AugmentedTermScorer(null, mainPostings, new ArrayList<>(), simScorer);

        // act + assert
        scorer.iterator().nextDoc();
        scorer.score();

        Assert.assertEquals(1, simScorer.getDoc());
        Assert.assertEquals(2, simScorer.getFreq(), 0);


        scorer.iterator().nextDoc();
        scorer.score();

        Assert.assertEquals(2, simScorer.getDoc());
        Assert.assertEquals(5, simScorer.getFreq(), 0);

        scorer.iterator().nextDoc();
        scorer.score();

        Assert.assertEquals(4, simScorer.getDoc());
        Assert.assertEquals(1, simScorer.getFreq(), 0);
    }

    /**
     * Tests with a main term <code>{@link PostingsEnumMock}</code> and with 1 similar term
     * Similar term has different entries that are also found
     */
    @Test
    public void test_twoTerms() throws IOException {

        // arrange - postings have 2 docs in common: 1 & 4
        PostingsEnumMock mainPostings = new PostingsEnumMock(new int[]{1, 2, 4}, new int[]{2, 5, 1});
        PostingsEnumMock similar_1_Postings = new PostingsEnumMock(new int[]{1, 3, 4, 5}, new int[]{2, 1, 1, 1});
        List<PostingsEnumWeightTuple> similarList = new ArrayList<>();
        similarList.add(new PostingsEnumWeightTuple(similar_1_Postings, 0.5f));

        SimScorerMock simScorer = new SimScorerMock();

        AugmentedTermScorer scorer = new AugmentedTermScorer(null, mainPostings, similarList, simScorer);

        // act + assert
        scorer.iterator().nextDoc(); // 1
        scorer.score();

        // sum of main + similar * weight
        Assert.assertEquals(1, simScorer.getDoc());
        Assert.assertEquals(2 + 2 * 0.5, simScorer.getFreq(), 0.000001);

        scorer.iterator().nextDoc(); // 2
        scorer.score();

        // main only
        Assert.assertEquals(2, simScorer.getDoc());
        Assert.assertEquals(5, simScorer.getFreq(), 0);

        scorer.iterator().nextDoc(); // 3
        scorer.score();

        // sum of main + similar * weight
        Assert.assertEquals(3, simScorer.getDoc());
        Assert.assertEquals(1 * 0.5, simScorer.getFreq(), 0.000001);

        scorer.iterator().nextDoc(); // 4
        scorer.score();

        // sum of main + similar * weight
        Assert.assertEquals(4, simScorer.getDoc());
        Assert.assertEquals(1 + 1 * 0.5, simScorer.getFreq(), 0.000001);

        scorer.iterator().nextDoc(); // 5
        scorer.score();

        // sum of main + similar * weight
        Assert.assertEquals(5, simScorer.getDoc());
        Assert.assertEquals(0.5, simScorer.getFreq(), 0.000001);

    }

    /**
     * Tests with a main term <code>{@link PostingsEnumMock}</code> and with many (15) similar term postings
     * Similar terms have different lengths (shorter, exact, longer) and ascending ids, main ids skip some ids
     * all main ids are checked in a loop
     */
    @Test
    public void test_ManySimilar() throws IOException {

        // arrange
        float[] expectedFrequenciesAtPosition = new float[15];
        int[] mainIds = new int[]{2, 4, 6, 7, 8, 9, 10, 11, 12};
        int[] mainFrequencies = new int[]{10, 10, 10, 10, 10, 10, 10, 10, 10};
        PostingsEnumMock mainPostings = new PostingsEnumMock(mainIds, mainFrequencies);
        for (int i = 0; i < mainIds.length; i++) {
            expectedFrequenciesAtPosition[mainIds[i]] = mainFrequencies[i];
        }

        // similar postings -> length: 0, 1, 2, ..., 10
        int[][] similarIds = new int[15][];
        int[][] similarFrequencies = new int[15][];


        PostingsEnumMock[] similarPostings = new PostingsEnumMock[15];
        List<PostingsEnumWeightTuple> similarList = new ArrayList<>();

        for (int i = 0; i < similarPostings.length; i++) {

            similarIds[i] = new int[i];
            similarFrequencies[i] = new int[i];
            float weight = 1f / (i + 1f);

            for (int t = 0; t < i; t++) {
                similarIds[i][t] = t;
                similarFrequencies[i][t] = t + 1;

                expectedFrequenciesAtPosition[t] += (t + 1) * weight;
            }

            similarPostings[i] = new PostingsEnumMock(similarIds[i], similarFrequencies[i]);
            similarList.add(new PostingsEnumWeightTuple(similarPostings[i], weight));


        }

        SimScorerMock simScorer = new SimScorerMock();
        AugmentedTermScorer scorer = new AugmentedTermScorer(null, mainPostings, similarList, simScorer);

        // act + assert
        for (int i = 0; i < 14; i++) {
            scorer.iterator().nextDoc();
            scorer.score();

            // sum of main + similar * weight
            // -> sum up similar frequencies by checking if the current id is in the id array
            //      -> getting the index and picking out the frequency
            //      -> multiplying it with its unique weight

            Assert.assertEquals("Iteration: " + i, i, simScorer.getDoc());
            Assert.assertEquals("Iteration: " + i, expectedFrequenciesAtPosition[i], simScorer.getFreq(), 0.000001);
        }
    }
}
