package at.ac.tuwien.ifs.query;
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.index.TermState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.Similarity.SimScorer;
import org.apache.lucene.search.similarities.SimilarityBase;

/**
 * A Query that matches documents containing a mainTerm or at least one similar term
 * and changes the term frequency based on weighted similar terms.
 * This may be combined with other terms with a {@link BooleanQuery}.
 */
public class AugmentedTermQuery extends Query {

    private final Term mainTerm;
    private final TermWeightTuple[] similarTerms;

    private final ModelMethod method;
    /**
     * Constructs a query for the mainTerm and the weighted similarTerms.
     * Both must be non-null.
     */
    public AugmentedTermQuery(ModelMethod method, Term mainTerm, TermWeightTuple[] similarTerms) {
        this.method = method;
        this.mainTerm = Objects.requireNonNull(mainTerm);
        this.similarTerms = Objects.requireNonNull(similarTerms);
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {

        IndexReaderContext context = searcher.getTopReaderContext();

        TermContext mainTermState = null;
        TermContext[] similarStates = new TermContext[similarTerms.length];

        if (needsScores) {

            //
            // get the term contexts, for the main term + for each similar term
            //
            mainTermState = TermContext.build(context, mainTerm);

            for (int i = 0; i < similarTerms.length; i++) {
                similarStates[i] = TermContext.build(context, similarTerms[i].term);
            }
        }

        // else:  do not compute the term states, this will help save seeks in the terms
        //        dict on segments that have a cache entry for this query

        return new AugmentedTermWeight(searcher, needsScores, mainTermState, similarStates);
    }

    /** Prints a user-readable version of this query. */
    @Override
    public String toString(String field) {
        StringBuilder buffer = new StringBuilder();
        if (!mainTerm.field().equals(field)) {
            buffer.append(mainTerm.field());
            buffer.append(":");
        }
        buffer.append(mainTerm.text() + " (");
        for (int i = 0; i < similarTerms.length; i++) {
            buffer.append(similarTerms[i].weight + "*"+similarTerms[i].term.text());
            if(i != similarTerms.length-1){
                buffer.append(";");
            }
        }
        buffer.append(")");

        return buffer.toString();
    }

    /** Returns true iff <code>o</code> is equal to this. */
    @Override
    public boolean equals(Object other) {

        // check class
        if(!sameClassAs(other)){
            return false;
        }

        AugmentedTermQuery otherQuery = (AugmentedTermQuery) other;

        // check method
        if(method.compareTo(otherQuery.method) != 0){
            return false;
        }

        // check main term
        if(!mainTerm.equals(otherQuery.mainTerm)) {
            return false;
        }

        // check similar terms (length, term, weight)
        if(similarTerms.length != otherQuery.similarTerms.length) {
            return false;
        }
        for (int i = 0; i < similarTerms.length; i++) {
            if(!similarTerms[i].term.equals(otherQuery.similarTerms[i].term) ||
               similarTerms[i].weight != otherQuery.similarTerms[i].weight){
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return classHash() ^ mainTerm.hashCode();
    }

    /**
     * Defines the method to extend the query
     */
    public enum ModelMethod{

        /**
         * Changes the term frequency per document to incorporate weighted similar term frequencies
         */
        Generalized,

        /**
         * Generalized + changes collection statistics accordingly
         */
        Extended
    }

    /**
     * Weight class is used by lucene to conduct the search. It provides access to the
     * <code>{@link AugmentedTermScorer}</code>.
     * It cannot be used without the surrounding <code>{@link AugmentedTermQuery}</code>
     */
    private class AugmentedTermWeight extends Weight {

        private final boolean needsScores;

        //
        // references to the similarity instances
        //
        private final Similarity similarity;
        private final Similarity.SimWeight stats;

        //
        // term contexts -> used to access the index
        //
        private final TermContext mainTermStates;
        private final TermContext[] similarTermStates; // corresponding index to the AugmentedTermQuery.similarTerms

        AugmentedTermWeight(IndexSearcher searcher, boolean needsScores, TermContext mainTermStates, TermContext[] similarTermStates)
                throws IOException {

            super(AugmentedTermQuery.this);

            this.needsScores = needsScores;

            this.similarity = searcher.getSimilarity(needsScores);

            this.similarTermStates = similarTermStates;
            this.mainTermStates = mainTermStates;

            debugOutput(searcher, mainTermStates, similarTermStates);

            this.stats = handleStatistics(searcher, needsScores, mainTermStates);
        }

        private Similarity.SimWeight handleStatistics(IndexSearcher searcher, boolean needsScores, TermContext mainTermStates) throws IOException {
            CollectionStatistics collectionStats = null;
            TermStatistics termStats = null;

            if (needsScores) {

                collectionStats = searcher.collectionStatistics(mainTerm.field());
                termStats = searcher.termStatistics(mainTerm, mainTermStates);

                if(method == ModelMethod.Generalized) {

                    //
                    // generalized: just use the main term statistics
                    //
                    collectionStats = searcher.collectionStatistics(mainTerm.field());
                    termStats = searcher.termStatistics(mainTerm, mainTermStates);

                }
                else if(method == ModelMethod.Extended){

                    //
                    // merge the collection statistics
                    //
                    if(similarTerms.length > 0) {

                        //
                        // - document frequency as a set of all docs (not weighted)
                        // - total term freq (weighted)
                        //
                        // : iterate over all found docs to get correct values
                        //
                        int documentSetCount = 0;
                        float weightedSimilarTermFreqSum = 0;
                        float oneMinusWeightedSimilarTermFreqSum = 0;

                        for (LeafReaderContext ctx : searcher.getTopReaderContext().leaves()) {
                            List<PostingsEnumWeightTuple> post = new ArrayList<>();

                            PostingsEnum mainPost = ctx.reader().postings(mainTerm);
                            if (mainPost != null) post.add(new PostingsEnumWeightTuple(mainPost, 1));

                            for (TermWeightTuple t : similarTerms) {
                                PostingsEnum localPost = ctx.reader().postings(t.term);
                                if (localPost != null) post.add(new PostingsEnumWeightTuple(localPost, t.weight));
                            }

                            MultiDocIdSetIterator it = new MultiDocIdSetIterator(post.toArray(new PostingsEnumWeightTuple[0]));

                            while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                                documentSetCount++;

                                for (PostingsEnumWeightTuple tuple : post) {
                                    if (!tuple.postingsEnum.equals(mainPost) && tuple.postingsEnum.docID() == it.docID()) {
                                        weightedSimilarTermFreqSum += tuple.weight * tuple.postingsEnum.freq();
                                        oneMinusWeightedSimilarTermFreqSum += (1 - tuple.weight) * tuple.postingsEnum.freq();
                                    }
                                }
                            }
                        }

                        //System.out.println("count = " + documentSetCount + " vs main stat = " + termStats.docFreq());

                        //System.out.println("one minus sim sum = " + oneMinusWeightedSimilarTermFreqSum);

                        long updatedTotalTermFreq = Math.round(collectionStats.sumTotalTermFreq() - oneMinusWeightedSimilarTermFreqSum);

                        // we can not have less terms than documents (1 document = min 1 term) -> otherwise CollectionStatistic assertion fails
                        if(updatedTotalTermFreq < collectionStats.sumDocFreq()){
                            updatedTotalTermFreq = collectionStats.sumDocFreq();
                        }
                        //System.out.println("updated collection stat  = " +
                        //        updatedTotalTermFreq
                        //        + " , old stat = " + collectionStats.sumTotalTermFreq());

                        //System.out.println("sim freq  = " + weightedSimilarTermFreqSum + " , main freq = " + termStats.totalTermFreq());

                        // set new term stats (all new values)
                        termStats = new TermStatistics(mainTerm.bytes(), documentSetCount, termStats.totalTermFreq() + Math.round(weightedSimilarTermFreqSum));

                        // set new collection stats (only the total term frequency is updated) -> used for avg document length computation
                        collectionStats = new CollectionStatistics(mainTerm.field(),
                                collectionStats.maxDoc(),
                                collectionStats.docCount(),
                                updatedTotalTermFreq,
                                collectionStats.sumDocFreq());
                    }
                    // we only have the main term, no need to iterate over our index twice
                    else{
                        termStats = searcher.termStatistics(mainTerm, mainTermStates);
                    }

                }else{
                    throw new RuntimeException("ModelMethod: "+method.toString()+" not supported");
                }
            } else {
                // we do not need the actual stats, use fake stats with docFreq=maxDoc and ttf=-1
                int maxDoc = searcher.getIndexReader().maxDoc();
                collectionStats = new CollectionStatistics(mainTerm.field(), maxDoc, -1, -1, -1);
                termStats = new TermStatistics(mainTerm.bytes(), maxDoc, -1);
            }

            return similarity.computeWeight(collectionStats, termStats);
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            terms.add(mainTerm);
        }

        @Override
        public String toString() {
            return "weight(" + AugmentedTermQuery.this + ")";
        }

        @Override
        public float getValueForNormalization() {
            return stats.getValueForNormalization();
        }

        @Override
        public void normalize(float queryNorm, float boost) {
            stats.normalize(queryNorm, boost);
        }

        @Override
        public Scorer scorer(LeafReaderContext context) throws IOException {

            // just in case, but it should not be possible to fail here ...
            assert mainTermStates == null || mainTermStates.wasBuiltFor(ReaderUtil.getTopLevelContext(context)) : "The top-reader used to create Weight is not the same as the current reader's top-reader (" + ReaderUtil.getTopLevelContext(context);;

            short flag = needsScores ? PostingsEnum.FREQS : PostingsEnum.NONE;

            //
            // main term -> PostingsEnum
            //
            TermsEnum termsEnum = getTermsEnum(context, mainTermStates, mainTerm);
            PostingsEnum docs = null;
            if (termsEnum != null) {
                docs = termsEnum.postings(null, flag);
            }else{
                return null; // todo remove this and replace it with a check that similar terms are null as well and
                             // keep going if they are not null !!
            }

            //
            // similar terms -> PostingsEnum
            //

            List<PostingsEnumWeightTuple> similarEnums = new ArrayList<>();
            for (int i = 0; i < similarTerms.length; i++) {
                TermWeightTuple wt = similarTerms[i];
                TermsEnum enums = getTermsEnum(context, similarTermStates[i], wt.term);
                if (enums != null) {
                    similarEnums.add(
                            new PostingsEnumWeightTuple(
                                    enums.postings(null, flag),
                                    wt.weight
                            )
                    );
                }
            }

            SimScorer simScorer = null;
            if(method == ModelMethod.Generalized) {
                // main term context only -> as access by the similarity class
                simScorer = similarity.simScorer(stats, context);
            }
            else if(method == ModelMethod.Extended) {

                //
                // wrap context for all saved document length - similar terms
                //

                // we have to use reflection, should not have any side effects
                Class<LeafReaderContext> contextClass = LeafReaderContext.class;
                Constructor<LeafReaderContext> declaredConstructor;
                LeafReaderContext fakeContext = null;

                // sim base is used by all language model classes
                boolean simUsesOneByteDocCompression = similarity instanceof SimilarityBase || similarity instanceof BM25Similarity;

                try {
                    declaredConstructor = contextClass.getDeclaredConstructor(LeafReader.class);
                    declaredConstructor.setAccessible(true);
                    fakeContext = declaredConstructor.newInstance(new LeafReaderOverride(similarEnums, context.reader(), simUsesOneByteDocCompression));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                simScorer = similarity.simScorer(stats, fakeContext);
            }
            else {
                throw new RuntimeException("ModelMethod: " +  method.toString() + " not supported");
            }

            return new AugmentedTermScorer(this, docs, similarEnums, simScorer);
        }

        /**
         * Returns a {@link TermsEnum} positioned at this weights Term or null if
         * the mainTerm does not exist in the given context
         */
        private TermsEnum getTermsEnum(LeafReaderContext context,TermContext termContext,Term term) throws IOException {
            if (termContext != null) {
                // TermQuery either used as a Query or the mainTerm states have been provided at construction time
                assert termContext.wasBuiltFor(ReaderUtil.getTopLevelContext(context)) : "The top-reader used to create Weight is not the same as the current reader's top-reader (" + ReaderUtil.getTopLevelContext(context);
                final TermState state = termContext.get(context.ord);
                if (state == null) { // mainTerm is not present in that reader
                    assert termNotInReader(context.reader(), term) : "no termstate found but mainTerm exists in reader mainTerm=" + mainTerm;
                    return null;
                }
                final TermsEnum termsEnum = context.reader().terms(term.field()).iterator();
                termsEnum.seekExact(term.bytes(), state);
                return termsEnum;
            } else {
                // TermQuery used as a filter, so the mainTerm states have not been built up front
                Terms terms = context.reader().terms(term.field());
                if (terms == null) {
                    return null;
                }
                final TermsEnum termsEnum = terms.iterator();
                if (termsEnum.seekExact(term.bytes())) {
                    return termsEnum;
                } else {
                    return null;
                }
            }
        }

        private boolean termNotInReader(LeafReader reader, Term term) throws IOException {
            // only called from assert
            // System.out.println("TQ.termNotInReader reader=" + reader + " mainTerm=" +
            // field + ":" + bytes.utf8ToString());
            return reader.docFreq(term) == 0;
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            Scorer scorer = scorer(context);
            if (scorer != null) {
                int newDoc = scorer.iterator().advance(doc);
                if (newDoc == doc) {
                    float freq = scorer.freq();
                    SimScorer docScorer = similarity.simScorer(stats, context);
                    Explanation freqExplanation = Explanation.match(freq, "termFreq=" + freq);
                    Explanation scoreExplanation = docScorer.explain(doc, freqExplanation);
                    return Explanation.match(
                            scoreExplanation.getValue(),
                            "weight(" + getQuery() + " in " + doc + ") ["
                                    + similarity.getClass().getSimpleName() + "], result of:",
                            scoreExplanation);
                }
            }
            return Explanation.noMatch("no matching mainTerm");
        }

        private void debugOutput(IndexSearcher searcher, TermContext mainTermStates, TermContext[] similarTermStates) throws IOException {
            if(mainTerm.text().length()<19){
                System.out.println("\nmain: "+mainTerm.text()+ new String(new char[19 - mainTerm.text().length()]).replace("\0", " ") +" * 1    -> "+searcher.termStatistics(mainTerm, mainTermStates).docFreq()+" docs ("+searcher.termStatistics(mainTerm, mainTermStates).totalTermFreq()+" occ.)");
            }
            else{
                System.out.println("\nmain: "+mainTerm.text()+" * 1    -> "+searcher.termStatistics(mainTerm, mainTermStates).docFreq()+" docs ("+searcher.termStatistics(mainTerm, mainTermStates).totalTermFreq()+" occ.)");

            }
            for (int i = 0; i < similarTerms.length; i++) {
                TermWeightTuple t = similarTerms[i];
                String text = "      " +  t.term.text();
                String numbers =  String.format("%.02f", t.weight) + " -> " + searcher.termStatistics(t.term, similarTermStates[i]).docFreq()+" docs ("+searcher.termStatistics(t.term, similarTermStates[i]).totalTermFreq()+" occ.)";
                if(text.length()<25) {
                    System.out.println(text + new String(new char[25 - text.length()]).replace("\0", " ") + " * "
                            + numbers);
                }else{
                    System.out.println(text + " * " + numbers);
                }
            }
        }

    }
}
