package dp

func maxCoins(nums []int) int {
	v := append(append([]int{1}, nums...), 1)
	n := len(v)
	dp := make([][]int, n)
	for i := range dp {
		dp[i] = make([]int, n)
	}

	for l := n - 2; l >= 1; l-- { // rows bottom-up: dp[k+1][..] ready
		for r := l; r <= n-2; r++ { // cols left-right: dp[l][k-1] ready
			for k := l; k <= r; k++ {
				coins := v[l-1]*v[k]*v[r+1] + dp[l][k-1] + dp[k+1][r]
				dp[l][r] = max(dp[l][r], coins)
			}
		}
	}
	return dp[1][n-2]
}
