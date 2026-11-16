#include <stdlib.h>
#include <string.h>

static int min3(int a, int b, int c) {
  int m = a < b ? a : b;
  return m < c ? m : c;
}

int minDistance(char *word1, char *word2) {
  if (strcmp(word1, word2) == 0)
    return 0;
  int n = strlen(word1), m = strlen(word2);
  int w = m + 1;
  int *memo = calloc((n + 1) * w, sizeof(int)); // memo[i * w + j]

  memo[n * w + m] = 0;
  for (int idx = 0; idx < n; idx++) {
    memo[idx * w + m] = n - idx;
  }

  for (int idx = 0; idx < m; idx++) {
    memo[n * w + idx] = m - idx;
  }

  for (int j = m - 1; j >= 0; j--) {
    for (int i = n - 1; i >= 0; i--) {
      if (word1[i] == word2[j])
        memo[i * w + j] = memo[(i + 1) * w + (j + 1)];
      else {
        memo[i * w + j] = 1 + min3(memo[(i + 1) * w + j], memo[i * w + (j + 1)],
                                   memo[(i + 1) * w + (j + 1)]);
      }
    }
  }

  int res = memo[0];
  free(memo);
  return res;
}
