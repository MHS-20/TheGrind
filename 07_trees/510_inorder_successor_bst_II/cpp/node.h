#pragma once

// Definition for a binary tree node with a parent pointer.
struct Node {
  int val;
  Node *left = nullptr;
  Node *right = nullptr;
  Node *parent = nullptr;
  Node(int x) : val(x) {}
};
