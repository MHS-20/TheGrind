#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

bool isInterleave(char *s1, char *s2, char *s3) {
  int n = strlen(s1), m = strlen(s2);
  if (n + m != (int)strlen(s3))
    return false;

  int w = m + 1;
  bool *dp = calloc((n + 1) * w, sizeof(bool)); // dp[i * w + j]

  dp[n * w + m] = true;
  for (int i = n; i >= 0; i--) {
    for (int j = m; j >= 0; j--) {
      if (i == n && j == m)
        continue;

      bool res = false;
      if (i < n && dp[(i + 1) * w + j])
        res = res || s1[i] == s3[i + j];
      if (j < m && dp[i * w + (j + 1)])
        res = res || s2[j] == s3[i + j];
      dp[i * w + j] = res;
    }
  }

  bool res = dp[0];
  free(dp);
  return res;
}
