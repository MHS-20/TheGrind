#pragma once

#include <stddef.h>

// Definition for a binary tree node with a parent pointer.
struct Node {
  int val;
  struct Node *left;
  struct Node *right;
  struct Node *parent;
};
