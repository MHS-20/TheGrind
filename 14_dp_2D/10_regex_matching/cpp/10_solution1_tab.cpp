#include <string>
#include <vector>

using namespace std;
class Solution {
public:
  bool isMatch(string s, string p) {
    int n = s.size(), m = p.size();
    // dp[i][j]: does s[i..] match p[j..]?
    vector<vector<bool>> dp(n + 1, vector<bool>(m + 1, false));

    // base case: empty pattern matches only the empty string
    dp[n][m] = true; // dp[i][m] for i < n stays false

    for (int i = n; i >= 0; i--) {       // row i reads row i+1 → bottom-up
      for (int j = m - 1; j >= 0; j--) { // reads j+1, j+2 → right-to-left
        bool match = i < n && (s[i] == p[j] || p[j] == '.');

        if (j + 1 < m && p[j + 1] == '*') {
          dp[i][j] = dp[i][j + 2] ||          // zero copies of x*
                     (match && dp[i + 1][j]); // one more copy
        } else {
          dp[i][j] = match && dp[i + 1][j + 1];
        }
      }
    }
    return dp[0][0];
  }
};
