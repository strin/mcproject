import java.util.*;
import org.deuce.Atomic;
import java.util.concurrent.locks.*;
import java.util.concurrent.*;

public class SerialFireWall implements Runnable {
	final int numAddressLog;
	final int segListMaxLevel;
	final int numBin;

	PacketGenerator gen;
	PaddedPrimitiveNonVolatile<Boolean> done;
	Hashtable<Integer, Boolean> tablePng;
	Hashtable<Integer, SegmentList> tableR;
	Hashtable<Integer, Histogram> histograms;
	
	Fingerprint fingerprint;

	public int totalCount = 0;
	public int totalAccepted = 0;


	public SerialFireWall(PaddedPrimitiveNonVolatile<Boolean> done,
						 int numAddressLog,
						 int segListMaxLevel,
						 int numBin,
						 PacketGenerator gen) {
		this.done = done;
		this.gen = gen;
		this.numBin = numBin;
		this.numAddressLog = numAddressLog;
		this.segListMaxLevel = segListMaxLevel;
		this.tablePng = new Hashtable<Integer, Boolean>(1<<numAddressLog);
		this.tableR = new Hashtable<Integer, SegmentList>(1<<numAddressLog);
		this.histograms = new Hashtable<Integer, Histogram>(1<<numAddressLog);
		this.fingerprint = new Fingerprint();
	}

	public void processPacket(Packet pkt) {
		if(pkt.type == Packet.MessageType.ConfigPacket) { // configuration packet.
			tablePng.put(pkt.config.address, !pkt.config.personaNonGrata);
			SegmentList list = tableR.get(pkt.config.address);
			if(list == null) {
				list = new SegmentList(segListMaxLevel, 32);
				tableR.put(pkt.config.address, list);
			}
			list.add(pkt.config.addressBegin, pkt.config.addressEnd, 
								pkt.config.acceptingRange);
		}else{  // data packet.
			Boolean sendPermission = tablePng.get(pkt.header.source);
			SegmentList receivePermission = tableR.get(pkt.header.dest);
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
		}
	}

	public void warmup() {
		for(long i = 0; i < (long)1<<(3*numAddressLog/2); i++) {
			this.processPacket(gen.getConfigPacket());
		}
	}

	public void run() {
		while(!done.value) {
			this.processPacket(gen.getPacket());
			totalCount++;
		}
	}
}

