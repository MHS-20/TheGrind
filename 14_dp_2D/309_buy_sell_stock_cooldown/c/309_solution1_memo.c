#include <stdlib.h>
#include <string.h>

static int max(int a, int b) { return a > b ? a : b; }

static int dpf(int day, int holding, int (*memo)[2], int *prices, int n) {
  if (day >= n)
    return 0;
  if (memo[day][holding] != -1)
    return memo[day][holding];

  int profit = dpf(day + 1, holding, memo, prices, n);
  if (holding == 0) {
    profit = max(profit, dpf(day + 1, 1, memo, prices, n) - prices[day]);
  } else {
    profit = max(profit, dpf(day + 2, 0, memo, prices, n) + prices[day]);
  }

  memo[day][holding] = profit;
  return profit;
}

int maxProfit(int *prices, int pricesSize) {
  int n = pricesSize;
  int(*memo)[2] = malloc(n * sizeof *memo);
  memset(memo, -1, n * sizeof *memo);
  int res = dpf(0, 0, memo, prices, n);
  free(memo);
  return res;
}
