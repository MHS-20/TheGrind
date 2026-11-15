import java.util.Arrays;

class Solution {
    private long dpf(int i, int j, String s, String t, long[][] memo) {
        if (j == t.length())
            return 1;
        if (i == s.length())
            return 0;
        if (memo[i][j] != -1)
            return memo[i][j];

        long ways = 0;
        if (s.charAt(i) == t.charAt(j)) {
            ways += dpf(i + 1, j + 1, s, t, memo);
        }
        ways += dpf(i + 1, j, s, t, memo);

        // keep within u32 range so nothing overflows and -1 stays a free sentinel
        return memo[i][j] = ways & 0xFFFFFFFFL;
    }

    public int numDistinct(String s, String t) {
        int n = s.length(), m = t.length();
        long[][] memo = new long[n][m];
        for (long[] row : memo)
            Arrays.fill(row, -1);
        return (int) dpf(0, 0, s, t, memo);
    }
}
