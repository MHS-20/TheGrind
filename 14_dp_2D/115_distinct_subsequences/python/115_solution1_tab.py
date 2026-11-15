class Solution:
    def numDistinct(self, s: str, t: str) -> int:
        n, m = len(s), len(t)
        memo = [[0] * (m + 1) for _ in range(n + 1)]

        for idx in range(n + 1):
            memo[idx][m] = 1

        for j in range(m - 1, -1, -1):
            for i in range(n - 1, -1, -1):
                ways = 0
                if s[i] == t[j]:
                    ways += memo[i + 1][j + 1]
                ways += memo[i + 1][j]
                memo[i][j] = ways

        return memo[0][0]
