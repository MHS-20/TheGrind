#include "tree_node.h"

// LCA of a plain Binary Tree: recursive DFS, O(n) time, O(h) space
struct TreeNode *lowestCommonAncestor(struct TreeNode *root, struct TreeNode *p,
                                      struct TreeNode *q) {
  if (!root || root == p || root == q)
    return root;

  struct TreeNode *left = lowestCommonAncestor(root->left, p, q);
  struct TreeNode *right = lowestCommonAncestor(root->right, p, q);

  if (left && right)
    return root;              // p and q on different sides
  return left ? left : right; // pass up whatever was found
}
