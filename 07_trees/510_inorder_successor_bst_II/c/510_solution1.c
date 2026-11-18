#include "node.h"

static struct Node *findMinKeyWithinTree(struct Node *root) {
  while (root->left)
    root = root->left;
  return root;
}

struct Node *inorderSuccessor(struct Node *node) {
  if (node->right)
    return findMinKeyWithinTree(node->right);

  struct Node *ancestor = node->parent;
  struct Node *child = node;

  while (ancestor && child == ancestor->right) {
    child = ancestor;
    ancestor = child->parent;
  }

  return ancestor;
}
