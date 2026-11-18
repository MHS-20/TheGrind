use std::cell::RefCell;
use std::rc::Rc;

use crate::node::Node;

type NodeRef = Rc<RefCell<Node>>;

impl Solution {
    fn find_min_key_within_tree(mut root: NodeRef) -> NodeRef {
        loop {
            let left = root.borrow().left.clone();
            match left {
                Some(next) => root = next,
                None => return root,
            }
        }
    }

    fn parent_of(node: &NodeRef) -> Option<NodeRef> {
        node.borrow().parent.as_ref()?.upgrade()
    }

    pub fn inorder_successor(node: NodeRef) -> Option<NodeRef> {
        if let Some(right) = node.borrow().right.clone() {
            return Some(Self::find_min_key_within_tree(right));
        }

        let mut child = node.clone();
        let mut ancestor = Self::parent_of(&node);

        while let Some(a) = ancestor.clone() {
            let is_right = a.borrow().right.as_ref().is_some_and(|r| Rc::ptr_eq(r, &child));
            if !is_right {
                break;
            }
            ancestor = Self::parent_of(&a);
            child = a;
        }

        ancestor
    }
}
