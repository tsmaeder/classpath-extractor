# MBT Extractor

The main class in MBTExtractor.java should implement the following algorigthm:

1. In the current directory, recursively find all files called "pom.xml" and sort them by path length ascending. We call this list the "todo list"
2. Take (and remove) the first file from the list and run a maven build in the parent directory of the file as follows:
    a. If the directory contains a maven wrapper (mvn or mvn.cmd), use the wrapper script to invoke maven, otherwise use an instance of the mvn cli (add a dependency to the cli to the MBTExtractor pom file)
    b. the invocation should have the following parameters: `mvn -Dmaven.ext.class.path=<lifecylceparticipant.jar> -Doutfile=<a unique path>.json test-compile ch.castleridge:classpath-extractor-maven-plugin:extract`
    c. Read the resulting file into Java Objects using the GSON library. There is an example of such a file next to this file called classpath.json
    d. Remove any entries from the "todo list" whose "pom" field is mentioned in the json file you just read
3. Repeat step 2 in this list until the todo list is empty
