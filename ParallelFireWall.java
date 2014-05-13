import java.util.Hashtable;
import java.util.concurrent.locks.*;
import org.deuce.Atomic;
import java.util.concurrent.*;


interface FireWall extends Runnable {
	public void processPacket(Packet pkt);
	public void processDataPacket(Packet
	public void warmup();
	public int  getTotalAccepted();
}

public class ParallelFireWall implements FireWall {
	final int numAddressLog;
	final int segListMaxLevel;
	final int numBin;

	PacketGenerator gen;
	PaddedPrimitiveNonVolatile<Boolean> done;
	Boolean[] tablePng;
	SegmentList[] tableR;
	ConcurrentHashMap<Integer, Histogram> histograms;
	ReentrantLock[] fineLocks;

	Fingerprint fingerprint;

	public int totalCount = 0;
	public int totalAccepted = 0;

	public int getTotalAccepted() {
		return this.totalAccepted;
	}

	public ParallelFireWall(PaddedPrimitiveNonVolatile<Boolean> done,
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
		this.histograms = new ConcurrentHashMap<Integer, Histogram>(1<<numAddressLog);
		this.fineLocks = new ReentrantLock[1<<numAddressLog];
		this.fingerprint = new Fingerprint();
	}

	private void initFineLockIfNecessary(Integer address) {
		if(this.fineLocks[address] == null) 
			this.fineLocks[address] = new ReentrantLock();
	}

	public void processPacket(Packet pkt) {
		if(pkt.type == Packet.MessageType.ConfigPacket) { // configuration packet.
			// initFineLockIfNecessary(pkt.config.address);
			// Lock lock = this.fineLocks.get(pkt.config.address);
			// lock.lock();
			try{
				tablePng[pkt.config.address] = !pkt.config.personaNonGrata;
				SegmentList list = tableR[pkt.config.address];
				if(list == null) {
					list = new SegmentList(segListMaxLevel, 32);
					tableR[pkt.config.address] = list;
				}
				list.add(pkt.config.addressBegin, pkt.config.addressEnd, 
									pkt.config.acceptingRange);
			}finally{
				// lock.unlock();
			}
		}else{  // data packet.
			// initFineLockIfNecessary(pkt.header.source);
			// initFineLockIfNecessary(pkt.header.dest);
			// Lock lock_s = this.fineLocks.get(Math.min(pkt.header.source, pkt.header.dest)), 
			// 	lock_e = this.fineLocks.get(Math.max(pkt.header.source, pkt.header.dest));
			// lock_s.lock();
			// if(lock_e != lock_s) lock_e.lock();
			try{
				Boolean sendPermission = tablePng[pkt.header.source];
				SegmentList receivePermission = tableR[pkt.header.dest];
				if(sendPermission != null && (boolean)sendPermission == true 
					&& receivePermission != null && receivePermission.contains(pkt.header.dest)) {
					long fingerprint = this.fingerprint.getFingerprint(pkt.body.iterations, pkt.body.seed);
					Histogram hist = histograms.get(pkt.header.tag);
					if(hist == null) {
						hist = new Histogram(Integer.MIN_VALUE, Integer.MAX_VALUE, numBin);
						histograms.put(pkt.header.tag, hist);
					}
					hist.add(fingerprint);
					totalAccepted++;
				}
			}finally{
				// lock_s.unlock();
				// if(lock_e != lock_s) lock_e.unlock();
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
	    while(!done.value || !queues[id].isempty()) {
	      if(!done.value) {
	        // queueid = (int)(Math.random()*this.numQueues);
	        queueid = id;
	      }else{
	        queueid = id;
	      }
	      queue = queues[queueid];
	      lock = locksOnQueue[queueid];
	      // if(!lock.tryLock()) continue;
	      try{
	        pkt = queue.remove();
	      }catch(Exception ee) {
	        continue;
	      }
	      finally{
	        // lock.unlock();
	      }
	      firewall.processPacket(pkt);
	      totalCount++;
    	}
  	}  

}
