#!/usr/bin/env python3
"""List known vulnerabilities in the runtime classpath, using the OSV.dev database.

Usage: ./gradlew -q dependencies --configuration runtimeClasspath | tools/analysis/osv-check.py

Sends only public Maven coordinates (group:artifact and version) to api.osv.dev. Prints one line
per vulnerable dependency with its advisory IDs, and exits 1 if any were found.
"""
import json
import re
import sys
import urllib.request

COORDINATE = re.compile(r"([\w.-]+):([\w.-]+)(?::([\w.+-]+))?(?: -> ([\w.+-]+))?")


def resolved_dependencies(lines):
    found = set()
    for line in lines:
        match = COORDINATE.search(line.split("--- ", 1)[-1])
        if match and "--- " in line:
            group, artifact, requested, resolved = match.groups()
            version = resolved or requested
            if version:
                found.add((f"{group}:{artifact}", version))
    return sorted(found)


def query(dependencies):
    body = {"queries": [{"package": {"ecosystem": "Maven", "name": name}, "version": version}
                        for name, version in dependencies]}
    request = urllib.request.Request("https://api.osv.dev/v1/querybatch", json.dumps(body).encode(),
                                     {"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)["results"]


def main():
    dependencies = resolved_dependencies(sys.stdin)
    if not dependencies:
        sys.exit("No dependencies on stdin; pipe in ./gradlew -q dependencies --configuration runtimeClasspath")
    vulnerable = 0
    for (name, version), result in zip(dependencies, query(dependencies)):
        ids = sorted(v["id"] for v in result.get("vulns", []))
        if ids:
            vulnerable += 1
            print(f"{name}:{version}  {len(ids)}  {' '.join(ids)}")
    print(f"{vulnerable} of {len(dependencies)} runtime dependencies have known vulnerabilities")
    sys.exit(1 if vulnerable else 0)


if __name__ == "__main__":
    main()
