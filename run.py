import os
import argparse
import scipy.io as sio
import utils
import numpy as np
import subprocess


n_list = [1,2,4,8]
p_list = ["11 12 5 1 3 3 3822 0.24 0.04 0.96 2000",
		  "12 10 1 3 3 1 2644 0.11 0.09 0.92 2000", 
		  "12 10 4 3 6 2 1304 0.10 0.03 0.90 2000",
		  "14 10 5 5 6 2 315 0.08 0.05 0.90 2000",
		  "15 14 9 16 7 10 4007 0.02 0.10 0.84 2000",
		  "15 15 9 10 9 9 7125 0.01 0.20 0.77 2000",
		  "15 15 10 13 8 10 5328 0.04 0.18 0.80 2000",
		  "16 14 15 12 9 5 8840 0.04 0.19 0.76 2000"
		  ]
num_runtime = 1

result = np.empty((len(p_list), len(n_list), num_runtime), dtype=object)

os.popen('rm *.stdout')
for (pi,p) in enumerate(p_list):
	for (ni,n) in enumerate(n_list):
		for ri in range(num_runtime):
			print 'pi, ni, ri', pi, ni, ri
			strs = ['']
			while not 'throughput' in ' '.join(strs):
				ret = os.popen('cqsub $JAVA_HOME/bin/java FirewallPacketParallelTest %s %d'%(p, n))
				strs = ret.readlines()
				print strs
			os.popen('mv *.stdout temp.txt')
			result[pi,ni,ri] = utils.readCounterData('temp.txt')['throughput']
			print result

sio.savemat('result_parallel_%s.mat', {'result':result})
