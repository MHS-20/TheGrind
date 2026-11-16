import java.util.Arrays;

class Solution {
    private int dpf(int i, int j, String word1, String word2, int[][] memo) {
        if (i == word1.length())
            return word2.length() - j;
        if (j == word2.length())
            return word1.length() - i;
        if (memo[i][j] != -1)
            return memo[i][j];

        if (word1.charAt(i) == word2.charAt(j)) {
            memo[i][j] = dpf(i + 1, j + 1, word1, word2, memo);
            return memo[i][j];
        }

        memo[i][j] = 1 + Math.min(dpf(i + 1, j + 1, word1, word2, memo),
                Math.min(dpf(i + 1, j, word1, word2, memo),
                        dpf(i, j + 1, word1, word2, memo)));
        return memo[i][j];
    }

    public int minDistance(String word1, String word2) {
        if (word1.equals(word2))
            return 0;
        int n = word1.length(), m = word2.length();
        int[][] memo = new int[n + 1][m + 1];
        for (int[] row : memo)
            Arrays.fill(row, -1);
        return dpf(0, 0, word1, word2, memo);
    }
}
