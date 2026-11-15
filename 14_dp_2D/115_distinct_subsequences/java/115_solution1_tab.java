class Solution {
    public int numDistinct(String s, String t) {
        int n = s.length(), m = t.length();
        int[][] memo = new int[n + 1][m + 1]; // int overflow wraps, never UB

        for (int idx = 0; idx <= n; idx++) {
            memo[idx][m] = 1;
        }

        for (int j = m - 1; j >= 0; j--) {
            for (int i = n - 1; i >= 0; i--) {
                int ways = 0;
                if (s.charAt(i) == t.charAt(j))
                    ways += memo[i + 1][j + 1];
                ways += memo[i + 1][j];
                memo[i][j] = ways;
            }
        }

        return memo[0][0];
    }
}
