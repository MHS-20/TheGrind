#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

typedef struct {
    const char **first;
    const char **second;
    int capacity;
} PairSet;

static unsigned int hash_pair(const char *a, const char *b) {
    unsigned int h = 5381;
    while (*a) h = h * 33 + (unsigned char)*a++;
    h = h * 33 + '#';
    while (*b) h = h * 33 + (unsigned char)*b++;
    return h;
}

static PairSet pairset_create(int capacity) {
    PairSet set;
    set.capacity = capacity;
    set.first = calloc(capacity, sizeof(char *));
    set.second = calloc(capacity, sizeof(char *));
    return set;
}

static void pairset_free(PairSet *set) {
    free(set->first);
    free(set->second);
}

static void pairset_insert(PairSet *set, const char *a, const char *b) {
    unsigned int idx = hash_pair(a, b) % set->capacity;

    while (set->first[idx]) {
        if (!strcmp(set->first[idx], a) && !strcmp(set->second[idx], b)) {
            return;
        }
        idx = (idx + 1) % set->capacity;
    }

    set->first[idx] = a;
    set->second[idx] = b;
}

static bool pairset_contains(const PairSet *set, const char *a, const char *b) {
    unsigned int idx = hash_pair(a, b) % set->capacity;

    while (set->first[idx]) {
        if (!strcmp(set->first[idx], a) && !strcmp(set->second[idx], b)) {
            return true;
        }
        idx = (idx + 1) % set->capacity;
    }

    return false;
}

bool areSentencesSimilar(char **sentence1, int sentence1Size, char **sentence2, int sentence2Size,
                         char ***similarPairs, int similarPairsSize, int *similarPairsColSize) {
    (void)similarPairsColSize;

    if (sentence1Size != sentence2Size) {
        return false;
    }

    PairSet pairs = pairset_create(similarPairsSize * 2 + 1);
    for (int i = 0; i < similarPairsSize; i++) {
        pairset_insert(&pairs, similarPairs[i][0], similarPairs[i][1]);
    }

    bool similar = true;
    for (int i = 0; i < sentence1Size; i++) {
        const char *w1 = sentence1[i];
        const char *w2 = sentence2[i];
        if (strcmp(w1, w2) && !pairset_contains(&pairs, w1, w2) && !pairset_contains(&pairs, w2, w1)) {
            similar = false;
            break;
        }
    }

    pairset_free(&pairs);
    return similar;
}
