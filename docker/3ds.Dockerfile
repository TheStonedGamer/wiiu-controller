FROM devkitpro/devkitarm@sha256:116afba8df8453961de2936ffab20dd441edf4d682856c1ec8b0e53d7ed0bbf5
RUN git init /tmp/ctr && git -C /tmp/ctr remote add origin https://github.com/3DSGuy/Project_CTR.git && git -C /tmp/ctr fetch --depth 1 origin d52c2b0e788c2d085763d7c5dcc4b2edd9ffb7d8 && git -C /tmp/ctr checkout --detach FETCH_HEAD && make -C /tmp/ctr/makerom -j2 && cp /tmp/ctr/makerom/bin/makerom /usr/local/bin/ && rm -rf /tmp/ctr
WORKDIR /project/3ds
