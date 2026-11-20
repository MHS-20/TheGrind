from typing import List


class Solution:
    def jobScheduling(
        self, startTime: List[int], endTime: List[int], profit: List[int]
    ) -> int:
        n = len(profit)
        jobs = sorted(zip(startTime, endTime, profit), key=lambda job: job[1])

        dp = [0] * (n + 1)

        for i, (start, _, p) in enumerate(jobs):
            left, right = 0, i
            while left < right:
                mid = (left + right) // 2

                if jobs[mid][1] <= start:
                    left = mid + 1
                else:
                    right = mid

            dp[i + 1] = max(dp[i], dp[left] + p)

        return dp[n]
