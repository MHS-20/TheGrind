# Max Money from Non-Overlapping Intervals

Each trip `[start, end, rate]` covers the days `start..end` inclusive and pays `rate` per day, so choosing it earns `(end - start + 1) * rate`. Choose trips that share no day and return the largest total.

Example: `[[1,3,2], [2,5,1], [4,6,3]]` gives `15`, by choosing `[1,3,2]` (6) and `[4,6,3]` (9).

Solution idea: sort the trips by end day. `dp[i]` is the best total using the first `i` trips. For trip `i`, binary search for the number of earlier trips that end before it starts, then `dp[i+1] = max(dp[i], dp[j] + pay_i)`. O(n log n).

Closest LeetCode problem: 1235, Maximum Profit in Job Scheduling. It is the same DP, but the profit is given directly and a job may start on the day the previous one ends.
