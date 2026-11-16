impl Solution {
    fn dpf(l: usize, r: usize, v: &[i32], memo: &mut Vec<Vec<i32>>) -> i32 {
        if l > r {
            return 0;
        }
        if memo[l][r] != -1 {
            return memo[l][r];
        }

        let mut best = 0;
        for k in l..=r {
            let coins = v[l - 1] * v[k] * v[r + 1] // k burst last
                + Self::dpf(l, k - 1, v, memo) // left part
                + Self::dpf(k + 1, r, v, memo); // right part
            best = best.max(coins);
        }
        memo[l][r] = best;
        best
    }

    pub fn max_coins(nums: Vec<i32>) -> i32 {
        let mut v = vec![1];
        v.extend(nums);
        v.push(1);

        let n = v.len();
        let mut memo = vec![vec![-1; n]; n];
        Self::dpf(1, n - 2, &v, &mut memo)
    }
}
