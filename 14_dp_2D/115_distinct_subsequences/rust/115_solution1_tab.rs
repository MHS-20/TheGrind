impl Solution {
    pub fn num_distinct(s: String, t: String) -> i32 {
        let (s, t) = (s.as_bytes(), t.as_bytes());
        let (n, m) = (s.len(), t.len());
        let mut memo = vec![vec![0u64; m + 1]; n + 1];

        for idx in 0..=n {
            memo[idx][m] = 1;
        }

        for j in (0..m).rev() {
            for i in (0..n).rev() {
                let mut ways = 0u64;
                if s[i] == t[j] {
                    ways = memo[i + 1][j + 1];
                }
                // wrapping: intermediates may overflow
                ways = ways.wrapping_add(memo[i + 1][j]);
                memo[i][j] = ways;
            }
        }

        memo[0][0] as i32
    }
}
