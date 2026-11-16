impl Solution {
    pub fn max_coins(nums: Vec<i32>) -> i32 {
        let mut v = vec![1];
        v.extend(nums);
        v.push(1);

        let n = v.len();
        let mut dp = vec![vec![0; n]; n];

        for l in (1..=n - 2).rev() { // rows bottom-up: dp[k+1][..] ready
            for r in l..=n - 2 { // cols left-right: dp[l][k-1] ready
                for k in l..=r {
                    let coins = v[l - 1] * v[k] * v[r + 1] + dp[l][k - 1] + dp[k + 1][r];
                    dp[l][r] = dp[l][r].max(coins);
                }
            }
        }
        dp[1][n - 2]
    }
}
