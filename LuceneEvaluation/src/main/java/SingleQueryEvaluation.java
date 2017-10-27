import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.SmallFloat;

import java.io.Console;
import java.nio.file.Paths;

/**
 * This class is used as a playground for extension classes
 */
public class SingleQueryEvaluation {


    /** Norm to document length map. */
    private static final float[] NORM_TABLE = new float[256];

    static {
        for (int i = 1; i < 256; i++) {
            float floatNorm = SmallFloat.byte315ToFloat((byte)i);
            NORM_TABLE[i] = 1.0f / (floatNorm * floatNorm);
        }
        NORM_TABLE[0] = 1.0f / NORM_TABLE[255]; // otherwise inf
    }

    /** Decodes a normalization factor (document length) stored in an index.
     * @see #encodeNormValue(float,float)
     */
    protected static float decodeNormValue(byte norm) {
        return NORM_TABLE[norm & 0xFF];  // & 0xFF maps negative bytes to positive above 127
    }

    /** Encodes the length to a byte via SmallFloat. */
    protected static byte encodeNormValue(float boost, float length) {
        return SmallFloat.floatToByte315((boost / (float) Math.sqrt(length)));
    }


    public static void main(String[] args) throws Exception {


        for(int i = 0;i<2000;i++){
            System.out.println(i + "\t"  + decodeNormValue(encodeNormValue(1, i)));
        }

        //FSDirectory indexDir = FSDirectory.open(Paths.get(args[0]));
        //IndexReader reader = DirectoryReader.open(indexDir);
        //IndexSearcher searcher = new IndexSearcher(reader);
        //searcher.setSimilarity(new BM25Similarity(0.9f, 0.4f));
//
        //Query query = new TermQuery(new Term("body","search"));
//
        //TopDocs docs = searcher.search(query,10);
//
        //for(ScoreDoc doc : docs.scoreDocs){
        //    System.out.println(doc.doc + " - "  + doc.score);
        //}
    }
}
