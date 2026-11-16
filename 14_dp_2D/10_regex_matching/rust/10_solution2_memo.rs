impl Solution {
    fn dpf(i: usize, j: usize, s: &[u8], p: &[u8], memo: &mut Vec<Vec<Option<bool>>>) -> bool {
        let (n, m) = (s.len(), p.len());
        // pattern used up: match only if the string is used up too
        if j == m {
            return i == n;
        }
        if let Some(res) = memo[i][j] {
            return res;
        }

        // does the current pattern char match s[i]? (s may already be used up)
        let is_match = i < n && (s[i] == p[j] || p[j] == b'.');

        let res = if j + 1 < m && p[j + 1] == b'*' {
            // treat "x*" as one unit: either use zero copies (skip it),
            // or match one copy and stay on "x*" to allow more
            Self::dpf(i, j + 2, s, p, memo) || (is_match && Self::dpf(i + 1, j, s, p, memo))
        } else {
            // plain char or '.': must match exactly one character
            is_match && Self::dpf(i + 1, j + 1, s, p, memo)
        };

        memo[i][j] = Some(res);
        res
    }

    pub fn is_match(s: String, p: String) -> bool {
        let (s, p) = (s.as_bytes(), p.as_bytes());
        let mut memo = vec![vec![None; p.len() + 1]; s.len() + 1];
        Self::dpf(0, 0, s, p, &mut memo)
    }
}
