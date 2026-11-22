from tree_node import TreeNode


# LCA of a plain Binary Tree: recursive DFS, O(n) time, O(h) space
class Solution:
    def lowestCommonAncestor(self, root: TreeNode, p: TreeNode, q: TreeNode) -> TreeNode:
        if not root or root is p or root is q:
            return root

        left = self.lowestCommonAncestor(root.left, p, q)
        right = self.lowestCommonAncestor(root.right, p, q)

        if left and right:
            return root  # p and q on different sides
        return left or right  # pass up whatever was found
