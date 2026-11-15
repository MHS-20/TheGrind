impl Solution {
    pub fn is_interleave(s1: String, s2: String, s3: String) -> bool {
        let (a, b, c) = (s1.as_bytes(), s2.as_bytes(), s3.as_bytes());
        let (n, m) = (a.len(), b.len());
        if n + m != c.len() {
            return false;
        }

        let mut dp = vec![vec![false; m + 1]; n + 1];

        dp[n][m] = true;
        for i in (0..=n).rev() {
            for j in (0..=m).rev() {
                if i == n && j == m {
                    continue;
                }

                let mut res = false;
                if i < n && dp[i + 1][j] {
                    res = res || a[i] == c[i + j];
                }
                if j < m && dp[i][j + 1] {
                    res = res || b[j] == c[i + j];
                }
                dp[i][j] = res;
            }
        }

        dp[0][0]
    }
}
