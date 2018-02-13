import java.util.*;

/**
 * Brute force implementation of the similarity searcher. The Jaccard similarity is computed for all pairs and the most similar ones are selected.
 * 
 * @author Toon Van Craenendonck
 *
 */
public abstract class Search {

	TwitterReader reader;

	public Search(TwitterReader reader){
		this.reader = reader;
	}

    public Map<Integer, Set<Integer>> getShingledTweets(){
        Map<Integer, Set<Integer>> docToShingle = new HashMap<Integer, Set<Integer>>();
        int id = 0;
        while (reader.hasNext()){
            docToShingle.put(id,reader.next());
            id++;
        }
        return docToShingle;
    }

    public abstract Set<SimilarPair> getSimilarPairsAboveThreshold(double threshold);


	/**
	 * Jaccard similarity between two sets.
	 * @param set1
	 * @param set2
	 * @return the similarity
	 */
	public <T> double jaccardSimilarity(Set<T> set1, Set<T> set2) {
		Set<T> union = new HashSet<T>(set1);
		union.addAll(set2);

		Set<T> intersection = new HashSet<T>(set1);
		intersection.retainAll(set2);

		if (union.size() == 0){
			return 0;
		}
		return (double) intersection.size() / union.size();
	}

}
