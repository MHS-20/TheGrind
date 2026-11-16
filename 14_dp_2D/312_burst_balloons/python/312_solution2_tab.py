from typing import List


class Solution:
    def maxCoins(self, nums: List[int]) -> int:
        v = [1] + nums + [1]
        n = len(v)
        dp = [[0] * n for _ in range(n)]

        for l in range(n - 2, 0, -1):  # rows bottom-up: dp[k+1][..] ready
            for r in range(l, n - 1):  # cols left-right: dp[l][k-1] ready
                for k in range(l, r + 1):
                    coins = v[l - 1] * v[k] * v[r + 1] + dp[l][k - 1] + dp[k + 1][r]
                    dp[l][r] = max(dp[l][r], coins)
        return dp[1][n - 2]
