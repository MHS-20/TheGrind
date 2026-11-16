/**
 * @param {string} s
 * @param {string} p
 * @return {boolean}
 */
var isMatch = function (s, p) {
  const n = s.length, m = p.length;
  const memo = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(-1));

  const dpf = (i, j) => {
    // pattern used up: match only if the string is used up too
    if (j === m) return i === n;
    if (memo[i][j] !== -1) return memo[i][j];

    // does the current pattern char match s[i]? (s may already be used up)
    const match = i < n && (s[i] === p[j] || p[j] === ".");
    let res;

    if (j + 1 < m && p[j + 1] === "*") {
      // treat "x*" as one unit: either use zero copies (skip it),
      // or match one copy and stay on "x*" to allow more
      res = dpf(i, j + 2) || (match && dpf(i + 1, j));
    } else {
      // plain char or '.': must match exactly one character
      res = match && dpf(i + 1, j + 1);
    }

    memo[i][j] = res;
    return res;
  };

  return dpf(0, 0);
};
