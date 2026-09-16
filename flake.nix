{
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { nixpkgs, ... }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forEachSystem = nixpkgs.lib.genAttrs systems;
    in {
      devShells = forEachSystem (system:
        let
          pkgs = import nixpkgs {
            inherit system;
            config.allowUnfree = true;
            config.android_sdk.accept_license = true;
          };
          androidAbi = if nixpkgs.lib.hasPrefix "aarch64" system then "arm64-v8a" else "x86_64";
          androidSdk = pkgs.androidenv.composeAndroidPackages {
            platformVersions = [ "35" ];
            buildToolsVersions = [ "34.0.0" "35.0.0" ];
            abiVersions = [ androidAbi ];
            includeEmulator = true;
            includeSystemImages = true;
            systemImageTypes = [ "google_apis" ];
          };
        in {
          default = pkgs.mkShell {
            packages = [ pkgs.jdk17 pkgs.gradle pkgs.kotlin androidSdk.androidsdk ];
            JAVA_HOME = "${pkgs.jdk17}";
            ANDROID_HOME = "${androidSdk.androidsdk}/libexec/android-sdk";
            ANDROID_SDK_ROOT = "${androidSdk.androidsdk}/libexec/android-sdk";
          };
        });
    };
}
