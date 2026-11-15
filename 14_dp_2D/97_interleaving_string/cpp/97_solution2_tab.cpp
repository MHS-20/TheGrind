#include <string>
#include <vector>

using namespace std;

class Solution {
public:
  bool isInterleave(string s1, string s2, string s3) {
    int n = s1.size(), m = s2.size();
    if (n + m != s3.size())
      return false;

    vector<vector<int>> dp(n + 1, vector<int>(m + 1, -1));

    dp[n][m] = 1;
    for (int i = n; i >= 0; i--) {
      for (int j = m; j >= 0; j--) {
        if (i == n && j == m)
          continue;

        bool res = false;
        if (i < n && dp[i + 1][j])
          res = res || s1[i] == s3[i + j];
        if (j < m && dp[i][j + 1])
          res = res || s2[j] == s3[i + j];
        dp[i][j] = res;
      }
    }

    return dp[0][0];
  }
};
