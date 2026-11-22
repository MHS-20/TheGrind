use std::cell::RefCell;
use std::rc::Rc;

use crate::tree_node::TreeNode;

type Node = Option<Rc<RefCell<TreeNode>>>;

// LCA of a plain Binary Tree: recursive DFS, O(n) time, O(h) space
impl Solution {
    pub fn lowest_common_ancestor(root: Node, p: Node, q: Node) -> Node {
        let (p, q) = (p?, q?);
        Self::dfs(root, &p, &q)
    }

    fn dfs(root: Node, p: &Rc<RefCell<TreeNode>>, q: &Rc<RefCell<TreeNode>>) -> Node {
        let node = root?;
        if Rc::ptr_eq(&node, p) || Rc::ptr_eq(&node, q) {
            return Some(node);
        }

        let left = Self::dfs(node.borrow().left.clone(), p, q);
        let right = Self::dfs(node.borrow().right.clone(), p, q);

        match (left, right) {
            (Some(_), Some(_)) => Some(node), // p and q on different sides
            (l, r) => l.or(r),                // pass up whatever was found
        }
    }
}
