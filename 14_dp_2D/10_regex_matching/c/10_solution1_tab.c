#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

bool isMatch(char *s, char *p) {
  int n = strlen(s), m = strlen(p);
  int w = m + 1;
  // dp[i * w + j]: does s[i..] match p[j..]?
  bool *dp = calloc((n + 1) * w, sizeof(bool));

  // base case: empty pattern matches only the empty string
  dp[n * w + m] = true; // dp[i][m] for i < n stays false

  for (int i = n; i >= 0; i--) {       // row i reads row i+1 → bottom-up
    for (int j = m - 1; j >= 0; j--) { // reads j+1, j+2 → right-to-left
      bool match = i < n && (s[i] == p[j] || p[j] == '.');

      if (j + 1 < m && p[j + 1] == '*') {
        dp[i * w + j] = dp[i * w + j + 2] ||                // zero copies of x*
                        (match && dp[(i + 1) * w + j]);     // one more copy
      } else {
        dp[i * w + j] = match && dp[(i + 1) * w + j + 1];
      }
    }
  }

  bool res = dp[0];
  free(dp);
  return res;
}
