const TreeNode = require("./tree_node");

// LCA of a plain Binary Tree: recursive DFS, O(n) time, O(h) space
var lowestCommonAncestor = function (root, p, q) {
  if (!root || root === p || root === q) return root;

  const left = lowestCommonAncestor(root.left, p, q);
  const right = lowestCommonAncestor(root.right, p, q);

  if (left && right) return root; // p and q on different sides
  return left ?? right; // pass up whatever was found
};
