import at.ac.tuwien.ifs.query.BM25SimilarityLossless;
import org.apache.commons.cli.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.benchmark.byTask.feeds.*;
import org.apache.lucene.benchmark.byTask.utils.Config;
import org.apache.lucene.benchmark.quality.utils.DocNameExtractor;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.BooleanClause;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Main class, is used to index TREC-8 (given path) into a lucene index
 */
public class MsMarcoSearcher {

    private static CommandLine parsedArgs;

    public static void main(String[] args) throws Exception {

        Options options = new Options();

        options.addRequiredOption("i", "index-dir", true, "directory for the index");

        options.addRequiredOption("q", "query-file", true, "collection file to be indexed");

        options.addRequiredOption("o", "output-file", true, "top 1000 output file");

        options.addRequiredOption("t", "type", true, "lossless/1-byte - document length");

        options.addRequiredOption("a", "analyzer", true, "english/stop-lower-only");

        CommandLineParser parser = new DefaultParser();
        try {
            parsedArgs = parser.parse(options, args);
        } catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("searcher", options);
            return;
        }

        try {

            Directory dir = FSDirectory.open(Paths.get(parsedArgs.getOptionValue("i")));
            Analyzer analyzer = null;

            switch (parsedArgs.getOptionValue("a")) {
            case "english":
                analyzer = new EnglishAnalyzer(StopWords.nltkStopWords());
                break;
            case "stop-lower-only":
                analyzer = new StopAnalyzer(StopWords.nltkStopWords());
                break;
            default:
                throw new RuntimeException("analyzer not supported: " + parsedArgs.getOptionValue("a"));
            }

            FSDirectory indexDirectory = FSDirectory.open(Paths.get(parsedArgs.getOptionValue("i")));
            IndexReader reader = DirectoryReader.open(indexDirectory);
            IndexSearcher searcher = new IndexSearcher(reader);

            switch (parsedArgs.getOptionValue("t")) {
            case "lossless":
                searcher.setSimilarity(new BM25SimilarityLossless(1.2f, 0.6f));
                break;
            case "1-byte":
                searcher.setSimilarity(new BM25Similarity(1.2f, 0.6f));
                break;
            default:
                throw new RuntimeException("type not supported: " + parsedArgs.getOptionValue("t"));
            }

            queryMsMarco(parsedArgs.getOptionValue("q"), parsedArgs.getOptionValue("o"), analyzer, searcher);

            reader.close();
            indexDirectory.close();

        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void queryMsMarco(String queryFile, String outputFile, Analyzer analyzer, IndexSearcher searcher)
            throws Exception {
        final long tStart = System.currentTimeMillis();
        int queryCount = 0;
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
        DocNameExtractor xt = new DocNameExtractor("passage_id");

        Set<String> query_ids = new HashSet<String>();
        try (BufferedReader br = new BufferedReader(new FileReader(queryFile))) {
            String line;
            while ((line = br.readLine()) != null) {

                String[] parts = line.split("\t");

                if(query_ids.contains(parts[0])) continue;
                query_ids.add(parts[0]);

                String[] queryTerms;

                try {
                    queryTerms = termsFromTokenStream(analyzer.tokenStream("passage_text", parts[2]));
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }

                //
                // create the lucene query
                //
                Query query;
                if (queryTerms.length == 1) {
                    query = new TermQuery(new Term("passage_text", queryTerms[0]));
                } else {

                    BooleanQuery.Builder builder = new BooleanQuery.Builder();

                    for (String term : queryTerms) {
                        builder.add(new TermQuery(new Term("passage_text", term)), BooleanClause.Occur.SHOULD);
                    }

                    query = builder.build();
                }

                TopDocs tops = searcher.search(query, 1000);
                ScoreDoc[] scoreDoc = tops.scoreDocs;

                if (scoreDoc.length != 1000) {
                    System.out.println("Attention, got "+scoreDoc.length+" results: " + parts[0]);
                }
                int rank = 1;

                for (ScoreDoc score : scoreDoc) {
                    writer.write(parts[0] + "\t" + xt.docName(searcher, score.doc)  + "\t" + rank + "\t" + score.score + "\n");
                    rank++;
                }

                queryCount++;

                if ((queryCount % 1000) == 0) {
                    System.out.println(" \t Queries: " + queryCount + " ("
                            + (System.currentTimeMillis() - tStart) / 1000.0 + " sec)");
                }
            }
        }

        writer.close();

        System.out.println("----- Fnished ----  (" + (System.currentTimeMillis() - tStart) / 1000.0 + " sec)");
        System.out.println("Total items: " + queryCount);
    }

    private static String[] termsFromTokenStream(TokenStream stream) throws IOException {

        List<String> outputTemp = new ArrayList<>();
        CharTermAttribute charTermAttribute = stream.addAttribute(CharTermAttribute.class);
        stream.reset();
        while (stream.incrementToken()) {
            outputTemp.add(charTermAttribute.toString());
        }
        stream.end();
        stream.close();

        return outputTemp.toArray(new String[0]);
    }

}
