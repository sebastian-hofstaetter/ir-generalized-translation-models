import at.ac.tuwien.ifs.query.BM25SimilarityLossless;
import org.apache.commons.cli.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.benchmark.byTask.feeds.*;
import org.apache.lucene.benchmark.byTask.utils.Config;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Main class, is used to index TREC-8 (given path) into a lucene index
 */
public class Indexer {

    private static CommandLine parsedArgs;

    public static void main(String[] args) {

        Options options = new Options();

        options.addRequiredOption("o","out-index-dir",true,
                "directory for the index");

        options.addRequiredOption("d","data-folder",true,
                "where the to be indexed files are");

        options.addRequiredOption("t","type",true,
                "lossless/1-byte - document length");

        options.addRequiredOption("a","analyzer",true,
                "english/stop-lower-only");

        CommandLineParser parser = new DefaultParser();
        try {
            parsedArgs = parser.parse( options, args );
        }catch (ParseException e){
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "indexer", options );
            return;
        }

        try {

            File indexFolder = new File(parsedArgs.getOptionValue("o"));
            if(!indexFolder.exists()){
                indexFolder.mkdirs();
            }

            Directory dir = FSDirectory.open(Paths.get(parsedArgs.getOptionValue("o")));
            Analyzer analyzer=null;

            switch (parsedArgs.getOptionValue("a")){
                case "english":
                    analyzer = new EnglishAnalyzer(StopWords.nltkStopWords());
                    break;
                case "stop-lower-only":
                    analyzer = new StopAnalyzer(StopWords.nltkStopWords());
                    break;
                default:
                    throw new RuntimeException("analyzer not supported: "+parsedArgs.getOptionValue("a"));
            }

            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            iwc.setRAMBufferSizeMB(256.0 * 4);

            switch (parsedArgs.getOptionValue("t")){
                case "lossless":
                    iwc.setSimilarity(new BM25SimilarityLossless(1.2f,0.6f));
                    break;
                case "1-byte":
                    iwc.setSimilarity(new BM25Similarity(1.2f,0.6f));
                    break;
                default:
                    throw new RuntimeException("type not supported: "+parsedArgs.getOptionValue("t"));
            }

            IndexWriter writer = new IndexWriter(dir, iwc);

            indexWithTrecContentSource(parsedArgs.getOptionValue("d"),writer);

            writer.close();

        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void indexWithTrecContentSource(String dataDir, IndexWriter index) throws IOException {
        final long tStart = System.currentTimeMillis();
        int docCount = 0;
        TrecContentSource tcs = createTrecSource(dataDir);

        System.out.println("Start indexing ...");

        while (true) {
            DocData dd = new DocData();

            try {
                dd = tcs.getNextDocData(dd);
            } catch (Exception e) {
                if (e instanceof NoMoreDataException) {
                    break;
                } else {
                    System.err.println("Failed: " + e.getMessage());
                    continue;
                }
            }

            Document doc = getDocumentFromDocData(dd);
            if (doc == null) {
                break;
            }

            docCount++;

            if ((docCount % 10000) == 0) {
                System.out.println("Total MB: " + tcs.getTotalBytesCount()/1000000 + " \t Docs: " + docCount + " (" + (System.currentTimeMillis() - tStart) / 1000.0 + " sec)");
            }

            index.addDocument(doc);
        }

        System.out.println("----- Fnished ----  (" + (System.currentTimeMillis() - tStart) / 1000.0 + " sec)");
        System.out.println("Total MB: " + tcs.getTotalBytesCount()/1000000);
        System.out.println("Total items: " + tcs.getTotalItemsCount());

    }

    // from https://github.com/lintool/IR-Reproducibility/blob/master/systems/lucene/ingester/src/main/java/luceneingester/TrecIngester.java
    private static TrecContentSource createTrecSource(String dataDir) {

        System.out.println("Gather files ... (this might take a long while)");

        TrecContentSource tcs = new TrecContentSource();
        Properties props = new Properties();
        props.setProperty("print.props", "false");
        props.setProperty("content.source.verbose", "false");
        props.setProperty("content.source.excludeIteration", "true");
        props.setProperty("docs.dir", dataDir);
        props.setProperty("trec.doc.parser", "org.apache.lucene.benchmark.byTask.feeds.TrecParserByPath");
        props.setProperty("content.source.forever", "false");
        tcs.setConfig(new Config(props));
        try {
            tcs.resetInputs();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return tcs;
    }

    private static Document getDocumentFromDocData(DocData dd) {
        Document doc = new Document();

        doc.add(new StringField("docname", dd.getName(), Field.Store.YES));
        doc.add(new TextField("body",dd.getTitle() + " " + dd.getBody(), Field.Store.NO));

        return doc;
    }
}
