
# Usage

The extension classes in this repository can be used in different integration points in Lucene & Solr.

Version: The packages are build for **Lucene 6.6.0 & Solr 6.6.0** 

## Solr

The main integration with Solr works through the: *SimilarityParserPlugin* which extends the *[QParserPlugin](https://wiki.apache.org/solr/SolrPlugins#QParserPlugin)*.
It allows to integrate with an api that provides similar words (see dependencies for more) for query terms. These similar words are used in the query to produce better results.

- The plugin hooks itself into the logging system and emits log messages, where applicable.
- The plugin uses the analyzer pipeline to process the query string; that is defined for the field to query. (This field is set in the ``<requestHandler>`` as ``<df>fieldName</df>`` or the query string) 


**The following steps are needed to use the *SimilarityParserPlugin* in a Solr core:**
 
 1. Get the JAR of the extension classes:
    - Either by compiling the source files (Extensions project) with maven: ``mvn package`` 
    - Or by taking a pre-compiled version
    
 2. Copy the JAR into the ``/lib`` directory of the Solr core home directory
 (This has to be done for every core, where you want to use the plugin)
 
 3. Make the plugin available (and the default) via the ``solrconfig.xml`` of the core *(add the tags inside the top level ``<config>`` tag)*
    1. Add a new ``queryParser`` tag 
    ````xml
    <queryParser name="similarityApiParser" class="at.ac.tuwien.ifs.parser.SimilarityParserPlugin"/>
    ````
    2. Set a ``<requestHandler>`` to use the new parser
    *(You can change the default ``/select`` handler or add a new handler with a new name - like in the code below; You can also use a different requestHandler class: (solr.MoreLikeThisHandler))*
    
    ````xml
      <requestHandler name="/similarity-query" class="solr.SearchHandler">
        <lst name="defaults">
    
            ... other parameters
    
            <str name="defType">similarityApiParser</str>

            <str name="query:method">GT</str>

            <str name="api:type">real or mock</str>
            <str name="api:failOnNotConnected">true or false</str>
            <str name="api:url">http://localhost:5000/api/v1.0/relatedterms</str>
            <str name="api:optionalParams">vector_method=we;similarity_method=cos;filter_method=threshold;filter_value=0.7</str>
    
        </lst>
      </requestHandler>
    ````
    
 4. The external api needs to run, and be accessible from the Solr instance
    
**Explanation of ``<requestHandler>`` Params:**

- **defType** sets the default parser for the request handler to be the similarityApiParser

- **query:method** ["GT" or "ET"] switsches between the generalized translation model and the extended translation model

- **api:type** ["real" or "mock"] sets which ``ISimilarityApi`` is used
- **api:failOnNotConnected** ["true" or "false"] default=false, if set to true a query fails (!) if it can't connect to the api otherwise it only logs the problem and does the search with the query terms only
- **api:url** The url of the similarity api  
- **api:optionalParams** a string of optional parameters, that are parsed (by ; and =) and added to the request payload when contacting the api. In the example the default parameters are shown that are used when the config string is omitted (see the api documentation for all possible values) 

**Similarity Classes**

*Important Note: If you change the similarity class, you have to re-index your data! Similarity classes are used during the indexing phase to calculate & store specific custom values (like the document length).*

For the generalized translation model (only the term frequency is changed) every similarity class can be used. If BM25 is used it is recommended to not use the built-in Lucene version. The built-in Lucene version compresses the document length into 1-byte and therefore looses accuracy. Leonid Boytsov made a fixed BM25 version available on [GitHub](https://github.com/searchivarius/AccurateLuceneBM25) and an [explanation with measurements](http://searchivarius.org/blog/accurate-bm25-similarity-lucene-follow).

The extension package provides the fixed BM25 via the class: ``at.ac.tuwien.ifs.query.BM25SimilarityLossless``. It can be set in the Solr definition of indexed fields.

The extended translation model is more tightly coupled with the similarity classes. It changes parts of statistics that are combined in the similarity classes. In general every similarity class can be used. The extended model tries to recognize classes that use Lucene's 1-byte document length compression. In special cases, the user should check the implementation with the paper if it uses the correct formulas. This was tested with: ``at.ac.tuwien.ifs.query.BM25SimilarityLossless``, ``BM25Similarity``, and ``LMDirichletSimilarity``. 

**Field Analyzer**

Be aware, that the *SimilarityParser* uses the specified analyzer pipeline for the specified search field to tokenize and process each tokenized term (stemming, stop words, etc..) as set in the configuration (schema.xml) **before** the terms are send as a list to the similarity api. 
The analyzer has to use the same processing pipeline as the terms in the similarity api, otherwise the terms are not matched in the api or are not found in the index. The api returns already processed terms. 

*It is very important that the pipelines match in the whole system.*


## Lucene 

When working with pure Lucene, without Solr, it is possible to use the *AugmentedTermQuery* class in place of any other query class when constructing a search. 
The *AugmentedTermQuery* needs a list of similar terms provided in the constructor. The similar terms can be gathered through the *SimilarityApi* or some other in-process method for example. See the LuceneEvaluation project for an example usage.
