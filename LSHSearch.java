import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/**
 *
 */
public class LSHSearch extends Search {
    private int lshRows;
    private int lshBands;
    private int sigRows;            // rows/hash functions of the signature matrix
    private int nShingles;          // max number of shingles/signature hashing values
    private Function <Integer, Integer>[] sigHashFunctions;

    public LSHSearch(TwitterReader reader, int bands, int rows) {
        super(reader);
        this.lshRows = rows;
        this.lshBands = bands;
        this.sigRows = rows * bands;
        this.nShingles = reader.shingler.nShingles;
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
    private int hashShingle(int shingle, int h){
        return sigHashFunctions[h].apply(shingle);
    }



    public Set<SimilarPair> getSimilarPairsAboveThreshold(double threshold) {
        //Set<SimilarPair> cands = new HashSet<SimilarPair>();
        Map<Integer, Set<Integer>> docToShingle = super.getShingledTweets();
        int docs = docToShingle.keySet().size();

        // create the signature matrix
        // 1. initialize sizes
        int[][] sig = new int[this.sigRows][docs];
        Arrays.fill(sig, this.nShingles + 1);                   // fill with infinite = fill with maximum hashing value
        // 2. go through each shingle and doc,
        for (Integer doc: docToShingle.keySet()){                    // iterate columns (docs)
            for (Integer shingleRow: docToShingle.get(doc)){         // iterate rows (shingles, range 0 - maxShingles)
                for (int i = 0; i < this.sigRows; i++){              // apply each hash function
                    // 3. hash row number when a 1 is found, store min value
                    sig[i][doc] = (hashShingle(shingleRow, i) < sig[i][doc]) ?
                            hashShingle(shingleRow, i) : sig[i][doc];
                }
            }
        }


        // divide by rows and bands, etc


        /*
        for (Integer obj1 : docToShingle.keySet()){
            if (obj1 % 10000 == 0){
                System.out.println("at " + obj1);
            }
            for (Integer obj2 : docToShingle.keySet()){
                if (obj1 < obj2){
                    double sim = jaccardSimilarity(docToShingle.get(obj1),docToShingle.get(obj2));
                    if (sim > threshold){
                        cands.add(new SimilarPair(obj1, obj2, sim));
                    }
                }
            }
        }
        */
        //return cands;
        return null;
    }
}
