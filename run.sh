n=2
echo n=$n
cqsub $JAVA_HOME/bin/java FirewallPacketParallelTest 11 12 5 1 3 3 3822 0.24 0.04 0.96 2000 $n
cqsub $JAVA_HOME/bin/java FirewallPacketParallelTest 12 10 1 3 3 1 2644 0.11 0.09 0.92 2000 $n
cqsub $JAVA_HOME/bin/java FirewallPacketParallelTest 12 10 4 3 6 2 1304 0.10 0.03 0.90 2000 $n
cqsub $JAVA_HOME/bin/java FirewallPacketParallelTest 14 10 5 5 6 2 315 0.08 0.05 0.90 2000 $n
cqsub $JAVA_HOME/bin/java FirewallPacketParallelTest 15 14 9 16 7 10 4007 0.02 0.10 0.84 2000 $n
cqsub $JAVA_HOME/bin/java FirewallPacketParallelTest 15 15 9 10 9 9 7125 0.01 0.20 0.77 2000 $n
cqsub $JAVA_HOME/bin/java FirewallPacketParallelTest 15 15 10 13 8 10 5328 0.04 0.18 0.80 2000 $n
cqsub $JAVA_HOME/bin/java FirewallPacketParallelTest 16 14 15 12 9 5 8840 0.04 0.19 0.76 2000 $n
