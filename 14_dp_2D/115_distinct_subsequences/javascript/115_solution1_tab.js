/**
 * @param {string} s
 * @param {string} t
 * @return {number}
 */
var numDistinct = function (s, t) {
  const n = s.length, m = t.length;
  const memo = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0));

  for (let idx = 0; idx <= n; idx++) {
    memo[idx][m] = 1;
  }

  for (let j = m - 1; j >= 0; j--) {
    for (let i = n - 1; i >= 0; i--) {
      let ways = 0;
      if (s[i] === t[j]) ways += memo[i + 1][j + 1];
      ways += memo[i + 1][j];
      memo[i][j] = ways;
    }
  }

  return memo[0][0];
};
