# EDT Form Update

Batch form update plugin for 1C:EDT configuration extensions.

The plugin adds an `Update forms...` command to the EDT configuration navigator context menu. It scans extension forms, lets you choose which forms to update, and calls the native EDT form update mechanism programmatically.

## Features

- Works inside 1C:EDT.
- Adds a context menu command for extension projects and their objects.
- Scans extension forms from project metadata resources.
- Shows a checkbox table for batch selection.
- Keeps the update dialog open while processing.
- Shows per-form status: success or failure.
- Shows a progress bar and an in-dialog log with failure messages.

## Installation

In 1C:EDT:

1. Open `Help -> Install New Software...`.
2. Click `Add...`.
3. Use this update site URL:

   ```text
   https://xelgo.github.io/edt-form-update-plugin/update-site/
   ```

4. Install `EDT Form Update`.
5. Restart EDT.

You can also install from a local archive after building the project:

```text
repositories/ru.xelgo.edt.formupdate.repository/target/ru.xelgo.edt.formupdate.repository.zip
```

## Usage

1. Open an EDT workspace with a configuration extension project.
2. In the configuration navigator, right-click the extension project or an object inside it.
3. Run `Update forms...`.
4. Select the forms to update.
5. Click `Update`.
6. Watch the status column and the log area for results.

## Compatibility

- Tested with `1C:EDT 2025.2.6.4`.
- Built with Java 17.
- Target platform: `targets/default/default.target`.

## Build

Requirements:

- JDK 17
- Maven 3.9.4+

Build:

```powershell
mvn package -DskipTests
```

The p2 update site archive is created at:

```text
repositories/ru.xelgo.edt.formupdate.repository/target/ru.xelgo.edt.formupdate.repository.zip
```

The GitHub Pages update site is stored in:

```text
docs/update-site/
```

Refresh it after a build by copying the generated repository contents:

```powershell
Copy-Item repositories/ru.xelgo.edt.formupdate.repository/target/repository/* docs/update-site/ -Recurse -Force
```

## Project Structure

- `bundles/ru.xelgo.edt.formupdate.ui` - UI plugin with the navigator command and batch update dialog.
- `features/ru.xelgo.edt.formupdate.feature` - installable Eclipse feature.
- `repositories/ru.xelgo.edt.formupdate.repository` - p2 repository project.
- `targets/default/default.target` - EDT 2025.2 target platform.
- `docs/update-site` - published p2 update site for GitHub Pages.
