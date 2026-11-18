package solution

func areSentencesSimilar(sentence1 []string, sentence2 []string, similarPairs [][]string) bool {
	if len(sentence1) != len(sentence2) {
		return false
	}

	type pair struct{ a, b string }
	pairs := make(map[pair]struct{}, len(similarPairs))
	for _, p := range similarPairs {
		pairs[pair{p[0], p[1]}] = struct{}{}
	}

	for i, w1 := range sentence1 {
		w2 := sentence2[i]
		if w1 == w2 {
			continue
		}
		_, fwd := pairs[pair{w1, w2}]
		_, bwd := pairs[pair{w2, w1}]
		if !fwd && !bwd {
			return false
		}
	}

	return true
}
