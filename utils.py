import scipy.io as sio

def readCounterData(path):
	data = file(path).readlines()
	result = dict()
	for line in data:
		line = line.replace('\n', '')
		line = line.split(':')
		if line[0] == "count":
			result['count'] = int(line[1])
		elif line[0] == "time":
			result['time'] = int(line[1])
		elif line[0] == "throughput":
			result['throughput'] = int(line[1])
	return result


	
