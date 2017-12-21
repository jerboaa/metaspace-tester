# Test OpenJDK's metaspace behaviour in Containers

This repository contains a simple program which leaks
metaspace data.

Once the container approaches the memory limit, the GC
will no longer be able to make progress. Thus, you'd
likely get for JDK 8:

    java.lang.OutOfMemoryError: GC overhead limit exceeded

## Basic Java Usage

    $ javac MetaspaceLeak.java
    $ java -XX:MaxRAM=200m -XX:MaxMetaspaceSize=200m -XX:MaxRAMFraction=2 MetaspaceLeak
    ----------------------------------------------------
    Non-heap memory. Used: 4304KB, committed: 7872KB
    Heap-memory. Used: 502KB, committed: 7680KB
    ----------------------------------------------------
    Running metaspace leak test.
    VmRSS:	   27380 kB
    VmRSS:	   76748 kB
    [...]
    VmRSS:	  314312 kB
    VmRSS:	  314588 kB
    VmRSS:	  314700 kB
    ----------------------------------------------------
    Non-heap memory. Used: 81082KB, committed: 154368KB
    Heap-memory. Used: 79772KB, committed: 91136KB
    ----------------------------------------------------
    Exception in thread "main" java.lang.OutOfMemoryError: GC overhead limit exceeded
    [...]

## Container Usage

    $ sudo docker build -t metaspace-leak .
    $ sudo docker run --memory=200m --memory-swap=0 -it --rm \
         -e JAVA_OPTS="-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap" \
         metaspace-leak

Alternatively start the container with the option to print GC details. You'll
notice that at some point you'll reach a point (close to the memory limit)
where the GC is more or less running constantly.

    $ sudo docker run --memory=200m --memory-swap=0 -it --rm \
         -e JAVA_OPTS="-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:+PrintGCDetails" \
         metaspace-leak
