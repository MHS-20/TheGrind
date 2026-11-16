#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

static const char *S, *P;
static int n, m, w;
static int *memo; // memo[i * w + j], -1 = not computed

static bool dpf(int i, int j) {
  // pattern used up: match only if the string is used up too
  if (j == m)
    return i == n;
  if (memo[i * w + j] != -1)
    return memo[i * w + j];

  // does the current pattern char match s[i]? (s may already be used up)
  bool match = i < n && (S[i] == P[j] || P[j] == '.');
  bool res;

  if (j + 1 < m && P[j + 1] == '*') {
    // treat "x*" as one unit: either use zero copies (skip it),
    // or match one copy and stay on "x*" to allow more
    res = dpf(i, j + 2) || (match && dpf(i + 1, j));
  } else {
    // plain char or '.': must match exactly one character
    res = match && dpf(i + 1, j + 1);
  }

  memo[i * w + j] = res;
  return res;
}

bool isMatch(char *s, char *p) {
  S = s, P = p, n = strlen(s), m = strlen(p), w = m + 1;
  memo = malloc((n + 1) * w * sizeof(int));
  for (int k = 0; k < (n + 1) * w; k++)
    memo[k] = -1;
  bool res = dpf(0, 0);
  free(memo);
  return res;
}
