#!/usr/bin/env python3
"""Report application classes and methods that nothing uses.

Usage: ./gradlew classes && tools/analysis/dead-code.py    (needs JAVA_HOME for jdeps and javap)

Classes: unreachable in the jdeps class graph from the entry points below, which are the main class,
the reflectively loaded video plugin and every class Datavyu_API.rb or a bundled script names.

Methods: declared in build/classes/java/main but never invoked by name anywhere in the compiled
code, and not named in a Ruby script or a resource bundle. Methods only tests call are reported.
Matching is by name only, so a method that shares its name with a used one is never reported: the
list undercounts. A listener method listed here is one that no event source ever fires. Swing and framework
callbacks invoked by the JDK are listed in CALLBACKS and skipped; @Action methods are skipped too
because the Swing Application Framework calls them by name.
"""
import collections
import glob
import os
import re
import subprocess
import sys

CLASSES = "build/classes/java/main"
SCRIPTS = ["src/main/resources/*.rb", "src/main/resources/scripts/*.rb", "src/main/favorites/*.rb"]
ENTRY_POINTS = {"org.datavyu.Datavyu", "org.datavyu.plugins.javafx.JavaFxVideoPlugin"}
CALLBACKS = set("""
    main run call toString equals hashCode compareTo compare accept getDescription values valueOf
    actionPerformed propertyChange stateChanged valueChanged itemStateChanged adjustmentValueChanged
    keyPressed keyReleased keyTyped mouseClicked mousePressed mouseReleased mouseEntered mouseExited
    mouseDragged mouseMoved mouseWheelMoved focusGained focusLost windowGainedFocus windowLostFocus
    windowClosing windowClosed windowOpened windowActivated windowDeactivated windowIconified
    windowDeiconified componentResized componentMoved componentShown componentHidden
    paint paintComponent updateUI isOpaque getPreferredSize getMinimumSize getMaximumSize
    getPreferredScrollableViewportSize getScrollableBlockIncrement getScrollableUnitIncrement
    getScrollableTracksViewportWidth getScrollableTracksViewportHeight
    doInBackground done process caretUpdate insertUpdate removeUpdate changedUpdate
    undo redo canUndo canRedo getPresentationName die isSignificant addEdit replaceEdit
    layoutContainer preferredLayoutSize minimumLayoutSize addLayoutComponent removeLayoutComponent
    getTreeCellRendererComponent getTableCellRendererComponent getListCellRendererComponent
    getColumnCount getRowCount getValueAt setValueAt isCellEditable getColumnName getColumnClass tableChanged
    getChildCount getChild getIndexOfChild getRoot isLeaf valueForPathChanged
    addTreeModelListener removeTreeModelListener getSize getElementAt addListDataListener removeListDataListener
    construct representData dispatchKeyEvent undoableEditHappened menuSelected menuDeselected menuCanceled
    lostOwnership initialize startup shutdown ready canExit willExit
    drop dragEnter dragOver dragExit dropActionChanged
""".split())


def tool(name):
    return os.path.join(os.environ.get("JAVA_HOME", ""), "bin", name) if os.environ.get("JAVA_HOME") else name


def class_files():
    return sorted(glob.glob(f"{CLASSES}/**/*.class", recursive=True))


def class_name(path):
    return os.path.relpath(path, CLASSES)[:-len(".class")].replace(os.sep, ".")


def script_text():
    paths = [p for pattern in SCRIPTS for p in glob.glob(pattern)]
    paths += glob.glob("src/main/resources/**/*.properties", recursive=True)
    return "\n".join(open(p, encoding="utf-8", errors="ignore").read() for p in paths)


def unreachable_classes(scripts):
    graph = collections.defaultdict(set)
    output = subprocess.run([tool("jdeps"), "-verbose:class", "-e", r"org\.datavyu\..*", "-filter:none", CLASSES],
                            capture_output=True, text=True, check=True).stdout
    for line in output.splitlines():
        match = re.match(r"\s+(org\.datavyu\.\S+)\s+->\s+(org\.datavyu\.\S+)", line)
        if match:
            graph[match.group(1)].add(match.group(2))
    roots = ENTRY_POINTS | set(re.findall(r"org\.datavyu\.[\w.]+[A-Z]\w*", scripts))
    seen, stack = set(), list(roots)
    while stack:
        node = stack.pop()
        if node not in seen:
            seen.add(node)
            stack.extend(graph[node])
    return sorted(c for c in map(class_name, class_files()) if "$" not in c and c not in seen)


def unused_methods(scripts):
    # -v adds the constant pool, whose method handles are the targets of method references and lambdas.
    output = subprocess.run([tool("javap"), "-p", "-c", "-v", *class_files()], capture_output=True, text=True,
                            check=True).stdout
    source = "\n".join(open(p, encoding="utf-8").read() for p in glob.glob("src/main/java/**/*.java", recursive=True))
    actions = set(re.findall(r"@Action[^\n]*\n\s*(?:public |protected )?[\w<>\[\], ]+ (\w+)\(", source))
    declared, used, current = [], set(), None
    for line in output.splitlines():
        header = re.search(r"(?:class|interface|enum) (org\.datavyu\.[\w.$]+)", line)
        if header and not line.startswith(" "):
            current = header.group(1)
            continue
        method = re.match(r"^  (?:(?:public|protected|private|static|final|abstract|synchronized|native|default)\s+)*"
                          r"[\w.$<>\[\], ?]+ (\w+)\(", line)
        if method and current:
            declared.append((current.split("$")[0], method.group(1)))
        used.update(re.findall(r"(?:Method|InterfaceMethod) (?:[\w/$]+\.)?(\w+):", line))
        used.update(re.findall(r"REF_\w+ (?:[\w/$]+\.)?(\w+):", line))
    unused = collections.defaultdict(set)
    for owner, name in declared:
        if (name in used or name in CALLBACKS or name in actions or "$" in name
                or re.search(rf"\b{re.escape(name)}\b", scripts)):
            continue
        unused[owner].add(name)
    return unused


def main():
    if not os.path.isdir(CLASSES):
        sys.exit(f"{CLASSES} not found; run ./gradlew classes from the repository root first")
    scripts = script_text()
    classes = unreachable_classes(scripts)
    print(f"Unreachable classes ({len(classes)}):")
    for name in classes:
        print(f"  {name.removeprefix('org.datavyu.')}")
    methods = unused_methods(scripts)
    print(f"\nMethods never invoked ({sum(map(len, methods.values()))}), excluding those in unreachable classes:")
    for owner in sorted(methods):
        if owner not in classes:
            print(f"  {owner.removeprefix('org.datavyu.')}: {', '.join(sorted(methods[owner]))}")


if __name__ == "__main__":
    main()
