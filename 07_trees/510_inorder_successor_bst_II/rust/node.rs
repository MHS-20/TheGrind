use std::cell::RefCell;
use std::rc::{Rc, Weak};

// Definition for a binary tree node with a parent pointer.
// The parent link is Weak so parent and child do not keep each other alive.
#[derive(Debug)]
pub struct Node {
    pub val: i32,
    pub left: Option<Rc<RefCell<Node>>>,
    pub right: Option<Rc<RefCell<Node>>>,
    pub parent: Option<Weak<RefCell<Node>>>,
}
