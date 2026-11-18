#include "node.h"

class Solution {
  Node *findMinKeyWithinTree(Node *root) {
    while (root->left)
      root = root->left;
    return root;
  }

public:
  Node *inorderSuccessor(Node *node) {
    if (node->right)
      return findMinKeyWithinTree(node->right);

    Node *ancestor = node->parent;
    Node *child = node;

    while (ancestor && child == ancestor->right) {
      child = ancestor;
      ancestor = child->parent;
    }

    return ancestor;
  }
};
