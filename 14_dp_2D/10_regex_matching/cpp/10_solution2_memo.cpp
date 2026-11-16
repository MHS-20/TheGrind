#include <string>
#include <vector>

using namespace std;

class Solution {
  int n, m;

  bool dpf(int i, int j, const string &s, const string &p,
           vector<vector<int>> &memo) {
    // pattern used up: match only if the string is used up too
    if (j == m)
      return i == n;
    if (memo[i][j] != -1)
      return memo[i][j];

    // does the current pattern char match s[i]? (s may already be used up)
    bool match = i < n && (s[i] == p[j] || p[j] == '.');
    bool res;

    if (j + 1 < m && p[j + 1] == '*') {
      // treat "x*" as one unit: either use zero copies (skip it),
      // or match one copy and stay on "x*" to allow more
      res = dpf(i, j + 2, s, p, memo) || (match && dpf(i + 1, j, s, p, memo));
    } else {
      // plain char or '.': must match exactly one character
      res = match && dpf(i + 1, j + 1, s, p, memo);
    }

    memo[i][j] = res;
    return res;
  }

public:
  bool isMatch(string s, string p) {
    n = s.size(), m = p.size();
    vector<vector<int>> memo(n + 1, vector<int>(m + 1, -1));
    return dpf(0, 0, s, p, memo);
  }
};
