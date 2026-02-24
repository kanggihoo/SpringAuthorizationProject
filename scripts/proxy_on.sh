#!/bin/bash

# Git Proxy ON
echo "Setting Git Proxy..."
git config --global http.proxy http://192.168.49.1:8282
git config --global https.proxy https://192.168.49.1:8282

echo "Current Git Proxy Settings:"
git config --global --get http.proxy
git config --global --get https.proxy
