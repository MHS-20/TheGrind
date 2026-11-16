#include <algorithm>
#include <vector>

using namespace std;

class Solution {
  int dpf(int l, int r, vector<int> &nums, vector<vector<int>> &memo) {
    if (l > r)
      return 0;
    if (memo[l][r] != -1)
      return memo[l][r];

    int best = 0;
    for (int k = l; k <= r; k++) {
      int coins = nums[l - 1] * nums[k] * nums[r + 1] // k burst last
                  + dpf(l, k - 1, nums, memo)         // left part
                  + dpf(k + 1, r, nums, memo);        // right part
      best = max(best, coins);
    }
    return memo[l][r] = best;
  }

public:
  int maxCoins(vector<int> &nums) {
    vector<int> v = {1};
    v.insert(v.end(), nums.begin(), nums.end());
    v.push_back(1);

    int n = v.size();
    vector<vector<int>> memo(n, vector<int>(n, -1));
    return dpf(1, n - 2, v, memo);
  }
};
