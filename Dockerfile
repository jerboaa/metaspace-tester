FROM centos:7
RUN yum -y install java-1.8.0-openjdk-devel && \
    yum clean all
COPY MetaspaceLeak.java /
RUN javac /MetaspaceLeak.java
CMD java $JAVA_OPTS MetaspaceLeak
