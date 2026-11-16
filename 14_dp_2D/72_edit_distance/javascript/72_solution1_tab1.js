/**
 * @param {string} word1
 * @param {string} word2
 * @return {number}
 */
var minDistance = function (word1, word2) {
  if (word1 === word2) return 0;
  const n = word1.length, m = word2.length;
  const memo = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0));

  memo[n][m] = 0;
  for (let idx = 0; idx < n; idx++) {
    memo[idx][m] = n - idx;
  }

  for (let idx = 0; idx < m; idx++) {
    memo[n][idx] = m - idx;
  }

  for (let j = m - 1; j >= 0; j--) {
    for (let i = n - 1; i >= 0; i--) {
      if (word1[i] === word2[j]) memo[i][j] = memo[i + 1][j + 1];
      else {
        memo[i][j] =
          1 + Math.min(memo[i + 1][j], memo[i][j + 1], memo[i + 1][j + 1]);
      }
    }
  }
  return memo[0][0];
};
