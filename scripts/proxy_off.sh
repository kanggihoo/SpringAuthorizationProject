#!/bin/bash

# Git Proxy OFF
echo "Unsetting Git Proxy..."
git config --global --unset http.proxy
git config --global --unset https.proxy

echo "Git Proxy has been unset."
echo "Current Git Proxy Settings (should be empty):"
git config --global --get http.proxy
git config --global --get https.proxy
