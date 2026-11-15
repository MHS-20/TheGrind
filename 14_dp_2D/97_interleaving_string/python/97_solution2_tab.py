class Solution:
    def isInterleave(self, s1: str, s2: str, s3: str) -> bool:
        n, m = len(s1), len(s2)
        if n + m != len(s3):
            return False

        dp = [[False] * (m + 1) for _ in range(n + 1)]

        dp[n][m] = True
        for i in range(n, -1, -1):
            for j in range(m, -1, -1):
                if i == n and j == m:
                    continue

                res = False
                if i < n and dp[i + 1][j]:
                    res = res or s1[i] == s3[i + j]
                if j < m and dp[i][j + 1]:
                    res = res or s2[j] == s3[i + j]
                dp[i][j] = res

        return dp[0][0]
