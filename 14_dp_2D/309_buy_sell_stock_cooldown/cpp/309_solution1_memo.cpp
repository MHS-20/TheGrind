#include <algorithm>
#include <vector>

using namespace std;

class Solution {
  int dpf(int day, int holding, vector<vector<int>> &memo,
          vector<int> &prices) {
    if (day >= prices.size())
      return 0;
    if (memo[day][holding] != -1)
      return memo[day][holding];

    int profit = dpf(day + 1, holding, memo, prices);
    if (holding == 0) {
      profit = max(profit, dpf(day + 1, 1, memo, prices) - prices[day]);
    } else {
      profit = max(profit, dpf(day + 2, 0, memo, prices) + prices[day]);
    }

    memo[day][holding] = profit;
    return profit;
  }

public:
  int maxProfit(vector<int> &prices) {
    int n = prices.size();
    vector<vector<int>> memo(n, vector<int>(2, -1));
    return dpf(0, 0, memo, prices);
  }
};
