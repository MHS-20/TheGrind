package dp

import "sort"

func jobScheduling(startTime []int, endTime []int, profit []int) int {
	n := len(profit)
	jobs := make([][3]int, n)
	for i := range jobs {
		jobs[i] = [3]int{startTime[i], endTime[i], profit[i]}
	}

	sort.Slice(jobs, func(a, b int) bool { return jobs[a][1] < jobs[b][1] })

	dp := make([]int, n+1)

	for i := 0; i < n; i++ {
		start, p := jobs[i][0], jobs[i][2]

		left, right := 0, i
		for left < right {
			mid := left + (right-left)/2

			if jobs[mid][1] <= start {
				left = mid + 1
			} else {
				right = mid
			}
		}

		dp[i+1] = max(dp[i], dp[left]+p)
	}

	return dp[n]
}
