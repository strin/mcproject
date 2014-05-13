import java.util.*;
import java.util.concurrent.locks.*;

public interface PacketDispatcher extends Runnable {
	public void run();
}

class SerialPacketDispatcher implements PacketDispatcher {
	PaddedPrimitiveNonVolatile<Boolean> done;
	final PacketGenerator gen;
	final int numSources;
	final LamportQueue<Packet>[] queues;
	final int queueDepth;
	public int totalCount;

	public SerialPacketDispatcher(PaddedPrimitiveNonVolatile<Boolean> done,
									PacketGenerator gen,
									int numSources,
									LamportQueue<Packet>[] queues,
									int queueDepth) {
		this.done = done;
		this.gen = gen;
		this.numSources = numSources;
		this.queues = queues;
		this.queueDepth = queueDepth;
		this.totalCount = 0;
	}

	public void run() {
		while(!done.value) {
			for(int i = 0; i < numSources; i++) {
				if(queues[i].isfull()) {
					continue;
				}
				Packet pkt	= gen.getPacket();
				try {
					queues[i].add(pkt);
					totalCount++;
				}catch(IllegalStateException e) {
					throw e;
				}
			}
		}
	}
}

class ParallelPacketDispatcher(


	PaddedPrimitiveNonVolatile<Boolean> done;
	final PacketGenerator gen;
	final int numSources;
	final LamportQueue<Packet>[] queues;
	final int queueDepth;
	FireWall firewall;
	Lock[] lock;
	public int totalCount;

	public ParallelPacketDispatcher(PaddedPrimitiveNonVolatile<Boolean> done, 
									PacketGenerator gen, 
									FireWall firewall,
									int numSources, 
									LamportQueue<Packet>[] queues, 
									Lcok[] lock,
									int queueDepth,
									) {						
		this.done = done;
		this.gen = gen;
	
	this.numSources = numSources;
		this.queues = queues;
		this.queueDepth = queueDepth;
		this.totalCount = 0;
		this.firewall = firewall;
		this.lock = lock;
	}

	public void run() {
		while(!done.value) {
			for(int i = 0; i < numSources; i++) {
				if(queues[i].isfull()) {
					continue;
				}
				lock[i].lock();
				Packet pkt	= gen.getPacket();
				try {
					queues[i].add(pkt);
				}catch(IllegalStateException e) {
					throw e;
				}finally{
					lock[i].unlock();
				}
				totalCount++
			}
		}
	}
}

