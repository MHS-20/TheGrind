class Solution {
    public int maxCoins(int[] nums) {
        int n = nums.length + 2;
        int[] v = new int[n];
        v[0] = v[n - 1] = 1;
        System.arraycopy(nums, 0, v, 1, nums.length);

        int[][] dp = new int[n][n];

        for (int l = n - 2; l >= 1; l--) { // rows bottom-up: dp[k+1][..] ready
            for (int r = l; r <= n - 2; r++) { // cols left-right: dp[l][k-1] ready
                for (int k = l; k <= r; k++) {
                    int coins = v[l - 1] * v[k] * v[r + 1] + dp[l][k - 1] + dp[k + 1][r];
                    dp[l][r] = Math.max(dp[l][r], coins);
                }
            }
        }
        return dp[1][n - 2];
    }
}
