# Related Work

This is a non-complete list of related work.

### Lucene/Solr Synonym-Expanding EDisMax Parser

https://github.com/healthonnet/hon-lucene-synonyms

https://nolanlawson.com/2012/10/31/better-synonym-handling-in-solr/

Uses QParserPlugin to extend the query with synonyms from static list

### word2vec query expansion component for Apache Lucene

https://github.com/saadtazi/word2vec-query-expansion

Uses lucene filter to expand the query -> Basic query expansion. 
Only single token -> similar words. No weighting (?) as far as I can see.


### General how to extend & use Solr query parsers

http://coding-art.blogspot.co.at/2016/05/writing-custom-solr-query-parser-for.html

https://dzone.com/articles/create-custom-solr-queryparser

https://github.com/o19s/lucene-query-example
