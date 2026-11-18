package trees

func findMinKeyWithinTree(root *Node) *Node {
	for root.Left != nil {
		root = root.Left
	}
	return root
}

func inorderSuccessor(node *Node) *Node {
	if node.Right != nil {
		return findMinKeyWithinTree(node.Right)
	}

	ancestor, child := node.Parent, node

	for ancestor != nil && child == ancestor.Right {
		child = ancestor
		ancestor = child.Parent
	}

	return ancestor
}
