package dp

func isMatch(s string, p string) bool {
	n, m := len(s), len(p)
	memo := make([][]int, n+1) // -1 = not computed, 0 = false, 1 = true
	for i := range memo {
		memo[i] = make([]int, m+1)
		for j := range memo[i] {
			memo[i][j] = -1
		}
	}

	var dpf func(i, j int) bool
	dpf = func(i, j int) bool {
		// pattern used up: match only if the string is used up too
		if j == m {
			return i == n
		}
		if memo[i][j] != -1 {
			return memo[i][j] == 1
		}

		// does the current pattern char match s[i]? (s may already be used up)
		match := i < n && (s[i] == p[j] || p[j] == '.')
		var res bool

		if j+1 < m && p[j+1] == '*' {
			// treat "x*" as one unit: either use zero copies (skip it),
			// or match one copy and stay on "x*" to allow more
			res = dpf(i, j+2) || (match && dpf(i+1, j))
		} else {
			// plain char or '.': must match exactly one character
			res = match && dpf(i+1, j+1)
		}

		memo[i][j] = 0
		if res {
			memo[i][j] = 1
		}
		return res
	}

	return dpf(0, 0)
}
