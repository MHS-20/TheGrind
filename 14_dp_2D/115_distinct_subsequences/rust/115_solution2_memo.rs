impl Solution {
    fn dpf(i: usize, j: usize, s: &[u8], t: &[u8], memo: &mut Vec<Vec<Option<u32>>>) -> u32 {
        if j == t.len() {
            return 1;
        }
        if i == s.len() {
            return 0;
        }
        if let Some(ways) = memo[i][j] {
            return ways;
        }

        let mut ways = 0u32;
        if s[i] == t[j] {
            ways = Self::dpf(i + 1, j + 1, s, t, memo);
        }
        // wrapping: intermediates may overflow
        ways = ways.wrapping_add(Self::dpf(i + 1, j, s, t, memo));

        memo[i][j] = Some(ways);
        ways
    }

    pub fn num_distinct(s: String, t: String) -> i32 {
        let (s, t) = (s.as_bytes(), t.as_bytes());
        let mut memo = vec![vec![None; t.len()]; s.len()];
        Self::dpf(0, 0, s, t, &mut memo) as i32
    }
}
