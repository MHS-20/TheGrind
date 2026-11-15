/**
 * @param {string} s
 * @param {string} t
 * @return {number}
 */
var numDistinct = function (s, t) {
  const n = s.length, m = t.length;
  const memo = Array.from({ length: n }, () => new Array(m).fill(-1));

  const dpf = (i, j) => {
    if (j === m) return 1;
    if (i === n) return 0;
    if (memo[i][j] !== -1) return memo[i][j];

    memo[i][j] = 0;
    if (s[i] === t[j]) {
      memo[i][j] += dpf(i + 1, j + 1);
    }
    memo[i][j] += dpf(i + 1, j);
    return memo[i][j];
  };

  return dpf(0, 0);
};
