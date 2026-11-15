impl Solution {
    fn dpf(day: usize, holding: usize, memo: &mut Vec<[i32; 2]>, prices: &[i32]) -> i32 {
        if day >= prices.len() {
            return 0;
        }
        if memo[day][holding] != -1 {
            return memo[day][holding];
        }

        let mut profit = Self::dpf(day + 1, holding, memo, prices);
        if holding == 0 {
            profit = profit.max(Self::dpf(day + 1, 1, memo, prices) - prices[day]);
        } else {
            profit = profit.max(Self::dpf(day + 2, 0, memo, prices) + prices[day]);
        }

        memo[day][holding] = profit;
        profit
    }

    pub fn max_profit(prices: Vec<i32>) -> i32 {
        let mut memo = vec![[-1; 2]; prices.len()];
        Self::dpf(0, 0, &mut memo, &prices)
    }
}
