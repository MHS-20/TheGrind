impl Solution {
    pub fn is_match(s: String, p: String) -> bool {
        let (s, p) = (s.as_bytes(), p.as_bytes());
        let (n, m) = (s.len(), p.len());
        // dp[i][j]: does s[i..] match p[j..]?
        let mut dp = vec![vec![false; m + 1]; n + 1];

        // base case: empty pattern matches only the empty string
        dp[n][m] = true; // dp[i][m] for i < n stays false

        for i in (0..=n).rev() {
            // row i reads row i+1 → bottom-up
            for j in (0..m).rev() {
                // reads j+1, j+2 → right-to-left
                let is_match = i < n && (s[i] == p[j] || p[j] == b'.');

                dp[i][j] = if j + 1 < m && p[j + 1] == b'*' {
                    dp[i][j + 2] // zero copies of x*
                        || (is_match && dp[i + 1][j]) // one more copy
                } else {
                    is_match && dp[i + 1][j + 1]
                };
            }
        }
        dp[0][0]
    }
}
