// LCA of a plain Binary Tree: recursive DFS, O(n) time, O(h) space
class Solution {
    public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
        if (root == null || root == p || root == q)
            return root;

        TreeNode left = lowestCommonAncestor(root.left, p, q);
        TreeNode right = lowestCommonAncestor(root.right, p, q);

        if (left != null && right != null)
            return root; // p and q on different sides
        return left != null ? left : right; // pass up whatever was found
    }
}
