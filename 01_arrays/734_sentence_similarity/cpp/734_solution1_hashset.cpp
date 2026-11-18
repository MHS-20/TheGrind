#include <string>
#include <unordered_set>
#include <vector>
using namespace std;

class Solution {
public:
  bool areSentencesSimilar(vector<string> &sentence1, vector<string> &sentence2,
                           vector<vector<string>> &similarPairs) {
    if (sentence1.size() != sentence2.size())
      return false;

    unordered_set<string> pairs;
    for (const auto &p : similarPairs)
      pairs.insert(p[0] + "#" + p[1]);

    for (size_t i = 0; i < sentence1.size(); i++) {
      const string &w1 = sentence1[i];
      const string &w2 = sentence2[i];
      if (w1 != w2 && !pairs.count(w1 + "#" + w2) && !pairs.count(w2 + "#" + w1))
        return false;
    }

    return true;
  }
};
