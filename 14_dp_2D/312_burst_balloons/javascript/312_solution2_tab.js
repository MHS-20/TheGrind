/**
 * @param {number[]} nums
 * @return {number}
 */
var maxCoins = function (nums) {
  const v = [1, ...nums, 1];
  const n = v.length;
  const dp = Array.from({ length: n }, () => new Array(n).fill(0));

  for (let l = n - 2; l >= 1; l--) { // rows bottom-up: dp[k+1][..] ready
    for (let r = l; r <= n - 2; r++) { // cols left-right: dp[l][k-1] ready
      for (let k = l; k <= r; k++) {
        const coins = v[l - 1] * v[k] * v[r + 1] + dp[l][k - 1] + dp[k + 1][r];
        dp[l][r] = Math.max(dp[l][r], coins);
      }
    }
  }
  return dp[1][n - 2];
};
