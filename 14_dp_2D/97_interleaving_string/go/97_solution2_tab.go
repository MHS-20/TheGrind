package dp

func isInterleave(s1 string, s2 string, s3 string) bool {
	n, m := len(s1), len(s2)
	if n+m != len(s3) {
		return false
	}

	dp := make([][]bool, n+1)
	for i := range dp {
		dp[i] = make([]bool, m+1)
	}

	dp[n][m] = true
	for i := n; i >= 0; i-- {
		for j := m; j >= 0; j-- {
			if i == n && j == m {
				continue
			}

			res := false
			if i < n && dp[i+1][j] {
				res = res || s1[i] == s3[i+j]
			}
			if j < m && dp[i][j+1] {
				res = res || s2[j] == s3[i+j]
			}
			dp[i][j] = res
		}
	}

	return dp[0][0]
}
