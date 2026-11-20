import java.util.Arrays;

class Solution {
    public int jobScheduling(int[] startTime, int[] endTime, int[] profit) {
        int n = profit.length;
        int[][] jobs = new int[n][];
        for (int i = 0; i < n; i++)
            jobs[i] = new int[] {startTime[i], endTime[i], profit[i]};

        Arrays.sort(jobs, (a, b) -> Integer.compare(a[1], b[1]));

        int[] dp = new int[n + 1];

        for (int i = 0; i < n; i++) {
            int start = jobs[i][0], p = jobs[i][2];

            int left = 0, right = i;
            while (left < right) {
                int mid = left + (right - left) / 2;

                if (jobs[mid][1] <= start)
                    left = mid + 1;
                else
                    right = mid;
            }

            dp[i + 1] = Math.max(dp[i], dp[left] + p);
        }

        return dp[n];
    }
}
