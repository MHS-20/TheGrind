package dp

func maxCoins(nums []int) int {
	v := append(append([]int{1}, nums...), 1)
	n := len(v)
	memo := make([][]int, n)
	for i := range memo {
		memo[i] = make([]int, n)
		for j := range memo[i] {
			memo[i][j] = -1
		}
	}

	var dpf func(l, r int) int
	dpf = func(l, r int) int {
		if l > r {
			return 0
		}
		if memo[l][r] != -1 {
			return memo[l][r]
		}

		best := 0
		for k := l; k <= r; k++ {
			coins := v[l-1]*v[k]*v[r+1] + // k burst last
				dpf(l, k-1) + // left part
				dpf(k+1, r) // right part
			best = max(best, coins)
		}
		memo[l][r] = best
		return best
	}

	return dpf(1, n-2)
}
