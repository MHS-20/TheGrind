package dp

func numDistinct(s string, t string) int {
	n, m := len(s), len(t)
	memo := make([][]int, n) // -1 = not computed
	for i := range memo {
		memo[i] = make([]int, m)
		for j := range memo[i] {
			memo[i][j] = -1
		}
	}

	var dpf func(i, j int) int
	dpf = func(i, j int) int {
		if j == m {
			return 1
		}
		if i == n {
			return 0
		}
		if memo[i][j] != -1 {
			return memo[i][j]
		}

		ways := 0
		if s[i] == t[j] {
			ways += dpf(i+1, j+1)
		}
		ways += dpf(i+1, j)

		// keep within u32 range so nothing overflows and -1 stays a free sentinel
		memo[i][j] = ways & 0xFFFFFFFF
		return memo[i][j]
	}

	return int(int32(dpf(0, 0)))
}
