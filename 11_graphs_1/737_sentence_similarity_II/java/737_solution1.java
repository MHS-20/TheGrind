import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Solution {
    private final Map<String, String> parent = new HashMap<>();

    private String find(String w) {
        parent.putIfAbsent(w, w);
        if (!parent.get(w).equals(w)) {
            parent.put(w, find(parent.get(w))); // path compression
        }
        return parent.get(w);
    }

    private void unite(String a, String b) {
        parent.put(find(a), find(b));
    }

    public boolean areSentencesSimilarTwo(String[] sentence1, String[] sentence2, List<List<String>> similarPairs) {
        if (sentence1.length != sentence2.length) {
            return false;
        }

        for (List<String> p : similarPairs) {
            unite(p.get(0), p.get(1));
        }

        for (int i = 0; i < sentence1.length; i++) {
            String w1 = sentence1[i];
            String w2 = sentence2[i];
            if (!w1.equals(w2) && !find(w1).equals(find(w2))) {
                return false;
            }
        }

        return true;
    }
}
