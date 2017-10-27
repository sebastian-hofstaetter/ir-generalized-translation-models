package at.ac.tuwien.ifs.query;

import org.apache.lucene.index.*;
import org.apache.lucene.search.Sort;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.SmallFloat;

import java.io.IOException;
import java.util.List;

/**
 * Used to feed the similarity class an updated document length
 * Only implements <code>{@link #getNormValues(String)}</code> all other methods return null / 0
 */
class LeafReaderOverride extends LeafReader {

    private List<PostingsEnumWeightTuple> similarEnums;
    private LeafReader originalReader;
    private boolean useOneByteCompression;

    public LeafReaderOverride(List<PostingsEnumWeightTuple> similarEnums,LeafReader originalReader,boolean useOneByteCompression){
        this.similarEnums = similarEnums;
        this.originalReader = originalReader;
        this.useOneByteCompression = useOneByteCompression;
    }

    @Override
    public NumericDocValues getNormValues(String field) throws IOException {
        return new NumericDocValuesOverride(originalReader.getNormValues(field));
    }

    public class NumericDocValuesOverride extends NumericDocValues {

        private NumericDocValues originalReaderNormValues;

        public NumericDocValuesOverride(NumericDocValues originalReaderNormValues) {

            this.originalReaderNormValues = originalReaderNormValues;
        }

        /**
         * Read the value from the originalReader and subtracts the (1 - weight) * term frequency for every
         * similar term (the PostingEnums are at the right position, because this is call in the Similarity.Score method)
         *
         * Never returns a value smaller than 1 (behavior of Similarity classes)
         *
         * If useOneByteCompression is set to true, the value is decoded, subtracted and encoded again before it is returned
         */
        @Override
        public long get(int docID) {

            if(originalReaderNormValues==null){
                return 1;
            }

            float original = originalReaderNormValues.get(docID);

            if(useOneByteCompression){
                original = decodeNormValue((byte)original);
            }

            float subtract = 0;
            try {
                for (PostingsEnumWeightTuple tuple:similarEnums){
                    if(tuple.postingsEnum.docID() == docID){
                            subtract += (1 - tuple.weight) * tuple.postingsEnum.freq();
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("could not read freq() from postings enum");
            }

            //System.out.println(String.format("%.02f", subtract) + "\t" + original);

            if(useOneByteCompression){
                byte value = encodeNormValue(1, original - subtract);
                //if(subtract>.5){
                //    System.out.println("could move: "+ subtract);
                //}
                //if(value != originalReaderNormValues.get(docID)){
                //    System.out.println("changed! from: "+ originalReaderNormValues.get(docID) + " to:" + value);
                //}
                return value;
            }
            else{
                return Math.max(1, Math.round(original - subtract));
            }
        }
    }

    //
    //    --- 1-byte compression stuff ----
    //      (taken from SimilarityBase)
    //

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
    private static float decodeNormValue(byte norm) {
        return NORM_TABLE[norm & 0xFF];  // & 0xFF maps negative bytes to positive above 127
    }

    /** Encodes the length to a byte via SmallFloat. */
    private static byte encodeNormValue(float boost, float length) {
        return SmallFloat.floatToByte315((boost / (float) Math.sqrt(length)));
    }


    //
    //    ----- ignore following --------
    //

    @Override
    public void addCoreClosedListener(CoreClosedListener listener) {

    }

    @Override
    public void removeCoreClosedListener(CoreClosedListener listener) {

    }

    @Override
    public Fields fields() throws IOException {
        return null;
    }

    @Override
    public NumericDocValues getNumericDocValues(String field) throws IOException {
        return null;
    }

    @Override
    public BinaryDocValues getBinaryDocValues(String field) throws IOException {
        return null;
    }

    @Override
    public SortedDocValues getSortedDocValues(String field) throws IOException {
        return null;
    }

    @Override
    public SortedNumericDocValues getSortedNumericDocValues(String field) throws IOException {
        return null;
    }

    @Override
    public SortedSetDocValues getSortedSetDocValues(String field) throws IOException {
        return null;
    }

    @Override
    public Bits getDocsWithField(String field) throws IOException {
        return null;
    }

    @Override
    public FieldInfos getFieldInfos() {
        return null;
    }

    @Override
    public Bits getLiveDocs() {
        return null;
    }

    @Override
    public PointValues getPointValues() {
        return null;
    }

    @Override
    public void checkIntegrity() throws IOException {

    }

    @Override
    public Sort getIndexSort() {
        return null;
    }

    @Override
    public Fields getTermVectors(int docID) throws IOException {
        return null;
    }

    @Override
    public int numDocs() {
        return 0;
    }

    @Override
    public int maxDoc() {
        return 0;
    }

    @Override
    public void document(int docID, StoredFieldVisitor visitor) throws IOException {

    }

    @Override
    protected void doClose() throws IOException {

    }
}
