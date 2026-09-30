import os
import sys

candidates = [
    # Android Studio / SDK
    r"C:\Program Files\Android\Android Studio\jbr",
    r"C:\Program Files\Android\Android Studio\jre",
    r"C:\Program Files\Android\jbr",
    r"C:\Program Files\Google\Android Studio\jbr",
    r"C:\Users\AMX\AppData\Local\Android\android-studio\jbr",
    r"C:\Users\AMX\AppData\Local\Programs\Android Studio\jbr",
    r"D:\Android Studio\jbr",
    r"D:\Android\Android Studio\jbr",
    r"D:\Android\jbr",
    r"D:\Program Files\Android\Android Studio\jbr",
    # JetBrains IDEs
    r"D:\JetBrains\CLion 2021.1.3\jbr",
    r"D:\JetBrains\IntelliJ IDEA\jbr",
    r"D:\JetBrains\PyCharm\jbr",
    r"D:\JetBrains\WebStorm\jbr",
    # Standard Java paths
    r"C:\Program Files\Java",
    r"C:\Program Files\Eclipse Adoptium",
    r"C:\Program Files\Microsoft",
    r"C:\Program Files\Zulu",
    r"C:\Program Files\BellSoft",
    r"C:\Users\AMX\.jdks",
    r"C:\Users\AMX\.gradle\jdks",
    r"D:\Java",
    r"D:\jdk",
    r"D:\JDK",
    r"D:\tools",
    r"C:\tools",
]

found = []
for c in candidates:
    if os.path.exists(c):
        found.append(c)
        java_exe = os.path.join(c, "bin", "java.exe")
        if os.path.exists(java_exe):
            print(f"FOUND JAVA: {java_exe} in {c}")
        else:
            # check subdirs
            try:
                for sub in os.listdir(c):
                    subpath = os.path.join(c, sub)
                    jexe = os.path.join(subpath, "bin", "java.exe")
                    if os.path.exists(jexe):
                        print(f"FOUND JAVA: {jexe} in {subpath}")
            except Exception as e:
                pass

print("Finished checking candidate bases. Existing directories:", found)
