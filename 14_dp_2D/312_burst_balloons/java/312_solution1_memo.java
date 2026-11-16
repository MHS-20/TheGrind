import java.util.Arrays;

class Solution {
    private int dpf(int l, int r, int[] nums, int[][] memo) {
        if (l > r)
            return 0;
        if (memo[l][r] != -1)
            return memo[l][r];

        int best = 0;
        for (int k = l; k <= r; k++) {
            int coins = nums[l - 1] * nums[k] * nums[r + 1] // k burst last
                    + dpf(l, k - 1, nums, memo) // left part
                    + dpf(k + 1, r, nums, memo); // right part
            best = Math.max(best, coins);
        }
        return memo[l][r] = best;
    }

    public int maxCoins(int[] nums) {
        int n = nums.length + 2;
        int[] v = new int[n];
        v[0] = v[n - 1] = 1;
        System.arraycopy(nums, 0, v, 1, nums.length);

        int[][] memo = new int[n][n];
        for (int[] row : memo)
            Arrays.fill(row, -1);
        return dpf(1, n - 2, v, memo);
    }
}
