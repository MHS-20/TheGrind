/**
 * @param {number[]} prices
 * @return {number}
 */
var maxProfit = function (prices) {
  const n = prices.length;
  const memo = Array.from({ length: n }, () => [-1, -1]);

  const dpf = (day, holding) => {
    if (day >= n) return 0;
    if (memo[day][holding] !== -1) return memo[day][holding];

    let profit = dpf(day + 1, holding);
    if (holding === 0) {
      profit = Math.max(profit, dpf(day + 1, 1) - prices[day]);
    } else {
      profit = Math.max(profit, dpf(day + 2, 0) + prices[day]);
    }

    memo[day][holding] = profit;
    return profit;
  };

  return dpf(0, 0);
};
