FROM ubuntu:24.04
RUN apt-get update && apt-get install -y --no-install-recommends clang lld git make g++ pkg-config libssl-dev libplist-dev python3 python3-pil curl ca-certificates xz-utils && rm -rf /var/lib/apt/lists/*
RUN git clone https://github.com/ProcursusTeam/ldid.git /tmp/ldid && git -C /tmp/ldid checkout af86971ae72ec3ed3d0a699107c4e882324c941b && make -C /tmp/ldid -j2 VERSION=2.1.5-controller && make -C /tmp/ldid install && rm -rf /tmp/ldid
RUN mkdir -p /opt/ios-sdk && curl -fsSL https://github.com/theos/sdks/releases/download/master-146e41f/iPhoneOS12.4.sdk.tar.xz -o /tmp/sdk.tar.xz && echo '6880a737bebc179e282aa6990c602536a2f9fe1614c4b3e0cc0a9a9a359145f7  /tmp/sdk.tar.xz' | sha256sum -c - && tar -xJf /tmp/sdk.tar.xz -C /opt/ios-sdk && rm /tmp/sdk.tar.xz
ENV IOS_SDK_PATH=/opt/ios-sdk/iPhoneOS12.4.sdk
WORKDIR /project/ios
