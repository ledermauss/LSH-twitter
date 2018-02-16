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

        short[][] sig = createSignatureMatrix();
        System.out.println("Signature matrix has been created");

        // finding the candidates with lsh
        for (int band = 0; band < this.lshBands; band++) {       // iterate bands
            Map<Long, HashSet<Integer>> bandCandidatePairs = getBandCandidatePairs(band, sig); //this was quick

            // extract pairs with similarity above the threshold from the band, store them
            System.out.println("Extracting the candidate pairs from the hashes");
            int iter = 0;
            for (HashSet<Integer> cands : bandCandidatePairs.values()) {
                if (iter % 100000== 0) System.out.println ("Pairs to examine: " + bandCandidatePairs.values().size());
                if (iter % 100000 == 0) System.out.println ("Examining candidates set number " + iter);
                if (cands.size() > 1) {
                    // optimization: do not check pairs already in the set
                    Set<SimilarPair> setSims = getSimilarPairsAboveThresholdFromSet(threshold, cands, sig);
                    sims.addAll(setSims);
                }
                iter++;
            }
        }
        System.out.println("Found similar pairs");
        return sims;
    }


    private short[][] createSignatureMatrix(){
        // 1. initialize sizes
        short[][] sig = new short[this.sigRows][this.nDocs];
        for (short[] row: sig)
            Arrays.fill(row, (short) (this.nShingles + 1));              // fill with infinite = fill with maximum hashing value

        // 2. go through each shingle and doc,
        int doc = 0;
        while (super.reader.hasNext()){                                 // iterate docs
            if (doc % 100000 == 0) System.out.println("Current doc is: " + doc);
            for (int shingle: super.reader.next()){                     // iterate shingles per doc
                for (int h = 0; h < this.sigRows; h++){                 // hash shingle (row) and add to sig matrix
                    sig[h][doc] = (hashShingle(shingle, h) < sig[h][doc]) ?
                            (short) hashShingle(shingle, h) : sig[h][doc];
                }
            }
            doc++;
        }
        return sig;
    }



    public Map<Long, HashSet<Integer>> getBandCandidatePairs(int band, short[][] sig){
        System.out.println("Looking for pair candidates in band " + band);
        int b = band * this.lshRows;
        Map<Long, HashSet<Integer>> bandCandidatePairs = new HashMap<Long, HashSet<Integer>>();
        // find the candidate pairs of a band
        for (int doc = 0; doc < this.nDocs; doc++) {                   // iterate signatures (columns) and hash them
            String docSignature = "";

            if (doc % 100000 == 0) System.out.println("Concating the rows of doc " + doc);
            for (int row = b; row < b + this.lshRows; row++) {  // create signature (concat values)
                docSignature = docSignature.concat(Integer.toString(sig[row][doc]));
            }

            long docSignatureHash = MurmurHash.hash64(docSignature, this.murmurSeed);

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

    public Set<SimilarPair> getSimilarPairsAboveThresholdFromSet(double threshold, Set<Integer> docs, short[][] sig){
        // it is assumed that the list is already ordered (since docs are were inserted in ascending order)
        Set<SimilarPair>  simPairs = new HashSet<SimilarPair>();
        for (int doc1: docs) {
            for (int doc2: docs)
                if (doc1 < doc2 && !simPairs.contains(new SimilarPair(doc1, doc2, 0))) {
                    double sim = computeSignaturesSimilarity(sig, doc1, doc2);
                    if (sim > threshold) simPairs.add(new SimilarPair(doc1, doc2, sim));
                }
        }
        return simPairs;
    }

    public Set<SimilarPair> getSimilarPairsAboveThresholdFromList(double threshold, List<Integer> docs, short[][] sig){
        // it is assumed that the list is already ordered (since docs are were inserted in ascending order)
        Set<SimilarPair>  simPairs = new HashSet<SimilarPair>();
        int size = docs.size();
        for (int i = 0; i < size; i++) {
            int doc1 = docs.get(i);
            for (int j = i + 1; j < size; j++) {
                int doc2 = docs.get(i);
                //if (doc1 < doc2) { // avoids duplicities, but unnecesary with arrays
                if (!simPairs.contains(new SimilarPair(doc1, doc2, 0))) {
                    double sim = computeSignaturesSimilarity(sig, doc1, doc2);
                    if (sim > threshold) simPairs.add(new SimilarPair(doc1, doc2, sim));
                }
            }
        }
        return simPairs;
    }



    public double computeSignaturesSimilarity(short[][] sig, int doc1, int doc2){
        int union = 0;
        for (int i = 0; i < this.sigRows; i++){
            if (sig[i][doc1] == sig[i][doc2]) union++; // rows matching in the signature
        }
        return (double) union / this.sigRows;
    }



}
