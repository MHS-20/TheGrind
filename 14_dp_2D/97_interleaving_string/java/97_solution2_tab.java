class Solution {
    public boolean isInterleave(String s1, String s2, String s3) {
        int n = s1.length(), m = s2.length();
        if (n + m != s3.length())
            return false;

        boolean[][] dp = new boolean[n + 1][m + 1];

        dp[n][m] = true;
        for (int i = n; i >= 0; i--) {
            for (int j = m; j >= 0; j--) {
                if (i == n && j == m)
                    continue;

                boolean res = false;
                if (i < n && dp[i + 1][j])
                    res = res || s1.charAt(i) == s3.charAt(i + j);
                if (j < m && dp[i][j + 1])
                    res = res || s2.charAt(j) == s3.charAt(i + j);
                dp[i][j] = res;
            }
        }

        return dp[0][0];
    }
}
