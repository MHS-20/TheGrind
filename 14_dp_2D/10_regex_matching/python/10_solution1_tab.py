class Solution:
    def isMatch(self, s: str, p: str) -> bool:
        n, m = len(s), len(p)
        # dp[i][j]: does s[i..] match p[j..]?
        dp = [[False] * (m + 1) for _ in range(n + 1)]

        # base case: empty pattern matches only the empty string
        dp[n][m] = True  # dp[i][m] for i < n stays False

        for i in range(n, -1, -1):  # row i reads row i+1 → bottom-up
            for j in range(m - 1, -1, -1):  # reads j+1, j+2 → right-to-left
                match = i < n and (s[i] == p[j] or p[j] == ".")

                if j + 1 < m and p[j + 1] == "*":
                    dp[i][j] = dp[i][j + 2] or (  # zero copies of x*
                        match and dp[i + 1][j]  # one more copy
                    )
                else:
                    dp[i][j] = match and dp[i + 1][j + 1]
        return dp[0][0]
