class Solution:
    def areSentencesSimilarTwo(
        self, sentence1: list[str], sentence2: list[str], similarPairs: list[list[str]]
    ) -> bool:
        if len(sentence1) != len(sentence2):
            return False

        parent: dict[str, str] = {}

        def find(w: str) -> str:
            parent.setdefault(w, w)
            if parent[w] != w:
                parent[w] = find(parent[w])  # path compression
            return parent[w]

        def unite(a: str, b: str) -> None:
            parent[find(a)] = find(b)

        for a, b in similarPairs:
            unite(a, b)

        return all(w1 == w2 or find(w1) == find(w2) for w1, w2 in zip(sentence1, sentence2))
