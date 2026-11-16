class Solution {
    public boolean isMatch(String s, String p) {
        int n = s.length(), m = p.length();
        // dp[i][j]: does s[i..] match p[j..]?
        boolean[][] dp = new boolean[n + 1][m + 1];

        // base case: empty pattern matches only the empty string
        dp[n][m] = true; // dp[i][m] for i < n stays false

        for (int i = n; i >= 0; i--) { // row i reads row i+1 → bottom-up
            for (int j = m - 1; j >= 0; j--) { // reads j+1, j+2 → right-to-left
                boolean match = i < n && (s.charAt(i) == p.charAt(j) || p.charAt(j) == '.');

                if (j + 1 < m && p.charAt(j + 1) == '*') {
                    dp[i][j] = dp[i][j + 2] || // zero copies of x*
                            (match && dp[i + 1][j]); // one more copy
                } else {
                    dp[i][j] = match && dp[i + 1][j + 1];
                }
            }
        }
        return dp[0][0];
    }
}
