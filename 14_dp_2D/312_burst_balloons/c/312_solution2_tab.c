#include <stdlib.h>

int maxCoins(int *nums, int numsSize) {
  int n = numsSize + 2;
  int *v = malloc(n * sizeof(int));
  v[0] = v[n - 1] = 1;
  for (int i = 0; i < numsSize; i++)
    v[i + 1] = nums[i];

  int *dp = calloc(n * n, sizeof(int)); // dp[l * n + r]

  for (int l = n - 2; l >= 1; l--) {   // rows bottom-up: dp[k+1][..] ready
    for (int r = l; r <= n - 2; r++) { // cols left-right: dp[l][k-1] ready
      for (int k = l; k <= r; k++) {
        int coins =
            v[l - 1] * v[k] * v[r + 1] + dp[l * n + k - 1] + dp[(k + 1) * n + r];
        if (coins > dp[l * n + r])
          dp[l * n + r] = coins;
      }
    }
  }

  int res = dp[1 * n + n - 2];
  free(dp);
  free(v);
  return res;
}
