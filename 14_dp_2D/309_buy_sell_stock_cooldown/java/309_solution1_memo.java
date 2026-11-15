import java.util.Arrays;

class Solution {
    private int dpf(int day, int holding, int[][] memo, int[] prices) {
        if (day >= prices.length)
            return 0;
        if (memo[day][holding] != -1)
            return memo[day][holding];

        int profit = dpf(day + 1, holding, memo, prices);
        if (holding == 0) {
            profit = Math.max(profit, dpf(day + 1, 1, memo, prices) - prices[day]);
        } else {
            profit = Math.max(profit, dpf(day + 2, 0, memo, prices) + prices[day]);
        }

        memo[day][holding] = profit;
        return profit;
    }

    public int maxProfit(int[] prices) {
        int n = prices.length;
        int[][] memo = new int[n][2];
        for (int[] row : memo)
            Arrays.fill(row, -1);
        return dpf(0, 0, memo, prices);
    }
}
