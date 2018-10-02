Implementation of a Nearest Neighbour algorithm to find pairs of tweets with the
highest similarity. Similarity is measured using shingles as features and jaccard 
similarity as a measure. 

# Shingling and hashing
Shingles are n-grams of consecutive words. For example, on the sentence _This is
a tweet_, using shingles of length 2 one obtains the following features:

{(this, is), (is, a), (a, tweet)}

To avoid sparsity and to prevent memory overflow, shingles are hashed using 
the Murmur Hash. That way, one tweet can be described as a set of integers 
(see TwitterReader class). 
Both the shingle length and the number of buckets of the hashing function are 
tuning parameters, specified with the `-shingleLength` and `-nShingles` option,
respectively.




```java
java Runner -algorithm lsh -threshold 0.3 -maxFiles 30000 -inputPath data/tweetsSubset -outputPath path_to_csv -shingleLength 3 -nShingles 300000 -rows 1 -bands 40 
```
