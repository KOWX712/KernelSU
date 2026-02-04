#!/bin/bash

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

if [ -f "./app/build.gradle.kts" ]; then
    sed -i 's/"KernelSU_${managerVersionName}_${managerVersionCode}"/"KowSU_${managerVersionName}_${managerVersionCode}"/' ./app/build.gradle.kts
fi

echo "Done."
