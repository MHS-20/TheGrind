/**
 * @param {number[]} startTime
 * @param {number[]} endTime
 * @param {number[]} profit
 * @return {number}
 */
var jobScheduling = function (startTime, endTime, profit) {
  const n = profit.length;
  const jobs = startTime.map((s, i) => [s, endTime[i], profit[i]]);

  jobs.sort((a, b) => a[1] - b[1]);

  const dp = new Array(n + 1).fill(0);

  for (let i = 0; i < n; i++) {
    const [start, , p] = jobs[i];

    let left = 0,
      right = i;
    while (left < right) {
      const mid = left + ((right - left) >> 1);

      if (jobs[mid][1] <= start) left = mid + 1;
      else right = mid;
    }

    dp[i + 1] = Math.max(dp[i], dp[left] + p);
  }

  return dp[n];
};
