import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class BruteForceSearch extends Search{

    public BruteForceSearch(TwitterReader reader) {
        super(reader);
    }

    /**
     * Get pairs of objects with similarity above threshold.
     * @param threshold the similarity threshold
     * @return the pairs
     */
    @Override
    public Set<SimilarPair> getSimilarPairsAboveThreshold(double threshold) {

        Map<Integer, Set<Integer>> docToShingle = super.getShingledTweets();

        Set<SimilarPair> cands = new HashSet<SimilarPair>();
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
        return cands;
    }
}
