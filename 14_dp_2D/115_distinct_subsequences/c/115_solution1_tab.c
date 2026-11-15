#include <stdlib.h>
#include <string.h>

int numDistinct(char *s, char *t) {
  int n = strlen(s), m = strlen(t);
  int w = m + 1;
  // memo[i * w + j]; unsigned so intermediate overflow wraps instead of being UB
  unsigned long long *memo = calloc((n + 1) * w, sizeof(unsigned long long));

  for (int idx = 0; idx <= n; idx++) {
    memo[idx * w + m] = 1;
  }

  for (int j = m - 1; j >= 0; j--) {
    for (int i = n - 1; i >= 0; i--) {
      unsigned long long ways = 0;
      if (s[i] == t[j])
        ways += memo[(i + 1) * w + (j + 1)];
      ways += memo[(i + 1) * w + j];
      memo[i * w + j] = ways;
    }
  }

  int res = (int)memo[0];
  free(memo);
  return res;
}
