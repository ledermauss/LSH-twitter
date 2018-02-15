import java.util.*;
import java.util.function.Function;

/**
 *
 */
public class LSHSearch extends Search {
    private int lshRows;
    private int lshBands;
    private int sigRows;            // rows/hash functions of the signature matrix
    private int nShingles;          // max number of shingles/signature hashing values
    private int nDocs;
    private int murmurSeed;         // murmur hash seed for the band buckets
    private Function <Integer, Integer>[] sigHashFunctions;

    public LSHSearch(TwitterReader reader, int bands, int rows) {
        super(reader);
        this.lshRows = rows;
        this.lshBands = bands;
        this.sigRows = rows * bands;
        this.murmurSeed = (new Random()).nextInt();
        this.nShingles = super.reader.shingler.nShingles;
        this.nDocs = super.reader.maxDocs;  // updated later if it is actually smaller
        this.sigHashFunctions = new Function[this.sigRows];
        Random rand = new Random();
        int prime = Primes.findLeastPrimeNumber(this.nShingles + 1);

        // init the signature hash functions
        for(int i = 0; i < this.sigRows; i++){
            // in each iteration, new parameters a, b and prime
            this.sigHashFunctions[i] =
                    initHash(rand.nextInt(Integer.MAX_VALUE), rand.nextInt(Integer.MAX_VALUE), prime);
            prime = Primes.findLeastPrimeNumber(prime + 1);
        }
    }


    private Function<Integer, Integer> initHash(int a, int b, int p){
        // lambda: shingle is the argument (int) the generated function expects
        // this.nShingles: max length of the hashValue (max shingles)
        return  shingle ->
                (UniversalHash.hash32(shingle, this.nShingles, a, b, p) & 0x7FFFFFFF);
    }


    // returns the hash of a single, given hashing function index h
    private int hashShingle(int shingle, int h){ return sigHashFunctions[h].apply(shingle); }


    @Override
    public Set<SimilarPair> getSimilarPairsAboveThreshold(double threshold){
        Set<SimilarPair> sims = new HashSet<SimilarPair>();
        boolean[][] shingledTweets= getShingledTweetsMatrix();
        System.out.println("Tweets have been read");

        int[][] sig = createSignatureMatrix(shingledTweets);
        System.out.println("Signature matrix has been created");

        // finding the candidates with lsh
        for (int band = 0; band < this.lshBands; band++) {       // iterate bands
            Map<Integer, HashSet<Integer>> bandCandidatePairs = getBandCandidatePairs(band, sig);

            // extract pairs with similarity above the threshold from the band, store them
            for (Set<Integer> set : bandCandidatePairs.values()) {
                if (set.size() > 1) {
                    Set<SimilarPair> setSims = getSimilarPairsAboveThresholdFromSet(threshold, set, sig);
                    sims.addAll(setSims);
                }
            }
        }
        System.out.println("Found similar pairs");
        return sims;
    }

    public boolean[][] getShingledTweetsMatrix(){
        //booleans should use less memory
        boolean[][] docToShingle = new boolean[this.nShingles][this.nDocs];
        int id = 0;
        while (reader.hasNext()){
            for (int shingle: reader.next()){
                docToShingle[shingle][id] = true;
            }
            id++;
        }
        // modify nDocs if finally the maximum is not met
        this.nDocs = id + 1;
        return docToShingle;
    }


    private int[][] createSignatureMatrix(boolean[][] docToShingle){
        // 1. initialize sizes
        int[][] sig = new int[this.sigRows][this.nDocs];
        for (int[] row: sig)
            Arrays.fill(row, this.nShingles + 1);              // fill with infinite = fill with maximum hashing value

        // 2. go through each shingle and doc,
        for (int doc = 0; doc < this.nDocs; doc++){                             // iterate columns (docs)
            for (int shingle = 0; shingle < this.nShingles; shingle++){         // iterate rows (shingles)
                for (int h = 0; h < this.sigRows; h++){              // apply each hash function (rows in sig matrix)
                    // 3. hash row number when a 1 is found, store min value
                    if (docToShingle[shingle][doc]) sig[h][doc] = (hashShingle(shingle, h) < sig[h][doc]) ?
                                hashShingle(shingle, h) : sig[h][doc];
                }
            }
        }
        return sig;
    }


    public Map<Integer, HashSet<Integer>> getBandCandidatePairs(int band, int[][] sig){
        int b = band * this.lshRows;
        Map<Integer, HashSet<Integer>> bandCandidatePairs = new HashMap<Integer, HashSet<Integer>>();
        // find the candidate pairs of a band
        for (int doc = 0; doc < this.nDocs; doc++) {                   // iterate signatures (columns) and hash them
            String docSignature = "";

            for (int row = b; row < b + this.lshRows; row++) {  // create signature (concat values)
                // bandSignature += sig[row][doc], concat is faster
                docSignature = docSignature.concat(Integer.toString(sig[row][doc]));
            }

            int docSignatureHash = MurmurHash.hash32(docSignature, this.murmurSeed);

            // add the candidate to the corresponding set, or create a new entry with the doc on the set
            if (bandCandidatePairs.containsKey(docSignatureHash)){
                bandCandidatePairs.get(docSignatureHash).add(doc);
            } else {
                HashSet<Integer> s = new HashSet<Integer>();
                s.add(doc);
                bandCandidatePairs.put(docSignatureHash, s);
            }
        }
        return bandCandidatePairs;
    }


    public Set<SimilarPair> getSimilarPairsAboveThresholdFromSet(double threshold, Set<Integer> docs, int[][] sig){
        Set<SimilarPair>  simPairs = new HashSet<SimilarPair>();
        for (int doc1 : docs)
            for (int doc2 : docs)
                if (doc1 < doc2){ // avoids duplicities
                    double sim = computeSignaturesSimilarity(sig, doc1, doc2);
                    if (sim > threshold) simPairs.add(new SimilarPair(doc1, doc2, sim));
                }
        return simPairs;
    }


    public double computeSignaturesSimilarity(int[][] sig, int doc1, int doc2){
        int union = 0;
        for (int i = 0; i < this.sigRows; i++){
            if (sig[i][doc1] == sig[i][doc2]) union++; // rows matching in the
        }
        return (double) union / this.sigRows;
    }



}
