impl Solution {
    pub fn min_distance(word1: String, word2: String) -> i32 {
        if word1 == word2 {
            return 0;
        }
        let (a, b) = (word1.as_bytes(), word2.as_bytes());
        let (n, m) = (a.len(), b.len());
        let mut memo = vec![vec![0i32; m + 1]; n + 1];

        memo[n][m] = 0;
        for idx in 0..n {
            memo[idx][m] = (n - idx) as i32;
        }

        for idx in 0..m {
            memo[n][idx] = (m - idx) as i32;
        }

        for j in (0..m).rev() {
            for i in (0..n).rev() {
                memo[i][j] = if a[i] == b[j] {
                    memo[i + 1][j + 1]
                } else {
                    1 + memo[i + 1][j].min(memo[i][j + 1]).min(memo[i + 1][j + 1])
                };
            }
        }
        memo[0][0]
    }
}
