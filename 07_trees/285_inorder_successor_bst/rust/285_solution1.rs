use std::cell::RefCell;
use std::rc::Rc;

use crate::tree_node::TreeNode;

impl Solution {
    pub fn inorder_successor(
        root: Option<Rc<RefCell<TreeNode>>>,
        p: Option<Rc<RefCell<TreeNode>>>,
    ) -> Option<Rc<RefCell<TreeNode>>> {
        let pv = p?.borrow().val;
        let mut successor = None;

        let mut cur = root;
        while let Some(node) = cur {
            if node.borrow().val > pv {
                cur = node.borrow().left.clone();
                successor = Some(node);
            } else {
                cur = node.borrow().right.clone();
            }
        }

        successor
    }
}
