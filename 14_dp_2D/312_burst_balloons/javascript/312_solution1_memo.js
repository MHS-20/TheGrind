/**
 * @param {number[]} nums
 * @return {number}
 */
var maxCoins = function (nums) {
  const v = [1, ...nums, 1];
  const n = v.length;
  const memo = Array.from({ length: n }, () => new Array(n).fill(-1));

  const dpf = (l, r) => {
    if (l > r) return 0;
    if (memo[l][r] !== -1) return memo[l][r];

    let best = 0;
    for (let k = l; k <= r; k++) {
      const coins =
        v[l - 1] * v[k] * v[r + 1] + // k burst last
        dpf(l, k - 1) + // left part
        dpf(k + 1, r); // right part
      best = Math.max(best, coins);
    }
    return (memo[l][r] = best);
  };

  return dpf(1, n - 2);
};
