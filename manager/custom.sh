#!/bin/bash

# Generate 3 custom lowercase words
word1="com"
word2="kowx712"
word3="supermanager"

# Export variables for use in find -exec
export word1 word2 word3

# Rename directories
find . -depth -type d -name 'me' -execdir mv {} "$word1" \;
find . -depth -type d -name 'weishu' -execdir mv {} "$word2" \;
find . -depth -type d -name 'kernelsu' -execdir mv {} "$word3" \;

# Replace inside files
find . -type f -exec sed -i \
    -e "s/me\.weishu\.kernelsu/$word1.$word2.$word3/g" \
    -e "s/me\/weishu\/kernelsu/$word1\/$word2\/$word3/g" \
    -e "s/me_weishu_kernelsu/${word1}_${word2}_${word3}/g" {} +

# Done
echo "Done."
