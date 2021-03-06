import java.util.Hashtable;
import java.util.concurrent.locks.*;
import java.util.concurrent.*;


interface FireWall extends Runnable {
	public void processPacket(Packet pkt);	
	public void processConfigPacket(Packet pkt);
	public void warmup();
	public int  getTotalAccepted();
}

class ParallelFireWallByLock implements FireWall {
	final int numAddressLog;
	final int segListMaxLevel;
	final int numBin;

	PacketGenerator gen;
	PaddedPrimitiveNonVolatile<Boolean> done;
	Boolean[] tablePng;
	SegmentList[] tableR;
	Histogram[] histograms;
	ReentrantLock[] fineLocks;

	ConcurrentHashMap<Integer, Boolean>[] receiverCache;
	Fingerprint fingerprint;

	public int totalCount = 0;
	public int totalAccepted = 0;
	
	public boolean letsCache = true;

	public int getTotalAccepted() {
		return this.totalAccepted;
	}
	
	public ParallelFireWallByLock(PaddedPrimitiveNonVolatile<Boolean> done,
						 int numAddressLog,
						 int segListMaxLevel,
						 int numBin,
						 PacketGenerator gen) {
		this.done = done;
		this.gen = gen;
		this.numBin = numBin;
		this.numAddressLog = numAddressLog;
		this.segListMaxLevel = segListMaxLevel;
		this.tablePng = new Boolean[1<<numAddressLog];
		this.tableR = new SegmentList[1<<numAddressLog];
		this.histograms = new Histogram[1<<numAddressLog];
		this.fineLocks = new ReentrantLock[1<<numAddressLog];
		this.receiverCache = (ConcurrentHashMap<Integer, Boolean>[])new ConcurrentHashMap[1<<numAddressLog];
		for(int i = 0; i < (1<<numAddressLog); i++) {
			this.tablePng[i] = new Boolean(true);
			this.tableR[i] = new SegmentList(segListMaxLevel, 32);
			this.tableR[i].add(0, 1<<numAddressLog, true);
			this.histograms[i] = new Histogram(Integer.MIN_VALUE, Integer.MAX_VALUE, numBin);
			this.fineLocks[i] = new ReentrantLock();
			this.receiverCache[i] = new ConcurrentHashMap<Integer, Boolean>();
		}
		this.fingerprint = new Fingerprint();
	}

	public void processConfigPacket(Packet pkt) {
		tablePng[pkt.config.address] = !pkt.config.personaNonGrata;
		SegmentList list = tableR[pkt.config.address];
		if(letsCache)
			receiverCache[pkt.config.address] = new ConcurrentHashMap<Integer, Boolean>();
		list.add(pkt.config.addressBegin, pkt.config.addressEnd, 
							pkt.config.acceptingRange);
	}

	/* optimistic locking */
	public void processPacket(Packet pkt) {
		if(pkt.type == Packet.MessageType.ConfigPacket) { // configuration packet.
			Lock lock = this.fineLocks[pkt.config.address];
			lock.lock();
			try{
				this.processConfigPacket(pkt);			
			}finally{
				lock.unlock();
			}
		}else{  // data packet.		
			Boolean sendPermission = tablePng[pkt.header.source];
			if((boolean)sendPermission == false) return;
			Lock lock = this.fineLocks[Math.min(pkt.header.source, pkt.header.dest)];
			lock.lock();			
			try{
				sendPermission = tablePng[pkt.header.source];
				if((boolean)sendPermission == false) return;
				boolean incache = false;
				if(letsCache) {
					Boolean cache = receiverCache[pkt.header.dest].get(pkt.header.source);
					if(cache != null) {
						incache = (boolean)cache;
					}else{
						SegmentList receivePermission = tableR[pkt.header.dest];
						incache = receivePermission.contains(pkt.header.source);
						receiverCache[pkt.header.dest].put(pkt.header.source, incache);
					}
				}else{
					SegmentList receivePermission = tableR[pkt.header.dest];
					incache = receivePermission.contains(pkt.header.source);
				}
				if(incache){
					long fingerprint = this.fingerprint.getFingerprint(pkt.body.iterations, pkt.body.seed);
					/*Histogram hist = histograms.get(pkt.header.tag);
					if(hist == null) {
						hist = new Histogram(Integer.MIN_VALUE, Integer.MAX_VALUE, numBin);
						histograms.put(pkt.header.tag, hist);
					}
					hist.add(fingerprint);*/
					totalAccepted++;
				}
			}finally{
				lock.unlock();
			}
		}
	}

	/* pair locking */
	public void processPacket_pairlock(Packet pkt) {
		if(pkt.type == Packet.MessageType.ConfigPacket) { // configuration packet.
			Lock lock = this.fineLocks[pkt.config.address];
			lock.lock();
			try{
				this.processConfigPacket(pkt);			
			}finally{
				lock.unlock();
			}
		}else{  // data packet.		
			Lock lock_s = this.fineLocks[Math.min(pkt.header.source, pkt.header.dest)], 
			lock_e = this.fineLocks[Math.max(pkt.header.source, pkt.header.dest)];
			lock_s.lock();
			if(lock_e != lock_s) lock_e.lock();
			try{
				Boolean sendPermission = tablePng[pkt.header.source];
				if((boolean)sendPermission == false) return;
				boolean incache = false;
				if(letsCache) {
					Boolean cache = receiverCache[pkt.header.dest].get(pkt.header.source);
					if(cache != null) {
						incache = (boolean)cache;
					}else{
						SegmentList receivePermission = tableR[pkt.header.dest];
						incache = receivePermission.contains(pkt.header.source);
						receiverCache[pkt.header.dest].put(pkt.header.source, incache);
					}
				}else{
					SegmentList receivePermission = tableR[pkt.header.dest];
					incache = receivePermission.contains(pkt.header.source);
				}
				if(incache){
					long fingerprint = this.fingerprint.getFingerprint(pkt.body.iterations, pkt.body.seed);
					/*Histogram hist = histograms.get(pkt.header.tag);
					if(hist == null) {
						hist = new Histogram(Integer.MIN_VALUE, Integer.MAX_VALUE, numBin);
						histograms.put(pkt.header.tag, hist);
					}
					hist.add(fingerprint);*/
					totalAccepted++;
				}
			}finally{
				lock_s.unlock();
				if(lock_e != lock_s) lock_e.unlock();
			}
		}
	}

	public void warmup() {
		while(gen.getPacket().type != Packet.MessageType.ConfigPacket); // trick, let numConfigPacket = 1.
		for(long i = 0; i < (long)1<<(3*numAddressLog/2); i++) {
			this.processPacket(gen.getConfigPacket());
		}
		System.out.println("warm up complete.");
	}

	public void run() {
		while(!done.value) {
			this.processPacket(gen.getPacket());
			totalCount++;
		}
	}
}


class ParallelFireWallWorker implements Runnable {
	final int id;

	PaddedPrimitiveNonVolatile<Boolean> done;
	LamportQueue<Packet>[] queues;
	Lock[] locksOnQueue;
	FireWall firewall;
	final int numQueues;
	Fingerprint fingerprint;

	public int totalAccepted = 0;
	public int totalCount = 0;

	public ParallelFireWallWorker(PaddedPrimitiveNonVolatile<Boolean> done,
						 FireWall firewall,
						 LamportQueue<Packet>[] queues,
						 Lock[] locksOnQueue,
						 int id) {
		this.done = done;
		this.firewall = firewall;
		this.queues = queues;
		this.locksOnQueue = locksOnQueue;
		this.numQueues = this.queues.length;
		this.id = id;
	}

    public void run() {
	    LamportQueue<Packet> queue = null;
	    Packet pkt = null;
	    Lock lock;
	    int queueid;
		locksOnQueue[id].lock();
		locksOnQueue[id].unlock();
	    while(!done.value || !queues[id].isempty()) {
	      if(!done.value) {
	        //queueid = (int)(Math.random()*this.numQueues);
	        queueid = id;
	      }else{
	        queueid = id;
	      }
	      queue = queues[queueid];
	      lock = locksOnQueue[queueid];
	      //if(!lock.tryLock()) continue;
	      try{
	        pkt = queue.remove();
	      }catch(Exception ee) {
	        continue;
	      }
	      finally{
	        //lock.unlock();
	      }
	      firewall.processPacket(pkt);
	      totalCount++;
    	}
  	}  

}
