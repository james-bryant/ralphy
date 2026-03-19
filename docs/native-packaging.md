# Native Packaging

Ralphy can now be packaged into native desktop app images with `jpackage`.

## Platform Rule

Build each package on its target operating system:

- Run the Windows package on Windows.
- Run the Linux package on Linux.

`jpackage` does not cross-build Windows and Linux app images from a single host.

## Commands

Windows:

```powershell
.\scripts\package-windows.ps1
```

Linux:

```bash
./scripts/package-linux.sh
```

Both scripts clear the previous platform package output, then run `clean verify` with the dedicated packaging profiles.

GitHub Actions:

- `.github/workflows/native-packages.yml` builds the Windows app image on `windows-latest` and the Linux app image on `ubuntu-latest`.
- The workflow uploads `Ralphy-windows.zip` and `Ralphy-linux.tar.gz` as CI artifacts.

## Outputs

Windows app image:

- `target/dist/windows/Ralphy/Ralphy.exe`

Linux app image:

- `target/dist/linux/Ralphy/bin/Ralphy`

Each app image also contains a bundled runtime, so the target machine does not need a separate JDK installed.

## Optional Overrides

Skip tests during packaging:

```powershell
.\scripts\package-windows.ps1 -DskipTests
```

```bash
./scripts/package-linux.sh -DskipTests
```

Request a native installer instead of an app image:

- Windows: add `-Djpackage.type=exe`
- Linux: add `-Djpackage.type=deb` or `-Djpackage.type=rpm`

Installer packaging can require extra platform tooling beyond the JDK, while the default `app-image` packaging does not.
