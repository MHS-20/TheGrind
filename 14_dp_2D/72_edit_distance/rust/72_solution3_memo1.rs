impl Solution {
    fn dpf(i: usize, j: usize, a: &[u8], b: &[u8], memo: &mut Vec<Vec<i32>>) -> i32 {
        if i == a.len() {
            return (b.len() - j) as i32;
        }
        if j == b.len() {
            return (a.len() - i) as i32;
        }
        if memo[i][j] != -1 {
            return memo[i][j];
        }

        if a[i] == b[j] {
            memo[i][j] = Self::dpf(i + 1, j + 1, a, b, memo);
            return memo[i][j];
        }

        let rep = Self::dpf(i + 1, j + 1, a, b, memo);
        let del = Self::dpf(i + 1, j, a, b, memo);
        let ins = Self::dpf(i, j + 1, a, b, memo);
        memo[i][j] = 1 + rep.min(del).min(ins);
        memo[i][j]
    }

    pub fn min_distance(word1: String, word2: String) -> i32 {
        if word1 == word2 {
            return 0;
        }
        let (a, b) = (word1.as_bytes(), word2.as_bytes());
        let mut memo = vec![vec![-1; b.len() + 1]; a.len() + 1];
        Self::dpf(0, 0, a, b, &mut memo)
    }
}
