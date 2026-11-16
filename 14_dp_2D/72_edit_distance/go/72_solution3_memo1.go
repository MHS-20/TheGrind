package dp

func minDistance(word1 string, word2 string) int {
	if word1 == word2 {
		return 0
	}
	n, m := len(word1), len(word2)
	memo := make([][]int, n+1)
	for i := range memo {
		memo[i] = make([]int, m+1)
		for j := range memo[i] {
			memo[i][j] = -1
		}
	}

	var dpf func(i, j int) int
	dpf = func(i, j int) int {
		if i == n {
			return m - j
		}
		if j == m {
			return n - i
		}
		if memo[i][j] != -1 {
			return memo[i][j]
		}

		if word1[i] == word2[j] {
			memo[i][j] = dpf(i+1, j+1)
			return memo[i][j]
		}

		memo[i][j] = 1 + min(dpf(i+1, j+1), dpf(i+1, j), dpf(i, j+1))
		return memo[i][j]
	}

	return dpf(0, 0)
}
