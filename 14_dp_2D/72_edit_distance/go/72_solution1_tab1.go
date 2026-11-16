package dp

func minDistance(word1 string, word2 string) int {
	if word1 == word2 {
		return 0
	}
	n, m := len(word1), len(word2)
	memo := make([][]int, n+1)
	for i := range memo {
		memo[i] = make([]int, m+1)
	}

	memo[n][m] = 0
	for idx := 0; idx < n; idx++ {
		memo[idx][m] = n - idx
	}

	for idx := 0; idx < m; idx++ {
		memo[n][idx] = m - idx
	}

	for j := m - 1; j >= 0; j-- {
		for i := n - 1; i >= 0; i-- {
			if word1[i] == word2[j] {
				memo[i][j] = memo[i+1][j+1]
			} else {
				memo[i][j] = 1 + min(memo[i+1][j], memo[i][j+1], memo[i+1][j+1])
			}
		}
	}
	return memo[0][0]
}
