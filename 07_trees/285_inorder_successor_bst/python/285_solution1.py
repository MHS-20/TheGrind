from tree_node import TreeNode


class Solution:
    def inorderSuccessor(self, root: TreeNode, p: TreeNode) -> TreeNode | None:
        successor = None

        while root:
            if root.val > p.val:
                successor = root
                root = root.left
            else:
                root = root.right

        return successor
