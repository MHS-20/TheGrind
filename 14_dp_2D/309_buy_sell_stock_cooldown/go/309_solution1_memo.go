package dp

func maxProfit(prices []int) int {
	n := len(prices)
	memo := make([][2]int, n)
	for i := range memo {
		memo[i] = [2]int{-1, -1}
	}

	var dpf func(day, holding int) int
	dpf = func(day, holding int) int {
		if day >= n {
			return 0
		}
		if memo[day][holding] != -1 {
			return memo[day][holding]
		}

		profit := dpf(day+1, holding)
		if holding == 0 {
			profit = max(profit, dpf(day+1, 1)-prices[day])
		} else {
			profit = max(profit, dpf(day+2, 0)+prices[day])
		}

		memo[day][holding] = profit
		return profit
	}

	return dpf(0, 0)
}
