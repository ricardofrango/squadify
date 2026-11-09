#!/usr/bin/env python3
"""
Which Android apps a change touches, so CI builds, checks and publishes only those (TECHNICAL_DECISIONS §17).

    android_apps_changed.py <base> <head>

Compares <base>...<head> (from their merge base) and prints `clubs=true|false` and `coach=true|false`, one per
line, in the form $GITHUB_OUTPUT takes. An app has changed when what its APK is built from has:

- Its own modules (the clubs app: app/androidApp and app/clubs; the coach app: app/coachAndroidApp and
  app/coach) and the ones both apps build on (app/shared, core, and common/src/main, which core compiles in).
  Not their tests, iOS, web or desktop sources, nor Markdown files: none of it goes into the APK.
- The root build files (settings.gradle.kts, build.gradle.kts, gradle.properties, the Gradle wrapper): both.
- gradle/libs.versions.toml: only the libraries, plugins, bundles and versions that changed, and only for an
  app whose modules' build files use one of them (a version counts for every entry that refers to it).
- The CI files that build and check it (this script, the release check, the app's deploy workflow), so an
  edit to them runs on the same change.

When <base> isn't a commit in the history (a new branch, a force push), every app counts as changed.
"""

import re
import subprocess
import sys
import tomllib

SHARED_MODULES = ["app/shared", "core"]
APPS = {
    "clubs": {
        "modules": ["app/androidApp", "app/clubs"],
        "ci": [".github/workflows/deploy-staging.yml"],
    },
    "coach": {
        "modules": ["app/coachAndroidApp", "app/coach"],
        "ci": [".github/workflows/deploy-staging-coach.yml"],
    },
}
COMMON_CI = [
    ".github/scripts/android_apps_changed.py",
    ".github/scripts/start-release-apk.sh",
    ".github/workflows/android-release-check.yml",
]
ROOT_BUILD_FILES = ["settings.gradle.kts", "build.gradle.kts", "gradle.properties"]
CATALOG = "gradle/libs.versions.toml"

# Source sets that never go into an Android APK: tests, iOS, web, desktop.
NOT_IN_APK = re.compile(r"/src/([^/]*[Tt]est[^/]*|ios[^/]*|js[^/]*|wasm[^/]*|web[^/]*|jvm[^/]*|desktop[^/]*)/")


def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def is_commit(ref: str) -> bool:
    return bool(ref.strip("0")) and subprocess.run(
        ["git", "cat-file", "-e", f"{ref}^{{commit}}"], capture_output=True
    ).returncode == 0


def catalog_at(ref: str) -> dict:
    try:
        return tomllib.loads(git("show", f"{ref}:{CATALOG}"))
    except subprocess.CalledProcessError:
        return {}


def accessor(alias: str) -> str:
    """The Kotlin accessor of a catalog alias: `koin-android` is `koin.android`."""
    return re.sub(r"[-_]", ".", alias)


def changed_catalog_accessors(old: dict, new: dict) -> set[str]:
    """The `libs.…` accessors whose entry changed between the two catalogs."""
    def entries(section: str, catalog: dict) -> dict:
        return catalog.get(section, {})

    versions = {
        key for key in entries("versions", old).keys() | entries("versions", new).keys()
        if entries("versions", old).get(key) != entries("versions", new).get(key)
    }

    def version_ref(entry) -> str | None:
        if isinstance(entry, dict):
            version = entry.get("version")
            return version.get("ref") if isinstance(version, dict) else entry.get("version.ref")
        return None

    changed = {f"libs.versions.{accessor(key)}" for key in versions}
    changed_libraries = set()
    for section, prefix in (("libraries", "libs."), ("plugins", "libs.plugins.")):
        for alias in entries(section, old).keys() | entries(section, new).keys():
            before, after = entries(section, old).get(alias), entries(section, new).get(alias)
            if before != after or version_ref(after) in versions or version_ref(before) in versions:
                changed.add(prefix + accessor(alias))
                if section == "libraries":
                    changed_libraries.add(alias)
    for alias in entries("bundles", old).keys() | entries("bundles", new).keys():
        before, after = entries("bundles", old).get(alias), entries("bundles", new).get(alias)
        if before != after or changed_libraries & set((before or []) + (after or [])):
            changed.add("libs.bundles." + accessor(alias))
    return changed


def uses_any(build_files: list[str], accessors: set[str]) -> bool:
    if not accessors:
        return False
    # An accessor followed by more of a name is another entry (libs.koin.android vs libs.koin.androidx);
    # followed by a dot it may be a longer alias, counted as a use: a change publishes rather than not.
    pattern = re.compile("(" + "|".join(re.escape(a) for a in sorted(accessors)) + r")(?![A-Za-z0-9_])")
    for path in build_files:
        try:
            with open(path, encoding="utf-8") as file:
                if pattern.search(file.read()):
                    return True
        except FileNotFoundError:
            pass
    return False


def under(path: str, directories: list[str]) -> bool:
    return any(path == d or path.startswith(d + "/") for d in directories)


def app_changed(app: str, files: list[str], catalog_accessors: set[str]) -> bool:
    modules = APPS[app]["modules"] + SHARED_MODULES
    for path in files:
        if path in COMMON_CI or path in APPS[app]["ci"] or path in ROOT_BUILD_FILES:
            return True
        if path.startswith("gradle/wrapper/"):
            return True
        if path.endswith(".md") or NOT_IN_APK.search("/" + path):
            continue
        if under(path, modules) or path.startswith("common/src/main/"):
            return True
    return uses_any([f"{module}/build.gradle.kts" for module in modules], catalog_accessors)


def main() -> None:
    base, head = sys.argv[1], sys.argv[2]
    if not is_commit(base):
        results = {app: True for app in APPS}
    else:
        merge_base = git("merge-base", base, head).strip()
        files = git("diff", "--name-only", merge_base, head).splitlines()
        catalog_accessors = (
            changed_catalog_accessors(catalog_at(merge_base), catalog_at(head)) if CATALOG in files else set()
        )
        results = {app: app_changed(app, files, catalog_accessors) for app in APPS}
    for app, changed in results.items():
        print(f"{app}={'true' if changed else 'false'}")


if __name__ == "__main__":
    main()
