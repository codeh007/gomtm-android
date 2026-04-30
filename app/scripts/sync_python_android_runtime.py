#!/usr/bin/env python3

import argparse
import shutil
import tarfile
import urllib.request
from pathlib import Path


PYTHON_VERSION = "3.14.4"
PYTHON_ARCH = "aarch64"
RUNTIME_RELEASE = f"{PYTHON_VERSION}-{PYTHON_ARCH}"
ARCHIVE_URL = (
    f"https://www.python.org/ftp/python/{PYTHON_VERSION}/"
    f"python-{PYTHON_VERSION}-{PYTHON_ARCH}-linux-android.tar.gz"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Vendor official Python Android runtime into the app build tree.",
    )
    parser.add_argument("--output-root", required=True)
    return parser.parse_args()


def download_if_missing(url: str, archive_path: Path) -> None:
    archive_path.parent.mkdir(parents=True, exist_ok=True)
    if archive_path.exists():
        return
    with urllib.request.urlopen(url) as response, archive_path.open("wb") as target:
        target.write(response.read())


def extract_tar_gz(archive_path: Path, destination: Path) -> None:
    destination.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive_path, "r:gz") as archive:
        archive.extractall(destination)


def write_base_package(release_root: Path) -> None:
    package_file = release_root / "site-packages-base" / "gomtm_python_base" / "__init__.py"
    package_file.parent.mkdir(parents=True, exist_ok=True)
    package_file.write_text(
        "package_name = \"gomtm_python_base\"\n"
        "bootstrap_gate = \"base-package\"\n\n"
        "__all__ = [\"bootstrap_gate\", \"package_name\"]\n",
        encoding="utf-8",
    )


def main() -> int:
    args = parse_args()
    output_root = Path(args.output_root)
    release_root = output_root / RUNTIME_RELEASE
    cache_dir = output_root.parent / "downloads"
    archive_path = cache_dir / f"python-{PYTHON_VERSION}-{PYTHON_ARCH}-linux-android.tar.gz"

    shutil.rmtree(release_root, ignore_errors=True)
    download_if_missing(ARCHIVE_URL, archive_path)
    extract_tar_gz(archive_path, release_root)
    write_base_package(release_root)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
