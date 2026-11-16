#include <algorithm>
#include <string>
#include <vector>

using namespace std;

class Solution {
public:
  int minDistance(string word1, string word2) {
    if (word1 == word2)
      return 0;
    int n = word1.size(), m = word2.size();
    vector<vector<int>> memo(n + 1, vector<int>(m + 1, 0));

    memo[n][m] = 0;
    for (int idx = 0; idx < n; idx++) {
      memo[idx][m] = word1.size() - idx;
    }

    for (int idx = 0; idx < m; idx++) {
      memo[n][idx] = word2.size() - idx;
    }

    for (int j = m - 1; j >= 0; j--) {
      for (int i = n - 1; i >= 0; i--) {
        if (word1[i] == word2[j])
          memo[i][j] = memo[i + 1][j + 1];
        else {
          memo[i][j] =
              1 + min({memo[i + 1][j], memo[i][j + 1], memo[i + 1][j + 1]});
        }
      }
    }
    return memo[0][0];
  }
};
