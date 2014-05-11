import os
import argparse
import scipy.io as sio
import utils
import numpy as np

parser = argparse.ArgumentParser()
parser.add_argument('-m', nargs=1, default=['stm'])
args = parser.parse_args()

method = args.m[0]

n_list = [1,2,4,8]
p_list = ["11 12 5 1 3 3 3822 0.24 0.04 0.96 2000",
		  "12 10 1 3 3 1 2644 0.11 0.09 0.92 2000", 
		  "12 10 4 3 6 2 1304 0.10 0.03 0.90 2000"]

result = np.empty((len(p_list), len(n_list)), dtype=object)

for (pi,p) in enumerate(p_list):
	for (ni,n) in enumerate(n_list):
		os.popen('$JAVA_HOME/bin/java -javaagent:deuceAgent-1.3.0.jar -cp . FirewallPacketParallelTest %s %d %s > temp.txt'%(p, n,method))
		result[pi,ni] = utils.readCounterData('temp.txt')['throughput']

sio.savemat('result_parallel_%s.mat'%method, {'result':result})
