import java.util.HashSet;
import java.util.List;
import java.util.Set;

class Solution {
    public boolean areSentencesSimilar(String[] sentence1, String[] sentence2, List<List<String>> similarPairs) {
        if (sentence1.length != sentence2.length) {
            return false;
        }

        Set<String> pairs = new HashSet<>();
        for (List<String> p : similarPairs) {
            pairs.add(p.get(0) + "#" + p.get(1));
        }

        for (int i = 0; i < sentence1.length; i++) {
            String w1 = sentence1[i];
            String w2 = sentence2[i];
            if (!w1.equals(w2) && !pairs.contains(w1 + "#" + w2) && !pairs.contains(w2 + "#" + w1)) {
                return false;
            }
        }

        return true;
    }
}
