import java.util.*;

public interface PacketDispatcher extends Runnable {
	public void run();
}

class ParallelPacketDispatcher implements PacketDispatcher {
	PaddedPrimitiveNonVolatile<Boolean> done;
	final PacketGenerator gen;
	final int numSources;
	final LamportQueue<Packet>[] queues;
	final int queueDepth;
	public int totalCount;

	public ParallelPacketDispatcher(PaddedPrimitiveNonVolatile<Boolean> done,
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