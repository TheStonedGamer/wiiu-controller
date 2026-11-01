# iOS homebrew repositories

Both feeds are hosted on GitHub Pages and generated from published GitHub
releases. They include released versions rather than untested pull-request
builds. IPAs are verified against each release's SHA-256 manifest before
packaging or indexing.

## Zebra, Sileo and Cydia

Add this source in the package manager:

```text
https://thestonedgamer.github.io/wiiu-controller/
```

Refresh it and search for **Wii U Controller**. The package targets **rootful
jailbreaks, arm64 and iOS 12+**, including the iPhone 5s. Rootless packages are
not provided. The Debian architecture name is `iphoneos-arm`, as used by
rootful jailbreak package managers; the application itself is arm64.

If the IPA is already installed, export your layouts and remove that copy
before installing the Debian package. Its pre-install check prevents duplicate
installations. You can keep using the IPA instead; adding the source does not
change your installed app.

Packages install the ad-hoc signed app under `/Applications` and refresh its
SpringBoard registration with `uicache`. Package installation/launch on a
jailbroken phone still needs a device test; repository format, checksums,
package contents and maintainer scripts have host validation.

The flat APT repository includes Release, Packages, compressed indexes and
versioned Debian packages. The index is unsigned and served over HTTPS;
package-manager trust prompts may vary. No external tweak repository is added.

## IPA source

Add the following URL in an installer supporting the AltStore Classic source
format:

```text
https://thestonedgamer.github.io/wiiu-controller/apps.json
```

For older SideStore-format clients, `sidestore.json` at the same base URL
provides a separate legacy schema.

The main feed includes released versions, exact IPA sizes, minimum iOS versions,
icons and declared permissions. It links to the original GitHub release IPAs.
It also includes legacy app-version metadata for compatible older source clients.

The feed does not supply an Apple signing certificate or change an installer's
minimum supported iOS version. The downloadable IPA is intended for a jailbreak
with AppSync Unified; stock iOS needs a separate valid signing route. On iOS 12,
use the jailbreak source or install the IPA with Filza rather than assuming a
current sideloading client supports that OS.

## Publishing and rebuilding

The **Publish iOS repositories** workflow runs after successful main CI,
on release publication, or manually. It lists stable published releases,
verifies the IPA checksums and identities, builds the packages/indexes,
validates them, and deploys the result through GitHub Pages. Repository updates
do not modify an existing release or its tag.

For a local rebuild, save the releases API response as an array and run:

```sh
python3 tools/build_ios_repo.py releases.json output-directory --cache cache-directory
python3 tests/validate_ios_repo.py output-directory
```

The generator needs Python 3 and `dpkg-deb`. The existing iOS Docker build
image includes these tools. Generated feeds/packages are build artifacts and
are not committed to source control.
