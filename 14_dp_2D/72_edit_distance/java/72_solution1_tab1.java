class Solution {
    public int minDistance(String word1, String word2) {
        if (word1.equals(word2))
            return 0;
        int n = word1.length(), m = word2.length();
        int[][] memo = new int[n + 1][m + 1];

        memo[n][m] = 0;
        for (int idx = 0; idx < n; idx++) {
            memo[idx][m] = n - idx;
        }

        for (int idx = 0; idx < m; idx++) {
            memo[n][idx] = m - idx;
        }

        for (int j = m - 1; j >= 0; j--) {
            for (int i = n - 1; i >= 0; i--) {
                if (word1.charAt(i) == word2.charAt(j))
                    memo[i][j] = memo[i + 1][j + 1];
                else {
                    memo[i][j] = 1 + Math.min(memo[i + 1][j], Math.min(memo[i][j + 1], memo[i + 1][j + 1]));
                }
            }
        }
        return memo[0][0];
    }
}
