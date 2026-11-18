const Node = require("./node");

const findMinKeyWithinTree = (root) => {
  while (root.left) root = root.left;
  return root;
};

var inorderSuccessor = function (node) {
  if (node.right) return findMinKeyWithinTree(node.right);

  let ancestor = node.parent;
  let child = node;

  while (ancestor && child === ancestor.right) {
    child = ancestor;
    ancestor = child.parent;
  }

  return ancestor;
};
