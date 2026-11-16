#include <stdlib.h>
#include <string.h>

static const char *A, *B;
static int n, m, w;
static int *memo; // memo[i * w + j], -1 = not computed

static int min3(int a, int b, int c) {
  int r = a < b ? a : b;
  return r < c ? r : c;
}

static int dpf(int i, int j) {
  if (i == n)
    return m - j;
  if (j == m)
    return n - i;
  if (memo[i * w + j] != -1)
    return memo[i * w + j];

  if (A[i] == B[j]) {
    memo[i * w + j] = dpf(i + 1, j + 1);
    return memo[i * w + j];
  }

  memo[i * w + j] = 1 + min3(dpf(i + 1, j + 1), dpf(i + 1, j), dpf(i, j + 1));
  return memo[i * w + j];
}

int minDistance(char *word1, char *word2) {
  if (strcmp(word1, word2) == 0)
    return 0;
  A = word1, B = word2, n = strlen(word1), m = strlen(word2), w = m + 1;
  memo = malloc((n + 1) * w * sizeof(int));
  for (int k = 0; k < (n + 1) * w; k++)
    memo[k] = -1;
  int res = dpf(0, 0);
  free(memo);
  return res;
}
