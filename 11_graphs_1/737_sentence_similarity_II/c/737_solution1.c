#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

// Maps each distinct word to a dense id so union-find can work on ints.
typedef struct {
  const char **keys;
  int *ids;
  int capacity;
  int size;
} WordIds;

static unsigned int hash_str(const char *s) {
  unsigned int h = 5381;
  while (*s)
    h = h * 33 + (unsigned char)*s++;
  return h;
}

static int word_id(WordIds *map, int *parent, const char *w) {
  unsigned int idx = hash_str(w) % map->capacity;
  while (map->keys[idx]) {
    if (!strcmp(map->keys[idx], w))
      return map->ids[idx];
    idx = (idx + 1) % map->capacity;
  }
  map->keys[idx] = w;
  map->ids[idx] = map->size;
  parent[map->size] = map->size;
  return map->size++;
}

static int find(int *parent, int x) {
  if (parent[x] != x)
    parent[x] = find(parent, parent[x]); // path compression
  return parent[x];
}

static void unite(int *parent, int a, int b) {
  parent[find(parent, a)] = find(parent, b);
}

bool areSentencesSimilarTwo(char **sentence1, int sentence1Size,
                            char **sentence2, int sentence2Size,
                            char ***similarPairs, int similarPairsSize,
                            int *similarPairsColSize) {
  (void)similarPairsColSize; // always 2
  if (sentence1Size != sentence2Size)
    return false;

  int maxWords = 2 * similarPairsSize + 2 * sentence1Size;
  WordIds map = {calloc(2 * maxWords + 1, sizeof(char *)),
                 malloc((2 * maxWords + 1) * sizeof(int)), 2 * maxWords + 1, 0};
  int *parent = malloc((maxWords + 1) * sizeof(int));

  for (int i = 0; i < similarPairsSize; i++)
    unite(parent, word_id(&map, parent, similarPairs[i][0]),
          word_id(&map, parent, similarPairs[i][1]));

  bool similar = true;
  for (int i = 0; i < sentence1Size && similar; i++) {
    const char *w1 = sentence1[i];
    const char *w2 = sentence2[i];
    if (strcmp(w1, w2) && find(parent, word_id(&map, parent, w1)) !=
                              find(parent, word_id(&map, parent, w2)))
      similar = false;
  }

  free(map.keys);
  free(map.ids);
  free(parent);
  return similar;
}
