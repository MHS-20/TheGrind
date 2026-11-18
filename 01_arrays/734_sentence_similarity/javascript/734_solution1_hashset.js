/**
 * @param {string[]} sentence1
 * @param {string[]} sentence2
 * @param {string[][]} similarPairs
 * @return {boolean}
 */
var areSentencesSimilar = function (sentence1, sentence2, similarPairs) {
  if (sentence1.length !== sentence2.length) {
    return false;
  }

  const key = (a, b) => `${a}#${b}`;
  const pairs = new Set(similarPairs.map(([a, b]) => key(a, b)));

  for (let i = 0; i < sentence1.length; i++) {
    const w1 = sentence1[i];
    const w2 = sentence2[i];
    if (w1 !== w2 && !pairs.has(key(w1, w2)) && !pairs.has(key(w2, w1))) {
      return false;
    }
  }

  return true;
};
