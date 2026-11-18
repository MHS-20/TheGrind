from node import Node


class Solution:
    def inorderSuccessor(self, node: Node) -> Node | None:
        if node.right:
            return self.findMinKeyWithinTree(node.right)

        ancestor, child = node.parent, node

        while ancestor and child is ancestor.right:
            child = ancestor
            ancestor = child.parent

        return ancestor

    def findMinKeyWithinTree(self, root: Node) -> Node:
        while root.left:
            root = root.left
        return root
