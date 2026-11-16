class Solution {
    private int n, m;
    private Boolean[][] memo; // null = not computed

    private boolean dpf(int i, int j, String s, String p) {
        // pattern used up: match only if the string is used up too
        if (j == m)
            return i == n;
        if (memo[i][j] != null)
            return memo[i][j];

        // does the current pattern char match s[i]? (s may already be used up)
        boolean match = i < n && (s.charAt(i) == p.charAt(j) || p.charAt(j) == '.');
        boolean res;

        if (j + 1 < m && p.charAt(j + 1) == '*') {
            // treat "x*" as one unit: either use zero copies (skip it),
            // or match one copy and stay on "x*" to allow more
            res = dpf(i, j + 2, s, p) || (match && dpf(i + 1, j, s, p));
        } else {
            // plain char or '.': must match exactly one character
            res = match && dpf(i + 1, j + 1, s, p);
        }

        memo[i][j] = res;
        return res;
    }

    public boolean isMatch(String s, String p) {
        n = s.length();
        m = p.length();
        memo = new Boolean[n + 1][m + 1];
        return dpf(0, 0, s, p);
    }
}
