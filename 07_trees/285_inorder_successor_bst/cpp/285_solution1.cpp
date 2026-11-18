#include "tree_node.h"

class Solution {
public:
  TreeNode *inorderSuccessor(TreeNode *root, TreeNode *p) {
    TreeNode *successor = nullptr;

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
};
