#include <algorithm>
#include <string>
#include <vector>

using namespace std;

class Solution {
  int dpf(int i, int j, string &word1, string &word2,
          vector<vector<int>> &memo) {
    if (i == word1.size())
      return word2.size() - j;
    if (j == word2.size())
      return word1.size() - i;
    if (memo[i][j] != -1)
      return memo[i][j];

    if (word1[i] == word2[j]) {
      memo[i][j] = dpf(i + 1, j + 1, word1, word2, memo);
      return memo[i][j];
    }

    memo[i][j] = 1 + min({dpf(i + 1, j + 1, word1, word2, memo),
                          dpf(i + 1, j, word1, word2, memo),
                          dpf(i, j + 1, word1, word2, memo)});
    return memo[i][j];
  }

public:
  int minDistance(string word1, string word2) {
    if (word1 == word2)
      return 0;
    int n = word1.size(), m = word2.size();
    vector<vector<int>> memo(n + 1, vector<int>(m + 1, -1));
    return dpf(0, 0, word1, word2, memo);
  }
};
