import at.ac.tuwien.ifs.query.BM25SimilarityLossless;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
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
import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;

/**
 * Main class, is used to index TREC-8 (given path) into a lucene index
 */
public class Indexer {

    private static String usageText =
            " Hi, please add 2 arguments: \n\t1. the index folder  " +
            "\n\t2. the data folder";

    public static void main(String[] args) {

        if(args.length != 2){
            System.out.println(usageText);
            return;
        }

        try {

            File indexFolder = new File(args[0]);
            if(!indexFolder.exists()){
                indexFolder.mkdirs();
            }

            Directory dir = FSDirectory.open(Paths.get(args[0]));
            Analyzer analyzer = new EnglishAnalyzer(nltkStopWords());

            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            iwc.setRAMBufferSizeMB(256.0 * 4);

            //iwc.setSimilarity(new BM25SimilarityLossless(1.2f,0.6f));
            iwc.setSimilarity(new BM25Similarity(1.2f,0.6f));

            IndexWriter writer = new IndexWriter(dir, iwc);

            indexWithTrecContentSource(args[1],writer);

            writer.close();

        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.out.println("\n\n" + usageText);
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
    private static CharArraySet nltkStopWords(){

        ArrayList<String> c = new ArrayList<>();

        c.add("i");
        c.add("me");
        c.add("my");
        c.add("myself");
        c.add("we");
        c.add("our");
        c.add("ours");
        c.add("ourselves");
        c.add("you");
        c.add("your");
        c.add("yours");
        c.add("yourself");
        c.add("yourselves");
        c.add("he");
        c.add("him");
        c.add("his");
        c.add("himself");
        c.add("she");
        c.add("her");
        c.add("hers");
        c.add("herself");
        c.add("it");
        c.add("its");
        c.add("itself");
        c.add("they");
        c.add("them");
        c.add("their");
        c.add("theirs");
        c.add("themselves");
        c.add("what");
        c.add("which");
        c.add("who");
        c.add("whom");
        c.add("this");
        c.add("that");
        c.add("these");
        c.add("those");
        c.add("am");
        c.add("is");
        c.add("are");
        c.add("was");
        c.add("were");
        c.add("be");
        c.add("been");
        c.add("being");
        c.add("have");
        c.add("has");
        c.add("had");
        c.add("having");
        c.add("do");
        c.add("does");
        c.add("did");
        c.add("doing");
        c.add("a");
        c.add("an");
        c.add("the");
        c.add("and");
        c.add("but");
        c.add("if");
        c.add("or");
        c.add("because");
        c.add("as");
        c.add("until");
        c.add("while");
        c.add("of");
        c.add("at");
        c.add("by");
        c.add("for");
        c.add("with");
        c.add("about");
        c.add("against");
        c.add("between");
        c.add("into");
        c.add("through");
        c.add("during");
        c.add("before");
        c.add("after");
        c.add("above");
        c.add("below");
        c.add("to");
        c.add("from");
        c.add("up");
        c.add("down");
        c.add("in");
        c.add("out");
        c.add("on");
        c.add("off");
        c.add("over");
        c.add("under");
        c.add("again");
        c.add("further");
        c.add("then");
        c.add("once");
        c.add("here");
        c.add("there");
        c.add("when");
        c.add("where");
        c.add("why");
        c.add("how");
        c.add("all");
        c.add("any");
        c.add("both");
        c.add("each");
        c.add("few");
        c.add("more");
        c.add("most");
        c.add("other");
        c.add("some");
        c.add("such");
        c.add("no");
        c.add("nor");
        c.add("not");
        c.add("only");
        c.add("own");
        c.add("same");
        c.add("so");
        c.add("than");
        c.add("too");
        c.add("very");
        c.add("s");
        c.add("t");
        c.add("can");
        c.add("will");
        c.add("just");
        c.add("don");
        c.add("should");
        c.add("now");

        return new CharArraySet(c,true);
    }
}
