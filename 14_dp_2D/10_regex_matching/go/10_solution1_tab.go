package dp

func isMatch(s string, p string) bool {
	n, m := len(s), len(p)
	// dp[i][j]: does s[i..] match p[j..]?
	dp := make([][]bool, n+1)
	for i := range dp {
		dp[i] = make([]bool, m+1)
	}

	// base case: empty pattern matches only the empty string
	dp[n][m] = true // dp[i][m] for i < n stays false

	for i := n; i >= 0; i-- { // row i reads row i+1 → bottom-up
		for j := m - 1; j >= 0; j-- { // reads j+1, j+2 → right-to-left
			match := i < n && (s[i] == p[j] || p[j] == '.')

			if j+1 < m && p[j+1] == '*' {
				dp[i][j] = dp[i][j+2] || // zero copies of x*
					(match && dp[i+1][j]) // one more copy
			} else {
				dp[i][j] = match && dp[i+1][j+1]
			}
		}
	}
	return dp[0][0]
}
