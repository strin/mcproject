JFLAGS= 
JC= $(JAVA_HOME)"/bin/javac"
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
	Statistics.java \
	Fingerprint.java \
	PaddedPrimitive.java \
	RandomGenerator.java \
	PacketGenerator.java \
	SerialFireWall.java \
	ParallelFireWall.java \
	PacketDispatcher.java \
	SegmentList.java \
	StopWatch.java \
	FirewallTest.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
