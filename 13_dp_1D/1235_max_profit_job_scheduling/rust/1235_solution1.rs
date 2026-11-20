impl Solution {
    pub fn job_scheduling(start_time: Vec<i32>, end_time: Vec<i32>, profit: Vec<i32>) -> i32 {
        let n = profit.len();
        let mut jobs: Vec<(i32, i32, i32)> = (0..n)
            .map(|i| (start_time[i], end_time[i], profit[i]))
            .collect();

        jobs.sort_unstable_by_key(|job| job.1);

        let mut dp = vec![0; n + 1];

        for i in 0..n {
            let (start, _, p) = jobs[i];

            let (mut left, mut right) = (0, i);
            while left < right {
                let mid = left + (right - left) / 2;

                if jobs[mid].1 <= start {
                    left = mid + 1;
                } else {
                    right = mid;
                }
            }

            dp[i + 1] = dp[i].max(dp[left] + p);
        }

        dp[n]
    }
}
