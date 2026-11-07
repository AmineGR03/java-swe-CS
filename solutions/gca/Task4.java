package gca;

import java.util.HashMap;
import java.util.Map;

/**
 * Reference solution — Task 4.
 *
 * Running balance: +1 per odd, -1 per even. A subarray (j+1 .. i) is balanced
 * iff balance[i] == balance[j], so for each balance value only its earliest
 * index matters. O(n) time, O(n) space.
 */
public class Task4 {

    public static int longestBalancedSegment(int[] nums) {
        if (nums == null || nums.length == 0) {
            return 0;
        }
        Map<Integer, Integer> firstIndex = new HashMap<>();
        firstIndex.put(0, -1);

        int balance = 0;
        int best = 0;
        for (int i = 0; i < nums.length; i++) {
            // nums[i] % 2 is -1 for negative odds, so compare against 0.
            balance += (nums[i] % 2 != 0) ? 1 : -1;
            Integer seen = firstIndex.get(balance);
            if (seen == null) {
                firstIndex.put(balance, i);
            } else {
                best = Math.max(best, i - seen);
            }
        }
        return best;
    }
}
