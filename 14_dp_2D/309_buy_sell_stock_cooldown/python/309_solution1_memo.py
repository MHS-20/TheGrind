from typing import List


class Solution:
    def maxProfit(self, prices: List[int]) -> int:
        n = len(prices)
        memo = [[-1, -1] for _ in range(n)]

        def dpf(day: int, holding: int) -> int:
            if day >= n:
                return 0
            if memo[day][holding] != -1:
                return memo[day][holding]

            profit = dpf(day + 1, holding)
            if holding == 0:
                profit = max(profit, dpf(day + 1, 1) - prices[day])
            else:
                profit = max(profit, dpf(day + 2, 0) + prices[day])

            memo[day][holding] = profit
            return profit

        return dpf(0, 0)
