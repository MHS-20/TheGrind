#include <stdlib.h>

typedef struct {
  int start, end, profit;
} Job;

static int max(int a, int b) { return a > b ? a : b; }

static int byEnd(const void *a, const void *b) {
  return ((const Job *)a)->end - ((const Job *)b)->end;
}

int jobScheduling(int *startTime, int startTimeSize, int *endTime,
                  int endTimeSize, int *profit, int profitSize) {
  int n = profitSize;
  Job *jobs = malloc(n * sizeof(Job));
  for (int i = 0; i < n; i++)
    jobs[i] = (Job){startTime[i], endTime[i], profit[i]};

  qsort(jobs, n, sizeof(Job), byEnd);

  int *dp = calloc(n + 1, sizeof(int));

  for (int i = 0; i < n; i++) {
    int left = 0, right = i;
    while (left < right) {
      int mid = left + (right - left) / 2;

      if (jobs[mid].end <= jobs[i].start)
        left = mid + 1;
      else
        right = mid;
    }

    dp[i + 1] = max(dp[i], dp[left] + jobs[i].profit);
  }

  int best = dp[n];
  free(jobs);
  free(dp);
  return best;
}
