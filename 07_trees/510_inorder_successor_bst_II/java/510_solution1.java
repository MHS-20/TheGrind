class Solution {
    private Node findMinKeyWithinTree(Node root) {
        while (root.left != null)
            root = root.left;
        return root;
    }

    public Node inorderSuccessor(Node node) {
        if (node.right != null)
            return findMinKeyWithinTree(node.right);

        Node ancestor = node.parent;
        Node child = node;

        while (ancestor != null && child == ancestor.right) {
            child = ancestor;
            ancestor = child.parent;
        }

        return ancestor;
    }
}
