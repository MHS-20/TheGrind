/**
 * @param {string[]} sentence1
 * @param {string[]} sentence2
 * @param {string[][]} similarPairs
 * @return {boolean}
 */
var areSentencesSimilarTwo = function (sentence1, sentence2, similarPairs) {
  if (sentence1.length !== sentence2.length) {
    return false;
  }

  const parent = new Map();

  const find = (w) => {
    if (!parent.has(w)) parent.set(w, w);
    if (parent.get(w) !== w) parent.set(w, find(parent.get(w))); // path compression
    return parent.get(w);
  };

  const unite = (a, b) => parent.set(find(a), find(b));

  for (const [a, b] of similarPairs) {
    unite(a, b);
  }

  for (let i = 0; i < sentence1.length; i++) {
    const w1 = sentence1[i];
    const w2 = sentence2[i];
    if (w1 !== w2 && find(w1) !== find(w2)) {
      return false;
    }
  }

  return true;
};
