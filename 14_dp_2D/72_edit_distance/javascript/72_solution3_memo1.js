/**
 * @param {string} word1
 * @param {string} word2
 * @return {number}
 */
var minDistance = function (word1, word2) {
  if (word1 === word2) return 0;
  const n = word1.length, m = word2.length;
  const memo = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(-1));

  const dpf = (i, j) => {
    if (i === n) return m - j;
    if (j === m) return n - i;
    if (memo[i][j] !== -1) return memo[i][j];

    if (word1[i] === word2[j]) {
      memo[i][j] = dpf(i + 1, j + 1);
      return memo[i][j];
    }

    memo[i][j] = 1 + Math.min(dpf(i + 1, j + 1), dpf(i + 1, j), dpf(i, j + 1));
    return memo[i][j];
  };

  return dpf(0, 0);
};
