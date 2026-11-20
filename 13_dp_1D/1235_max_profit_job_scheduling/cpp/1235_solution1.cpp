#include <algorithm>
#include <array>
#include <vector>

using namespace std;

class Solution {
public:
  int jobScheduling(vector<int> &startTime, vector<int> &endTime,
                    vector<int> &profit) {
    int n = profit.size();
    vector<array<int, 3>> jobs(n);
    for (int i = 0; i < n; i++)
      jobs[i] = {startTime[i], endTime[i], profit[i]};

    sort(jobs.begin(), jobs.end(),
         [](const auto &a, const auto &b) { return a[1] < b[1]; });

    vector<int> dp(n + 1, 0);

    for (int i = 0; i < n; i++) {
      int start = jobs[i][0], end = jobs[i][1], p = jobs[i][2];

      int left = 0, right = i;
      while (left < right) {
        int mid = left + (right - left) / 2;

        if (jobs[mid][1] <= start)
          left = mid + 1;
        else
          right = mid;
      }

      dp[i + 1] = max(dp[i], dp[left] + p);
    }

    return dp[n];
  }
};
