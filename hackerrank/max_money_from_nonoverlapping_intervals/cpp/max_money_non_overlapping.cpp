/*
 * Maximum Money from Non-Overlapping Intervals
 *
 * You are given an array `trips` where trips[i] = [start_i, end_i, rate_i].
 * Each triplet describes an interval of days from start_i to end_i
 * (inclusive). Every day inside that interval pays rate_i money.
 *
 * You can choose any subset of the intervals, as long as no two chosen
 * intervals overlap (they may not share any day). If you choose interval i,
 * you earn (end_i - start_i + 1) * rate_i money. Days not covered by a
 * chosen interval pay 0. Days not covered by any interval of the input, pay 0.
 *
 * Return the maximum total amount of money you can collect.
 *
 * Example:
 *   Input:  trips = [[1,3,2], [2,5,1], [4,6,3]]
 *   Output: 15
 *   Explanation:
 *     [1,3,2] pays 3 * 2 = 6
 *     [2,5,1] pays 4 * 1 = 4
 *     [4,6,3] pays 3 * 3 = 9
 *     Choosing [1,3,2] and [4,6,3] gives 6 + 9 = 15. They don't overlap,
 *     because the first ends on day 3 and the second starts on day 4.
 *     [2,5,1] overlaps both of them, so it can't be added.
 *
 * Constraints:
 *   1 <= trips.length <= 10^5
 *   1 <= start_i <= end_i <= 10^9
 *   1 <= rate_i <= 10^4
 */

#include <algorithm>
#include <array>
#include <iostream>
#include <vector>

using namespace std;

long long maxMoney(vector<array<long long, 3>> trips) {
  int n = trips.size();
  sort(trips.begin(), trips.end(),
       [](const auto &a, const auto &b) { return a[1] < b[1]; });

  // dp[i] = best total using only the first i intervals
  vector<long long> dp(n + 1, 0);
  for (int i = 0; i < n; i++) {
    long long l = trips[i][0], r = trips[i][1], c = trips[i][2];

    // Binary search: find the last interval that doesn't overlap
    int lo = 0, hi = i;
    while (lo < hi) {
      int mid = lo + (hi - lo) / 2;
      if (trips[mid][1] < l)
        lo = mid + 1; // mid ends before l: answer is to the right
      else
        hi = mid; // mid overlaps: answer is mid or to the left
    }
    int j = lo; // number of compatible intervals

    dp[i + 1] = max(dp[i], dp[j] + (r - l + 1) * c);
  }
  return dp[n];
}

int main() {
  int n;
  cin >> n;
  vector<array<long long, 3>> trips(n);
  for (auto &t : trips)
    cin >> t[0] >> t[1] >> t[2];
  cout << maxMoney(trips) << "\n";
}
