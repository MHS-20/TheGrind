package trees

// LCA of a plain Binary Tree: recursive DFS, O(n) time, O(h) space
func lowestCommonAncestor(root, p, q *TreeNode) *TreeNode {
	if root == nil || root == p || root == q {
		return root
	}

	left := lowestCommonAncestor(root.Left, p, q)
	right := lowestCommonAncestor(root.Right, p, q)

	if left != nil && right != nil {
		return root // p and q on different sides
	}
	if left != nil {
		return left // pass up whatever was found
	}
	return right
}
