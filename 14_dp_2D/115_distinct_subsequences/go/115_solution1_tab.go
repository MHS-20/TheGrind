package dp

func numDistinct(s string, t string) int {
	n, m := len(s), len(t)
	memo := make([][]uint64, n+1) // uint64 so intermediate overflow wraps
	for idx := range memo {
		memo[idx] = make([]uint64, m+1)
		memo[idx][m] = 1
	}

	for j := m - 1; j >= 0; j-- {
		for i := n - 1; i >= 0; i-- {
			var ways uint64
			if s[i] == t[j] {
				ways += memo[i+1][j+1]
			}
			ways += memo[i+1][j]
			memo[i][j] = ways
		}
	}

	return int(int32(memo[0][0]))
}
