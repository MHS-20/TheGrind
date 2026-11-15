#include <string>
#include <vector>

using namespace std;

class Solution {
public:
  int numDistinct(string s, string t) {
    int n = s.size(), m = t.size();
    vector<vector<unsigned long long>> memo(
        n + 1, vector<unsigned long long>(m + 1, 0));

    for (int idx = 0; idx <= n; idx++) {
      memo[idx][m] = 1;
    }

    for (int j = m - 1; j >= 0; j--) {
      for (int i = n - 1; i >= 0; i--) {
        unsigned long long ways = 0;
        if (s[i] == t[j])
          ways += memo[i + 1][j + 1];
        ways += memo[i + 1][j];
        memo[i][j] = ways;
      }
    }

    return (int)memo[0][0];
  }
};
