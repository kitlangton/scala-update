# scala-update
[![Release Artifacts][Badge-SonatypeReleases]][Link-SonatypeReleases]

Update your Scala dependencies (both libraries and plugins) interactively. [Video Demo](https://twitter.com/kitlangton/status/1541417514823028740).

## Installation

### Homebrew (Mac Only)

```shell
brew install kitlangton/tap/scala-update
```

*If you'd like slightly faster binaries on an M1 mac, install manually with GraalVM (the next step).*

### Manually with GraalVM

#### Prerequisites

You need GraalVM installed. If you don't have it, you may check their docs [here](https://www.graalvm.org/java/quickstart/). If you're using SDKMAN!, GraalVM images are available to install easily [here](https://sdkman.io/jdks#grl).
```shell
# See Java versions and pick a GraalVM version, for example 22.1.0.r17-grl
sdk list java

sdk install java 22.1.0.r17-grl

# If you haven't set grl version as default, set it for the current terminal session
sdk use java 22.1.0.r17-grl
```

You need `native-image` installed. You can install it with GraalVM updater.
```shell
gu install native-image
```

#### Building Native Image with GraalVM

1. Build the native image with `show graalvm-native-image:packageBin`.

```shell
sbt 'show graalvm-native-image:packageBin'
# [info] ~/code/sbt-interactive-update/target/graalvm-native-image/scala-update
```

2. Move the generated binary onto your `PATH`. For example (in project root directory)
```shell
# Might need to run with sudo
cp target/graalvm-native-image/scala-update /usr/local/bin
```

## Usage

Run the command from within an sbt project folder.

```shell
scala-update
```

<img width="832" alt="CleanShot 2022-06-27 at 09 15 23@2x" src="https://user-images.githubusercontent.com/7587245/175950420-9e645dc9-f140-43f2-ad60-1c88198fa8dc.png">

The commands are displayed at the bottom of the interactive output.

Select the libraries you wish to update, then hit `Enter` to update your build files to the selected versions.

<img width="373" alt="CleanShot 2022-06-27 at 09 15 53@2x" src="https://user-images.githubusercontent.com/7587245/175950536-3785bf02-1168-4819-92ef-7a4405315a20.png">

### Grouped Depenendcies

If multiple dependencies share a single version, they will be grouped.

<img width="856" alt="CleanShot 2022-06-27 at 09 18 15@2x" src="https://user-images.githubusercontent.com/7587245/175950974-aeb75f0e-00c1-4679-a5e0-92530ddfcba5.png">

### Multiple Versions

If a dependency has multiple possible update version—for instance, a new major version and a new minor version—then you can select which version to upgrade to.

<img width="1085" alt="CleanShot 2022-06-27 at 09 20 23@2x" src="https://user-images.githubusercontent.com/7587245/175951467-c2e15bbd-e450-46d6-bff8-5bf8ffc787dd.png">

## FAQ

### How did you make the interactive CLI?

I have another library, [zio-tui](https://github.com/kitlangton/zio-tui), for creating interactive command line interactive programs just like this one.

[Badge-SonatypeReleases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/io.github.kitlangton/scala-update_2.13.svg "Sonatype Releases"
[Badge-SonatypeSnapshots]: https://img.shields.io/nexus/s/https/oss.sonatype.org/io.github.kitlangton/scala-update_2.13.svg "Sonatype Snapshots"
[Link-SonatypeSnapshots]: https://oss.sonatype.org/content/repositories/snapshots/io/github/kitlangton/scala-update_2.13/ "Sonatype Snapshots"
[Link-SonatypeReleases]: https://oss.sonatype.org/content/repositories/releases/io/github/kitlangton/scala-update_2.13/ "Sonatype Releases"
