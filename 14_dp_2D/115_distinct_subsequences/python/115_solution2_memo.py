import sys


class Solution:
    def numDistinct(self, s: str, t: str) -> int:
        n, m = len(s), len(t)
        sys.setrecursionlimit(max(1000, n + 100))  # depth can reach len(s)
        memo = [[-1] * m for _ in range(n)]

        def dpf(i: int, j: int) -> int:
            if j == m:
                return 1
            if i == n:
                return 0
            if memo[i][j] != -1:
                return memo[i][j]

            memo[i][j] = 0
            if s[i] == t[j]:
                memo[i][j] += dpf(i + 1, j + 1)
            memo[i][j] += dpf(i + 1, j)
            return memo[i][j]

        return dpf(0, 0)
