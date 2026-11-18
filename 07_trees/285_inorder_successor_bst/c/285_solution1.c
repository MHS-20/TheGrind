#include "tree_node.h"

struct TreeNode *inorderSuccessor(struct TreeNode *root, struct TreeNode *p) {
  struct TreeNode *successor = NULL;

  while (root) {
    if (root->val > p->val) {
      successor = root;
      root = root->left;
    } else {
      root = root->right;
    }
  }

  return successor;
}
