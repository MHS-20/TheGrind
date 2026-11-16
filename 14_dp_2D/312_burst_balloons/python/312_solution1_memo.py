from typing import List


class Solution:
    def maxCoins(self, nums: List[int]) -> int:
        v = [1] + nums + [1]
        n = len(v)
        memo = [[-1] * n for _ in range(n)]

        def dpf(l: int, r: int) -> int:
            if l > r:
                return 0
            if memo[l][r] != -1:
                return memo[l][r]

            best = 0
            for k in range(l, r + 1):
                coins = (
                    v[l - 1] * v[k] * v[r + 1]  # k burst last
                    + dpf(l, k - 1)  # left part
                    + dpf(k + 1, r)  # right part
                )
                best = max(best, coins)
            memo[l][r] = best
            return best

        return dpf(1, n - 2)
