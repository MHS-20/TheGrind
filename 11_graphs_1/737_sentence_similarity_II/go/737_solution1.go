package solution

func areSentencesSimilarTwo(sentence1 []string, sentence2 []string, similarPairs [][]string) bool {
	if len(sentence1) != len(sentence2) {
		return false
	}

	parent := make(map[string]string)

	var find func(w string) string
	find = func(w string) string {
		if _, ok := parent[w]; !ok {
			parent[w] = w
		}
		if parent[w] != w {
			parent[w] = find(parent[w]) // path compression
		}
		return parent[w]
	}

	unite := func(a, b string) {
		parent[find(a)] = find(b)
	}

	for _, p := range similarPairs {
		unite(p[0], p[1])
	}

	for i, w1 := range sentence1 {
		w2 := sentence2[i]
		if w1 != w2 && find(w1) != find(w2) {
			return false
		}
	}

	return true
}
