#include <algorithm>
#include <vector>

using namespace std;

class Solution {
public:
  int maxCoins(vector<int> &nums) {
    vector<int> v = {1};
    v.insert(v.end(), nums.begin(), nums.end());
    v.push_back(1);

    int n = v.size();
    vector<vector<int>> dp(n, vector<int>(n, 0));

    for (int l = n - 2; l >= 1; l--) {   // rows bottom-up: dp[k+1][..] ready
      for (int r = l; r <= n - 2; r++) { // cols left-right: dp[l][k-1] ready
        for (int k = l; k <= r; k++) {
          int coins = v[l - 1] * v[k] * v[r + 1] + dp[l][k - 1] + dp[k + 1][r];
          dp[l][r] = max(dp[l][r], coins);
        }
      }
    }
    return dp[1][n - 2];
  }
};
