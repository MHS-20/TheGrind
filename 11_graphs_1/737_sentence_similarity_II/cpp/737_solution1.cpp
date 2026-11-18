#include <string>
#include <unordered_map>
#include <vector>
using namespace std;

class Solution {
  unordered_map<string, string> parent;

  string find(const string &w) {
    if (!parent.count(w))
      parent[w] = w;
    if (parent[w] != w)
      parent[w] = find(parent[w]); // path compression
    return parent[w];
  }

  void unite(const string &a, const string &b) { parent[find(a)] = find(b); }

public:
  bool areSentencesSimilarTwo(vector<string> &sentence1,
                              vector<string> &sentence2,
                              vector<vector<string>> &similarPairs) {
    if (sentence1.size() != sentence2.size())
      return false;

    for (const auto &p : similarPairs)
      unite(p[0], p[1]);

    for (size_t i = 0; i < sentence1.size(); i++) {
      if (sentence1[i] != sentence2[i] && find(sentence1[i]) != find(sentence2[i]))
        return false;
    }

    return true;
  }
};
