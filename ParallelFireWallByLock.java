import java.util.Hashtable;
import java.util.concurrent.locks.*;
import org.deuce.Atomic;
import java.util.concurrent.*;

public class ParallelFireWallByLock implements FireWall {
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
			this.tablePng[i] = new Boolean(false);
			this.tableR[i] = new SegmentList(segListMaxLevel, 32);
			this.histograms[i] = new Histogram(Integer.MIN_VALUE, Integer.MAX_VALUE, numBin);
			this.fineLocks[i] = new ReentrantLock();
			this.receiverCache[i] = new ConcurrentHashMap<Integer, Boolean>();
		}
		this.fingerprint = new Fingerprint();
	}

	public void processPacket(Packet pkt) {
		if(pkt.type == Packet.MessageType.ConfigPacket) { // configuration packet.
			Lock lock = this.fineLocks[pkt.config.address];
			lock.lock();
			try{
				tablePng[pkt.config.address] = !pkt.config.personaNonGrata;
				SegmentList list = tableR[pkt.config.address];
				if(letsCache)
					receiverCache[pkt.config.address] = new ConcurrentHashMap<Integer, Boolean>();
				list.add(pkt.config.addressBegin, pkt.config.addressEnd, 
									pkt.config.acceptingRange);
			}finally{
				lock.unlock();
			}
		}else{  // data packet.
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
				Lock lock_s = this.fineLocks[Math.min(pkt.header.source, pkt.header.dest)], 
				lock_e = this.fineLocks[Math.max(pkt.header.source, pkt.header.dest)];
				lock_s.lock();
				if(lock_e != lock_s) lock_e.lock();
				try{
						long fingerprint = this.fingerprint.getFingerprint(pkt.body.iterations, pkt.body.seed);
						/*Histogram hist = histograms.get(pkt.header.tag);
						if(hist == null) {
							hist = new Histogram(Integer.MIN_VALUE, Integer.MAX_VALUE, numBin);
							histograms.put(pkt.header.tag, hist);
						}
						hist.add(fingerprint);*/
						totalAccepted++;
				}finally{
					lock_s.unlock();
					if(lock_e != lock_s) lock_e.unlock();
				}
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
