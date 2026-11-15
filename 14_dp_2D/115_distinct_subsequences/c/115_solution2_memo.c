#include <stdlib.h>
#include <string.h>

static const char *S, *T;
static int n, m;
static long long *memo; // memo[i * m + j], -1 = not computed

static long long dpf(int i, int j) {
  if (j == m)
    return 1;
  if (i == n)
    return 0;
  if (memo[i * m + j] != -1)
    return memo[i * m + j];

  long long ways = 0;
  if (S[i] == T[j]) {
    ways += dpf(i + 1, j + 1);
  }
  ways += dpf(i + 1, j);

  // keep within u32 range so nothing overflows and -1 stays a free sentinel
  return memo[i * m + j] = ways & 0xFFFFFFFFLL;
}

int numDistinct(char *s, char *t) {
  S = s, T = t, n = strlen(s), m = strlen(t);
  memo = malloc((n * m + 1) * sizeof(long long));
  for (int k = 0; k < n * m; k++)
    memo[k] = -1;
  int res = (int)dpf(0, 0);
  free(memo);
  return res;
}
