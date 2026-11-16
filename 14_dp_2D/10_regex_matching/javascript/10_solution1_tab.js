/**
 * @param {string} s
 * @param {string} p
 * @return {boolean}
 */
var isMatch = function (s, p) {
  const n = s.length, m = p.length;
  // dp[i][j]: does s[i..] match p[j..]?
  const dp = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(false));

  // base case: empty pattern matches only the empty string
  dp[n][m] = true; // dp[i][m] for i < n stays false

  for (let i = n; i >= 0; i--) { // row i reads row i+1 → bottom-up
    for (let j = m - 1; j >= 0; j--) { // reads j+1, j+2 → right-to-left
      const match = i < n && (s[i] === p[j] || p[j] === ".");

      if (j + 1 < m && p[j + 1] === "*") {
        dp[i][j] = dp[i][j + 2] || // zero copies of x*
          (match && dp[i + 1][j]); // one more copy
      } else {
        dp[i][j] = match && dp[i + 1][j + 1];
      }
    }
  }
  return dp[0][0];
};
