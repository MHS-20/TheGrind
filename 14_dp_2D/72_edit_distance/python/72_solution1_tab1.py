class Solution:
    def minDistance(self, word1: str, word2: str) -> int:
        if word1 == word2:
            return 0
        n, m = len(word1), len(word2)
        memo = [[0] * (m + 1) for _ in range(n + 1)]

        memo[n][m] = 0
        for idx in range(n):
            memo[idx][m] = n - idx

        for idx in range(m):
            memo[n][idx] = m - idx

        for j in range(m - 1, -1, -1):
            for i in range(n - 1, -1, -1):
                if word1[i] == word2[j]:
                    memo[i][j] = memo[i + 1][j + 1]
                else:
                    memo[i][j] = 1 + min(
                        memo[i + 1][j], memo[i][j + 1], memo[i + 1][j + 1]
                    )
        return memo[0][0]
