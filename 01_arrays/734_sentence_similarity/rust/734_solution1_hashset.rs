use std::collections::HashSet;

impl Solution {
    pub fn are_sentences_similar(
        sentence1: Vec<String>,
        sentence2: Vec<String>,
        similar_pairs: Vec<Vec<String>>,
    ) -> bool {
        if sentence1.len() != sentence2.len() {
            return false;
        }

        let pairs: HashSet<(&str, &str)> = similar_pairs
            .iter()
            .map(|p| (p[0].as_str(), p[1].as_str()))
            .collect();

        sentence1.iter().zip(&sentence2).all(|(w1, w2)| {
            w1 == w2
                || pairs.contains(&(w1.as_str(), w2.as_str()))
                || pairs.contains(&(w2.as_str(), w1.as_str()))
        })
    }
}

struct Solution;
