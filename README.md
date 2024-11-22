# CheckGitCRLFChanges

## Overview

CheckGitCRLFChanges is an IntelliJ IDEA plugin that adds a right-click menu item to revert CRLF-only changes in the Git change list view. 
This plugin helps developers maintain consistent line endings by reverting changes that only involve CRLF to LF conversions and is especiassly useful when workiing with plugins that generate code that doesnt comply with the CRLF rules of the project.

## Features

- Revert CRLF-only changes in selected files.
- Display progress bar while processing files.
- Log warnings and errors for files that cannot be processed.

## Usage

1. Open a project with Git version control in IntelliJ IDEA.
2. Right-click on a file or selection of files in the Git change list view.
3. Select **Revert CRLF Only Changes** from the context menu.
4. The plugin will process the selected files and revert any CRLF-only changes.

## Development

### Prerequisites

- IntelliJ IDEA
- Java
- Gradle

### Building the Plugin

To build the plugin, run the following command:
```sh
./gradlew build
