/**
 * Created by aliciadelrio on 12/02/18.
 */
public class UniversalHash {
    // all methods static; private constructor.
    private UniversalHash() {}


    /**
     * Generates 32 bit hash from int (32 bits) of given length
     * and seed parameters
     *
     * @param data int to hash
     * @param length length of the array to hash (N in the slides)
     * @param a,b random params of the function
     * @param p > length, prime number
     * @return 32 bit hash of the given array
     */
    public static int hash32(final int data, int length, int a, int b, int p) {
        return (((a * data + b) % p ) % length);
    }
}
