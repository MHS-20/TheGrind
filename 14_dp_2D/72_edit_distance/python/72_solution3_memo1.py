import sys


class Solution:
    def minDistance(self, word1: str, word2: str) -> int:
        if word1 == word2:
            return 0
        n, m = len(word1), len(word2)
        sys.setrecursionlimit(max(1000, 2 * (n + m) + 100))  # depth can reach n + m
        memo = [[-1] * (m + 1) for _ in range(n + 1)]

        def dpf(i: int, j: int) -> int:
            if i == n:
                return m - j
            if j == m:
                return n - i
            if memo[i][j] != -1:
                return memo[i][j]

            if word1[i] == word2[j]:
                memo[i][j] = dpf(i + 1, j + 1)
                return memo[i][j]

            memo[i][j] = 1 + min(dpf(i + 1, j + 1), dpf(i + 1, j), dpf(i, j + 1))
            return memo[i][j]

        return dpf(0, 0)
