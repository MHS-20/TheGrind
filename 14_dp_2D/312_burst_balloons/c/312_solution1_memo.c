#include <stdlib.h>

static int *v, *memo; // memo[l * n + r], -1 = not computed
static int n;

static int dpf(int l, int r) {
  if (l > r)
    return 0;
  if (memo[l * n + r] != -1)
    return memo[l * n + r];

  int best = 0;
  for (int k = l; k <= r; k++) {
    int coins = v[l - 1] * v[k] * v[r + 1] // k burst last
                + dpf(l, k - 1)            // left part
                + dpf(k + 1, r);           // right part
    if (coins > best)
      best = coins;
  }
  return memo[l * n + r] = best;
}

int maxCoins(int *nums, int numsSize) {
  n = numsSize + 2;
  v = malloc(n * sizeof(int));
  v[0] = v[n - 1] = 1;
  for (int i = 0; i < numsSize; i++)
    v[i + 1] = nums[i];

  memo = malloc(n * n * sizeof(int));
  for (int k = 0; k < n * n; k++)
    memo[k] = -1;
  int res = dpf(1, n - 2);
  free(memo);
  free(v);
  return res;
}
