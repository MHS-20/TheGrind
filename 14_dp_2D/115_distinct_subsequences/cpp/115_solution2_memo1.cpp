#include <string>
#include <vector>

using namespace std;

class Solution {
  unsigned long long dpf(int i, int j, string &s, string &t,
                         vector<vector<unsigned long long>> &memo) {
    if (j == t.size())
      return 1;
    if (i == s.size())
      return 0;
    if (memo[i][j] != -1)
      return memo[i][j];

    memo[i][j] = 0;
    if (s[i] == t[j]) {
      memo[i][j] += dpf(i + 1, j + 1, s, t, memo);
    }
    memo[i][j] += dpf(i + 1, j, s, t, memo);
    return memo[i][j];
  }

public:
  int numDistinct(string s, string t) {
    int n = s.size(), m = t.size();
    vector<vector<unsigned long long>> memo(n,
                                            vector<unsigned long long>(m, -1));
    return (int)dpf(0, 0, s, t, memo);
  }
};
