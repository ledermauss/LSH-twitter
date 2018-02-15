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
            long[] bandHashes = getBandSignatureHashes(band, sig);
            Set<SimilarPair> bandSims = getSimilarPairsAboveThresholdFromBand(threshold, bandHashes, sig);
            sims.addAll(bandSims);
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


    // idea:  do not hash docs which are already candidate pairs (saves speed?)
    public long[] getBandSignatureHashes(int band, short[][] sig){
        System.out.println("Creating band signatures for band " + band);
        int b = band * this.lshRows;
        long[] bandHashes = new long[this.nDocs];
        // find the candidate pairs of a band
        for (int doc = 0; doc < this.nDocs; doc++) {
            String docSignature = "";
            for (int row = b; row < b + this.lshRows; row++) {  // create signature (concat values)
                docSignature = docSignature.concat(Short.toString(sig[row][doc]));
            }
            long docSignatureHash = MurmurHash.hash64(docSignature, this.murmurSeed);
            bandHashes[doc] = docSignatureHash;
        }
        return bandHashes;
    }


    /*
    For each document in the bandHashes array, looks for the other documents of higher id with same hash.
    Complexity: n + n-1 + n-2.. = n(n-1)/2 (slightly less than n squared)
     */
    public Set<SimilarPair> getSimilarPairsAboveThresholdFromBand(double threshold, long[] bandHash, short[][] sig){
        Set<SimilarPair>  simPairs = new HashSet<SimilarPair>();
        for (int doc1 = 0; doc1 < this.nDocs; doc1++) {
            for (int doc2 = doc1 + 1; doc2 < this.nDocs; doc2++) { // doc2 starts on the following doc
                if (bandHash[doc1] == bandHash[doc2]) {
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
