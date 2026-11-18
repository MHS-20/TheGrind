use std::collections::HashMap;

struct UnionFind<'a> {
    parent: HashMap<&'a str, &'a str>,
}

impl<'a> UnionFind<'a> {
    fn find(&mut self, w: &'a str) -> &'a str {
        let p = *self.parent.entry(w).or_insert(w);
        if p == w {
            return w;
        }
        let root = self.find(p);
        self.parent.insert(w, root); // path compression
        root
    }

    fn unite(&mut self, a: &'a str, b: &'a str) {
        let (ra, rb) = (self.find(a), self.find(b));
        self.parent.insert(ra, rb);
    }
}

impl Solution {
    pub fn are_sentences_similar_two(
        sentence1: Vec<String>,
        sentence2: Vec<String>,
        similar_pairs: Vec<Vec<String>>,
    ) -> bool {
        if sentence1.len() != sentence2.len() {
            return false;
        }

        let mut uf = UnionFind { parent: HashMap::new() };
        for p in &similar_pairs {
            uf.unite(&p[0], &p[1]);
        }

        sentence1
            .iter()
            .zip(&sentence2)
            .all(|(w1, w2)| w1 == w2 || uf.find(w1) == uf.find(w2))
    }
}

struct Solution;
