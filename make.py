import os
import argparse

parser = argparse.ArgumentParser()
parser.add_argument('--java', nargs=1)
args = parser.parse_args()
print "\033[1;30m"
v = os.system('make')
print '\033[0m'
if args.java != None and v == 0:
	os.system('$JAVA_HOME/bin/java -javaagent:deuceAgent-1.3.0.jar -cp . '+args.java[0]);

