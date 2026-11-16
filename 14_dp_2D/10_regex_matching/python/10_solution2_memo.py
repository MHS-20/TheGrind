import sys
from typing import List, Optional


class Solution:
    def isMatch(self, s: str, p: str) -> bool:
        n, m = len(s), len(p)
        sys.setrecursionlimit(max(1000, 2 * (n + m) + 100))  # depth can reach n + m
        memo: List[List[Optional[bool]]] = [[None] * (m + 1) for _ in range(n + 1)]

        def dpf(i: int, j: int) -> bool:
            # pattern used up: match only if the string is used up too
            if j == m:
                return i == n
            if memo[i][j] is not None:
                return memo[i][j]

            # does the current pattern char match s[i]? (s may already be used up)
            match = i < n and (s[i] == p[j] or p[j] == ".")

            if j + 1 < m and p[j + 1] == "*":
                # treat "x*" as one unit: either use zero copies (skip it),
                # or match one copy and stay on "x*" to allow more
                res = dpf(i, j + 2) or (match and dpf(i + 1, j))
            else:
                # plain char or '.': must match exactly one character
                res = match and dpf(i + 1, j + 1)

            memo[i][j] = res
            return res

        return dpf(0, 0)
