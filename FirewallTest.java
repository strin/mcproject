import java.util.concurrent.locks.*;
import java.lang.UnsupportedOperationException;

@SuppressWarnings("unchecked")
class FirewallPacketSerialTest {
  public static void main(String[] args) {
    final int numAddrLog = Integer.parseInt(args[0]);
    final int numTrainsLog = Integer.parseInt(args[1]);
    final int meanTrainSize = Integer.parseInt(args[2]);
    final int meanTransPerComm = Integer.parseInt(args[3]);
    final int meanWindow = Integer.parseInt(args[4]);
    final int meanCommsPerAddr = Integer.parseInt(args[5]);
    final int meanWork = Integer.parseInt(args[6]);
    final float configFraction = Float.parseFloat(args[7]);
    final float pngFraction = Float.parseFloat(args[8]);
    final float accFraction = Float.parseFloat(args[9]);
    final long numMilliseconds = Long.parseLong(args[10]);

    final int segListMaxLevel = 32;
    final int numBin = 50;

    StopWatch timer = new StopWatch();

    // allocate packet source.
    PacketGenerator gen = new PacketGenerator(numAddrLog, numTrainsLog, meanTrainSize, meanTransPerComm, 
                                            meanWindow, meanCommsPerAddr, meanWork, configFraction, pngFraction, accFraction);
    // intialize "done" signal 
    PaddedPrimitiveNonVolatile<Boolean> done = new PaddedPrimitiveNonVolatile<Boolean>(false);
    PaddedPrimitive<Boolean> memFence = new PaddedPrimitive<Boolean>(false);

    SerialFireWall wall = new SerialFireWall(done, numAddrLog, segListMaxLevel, numBin, gen);
    wall.warmup();

    Thread wallThread = new Thread(wall);
    wallThread.start();

    timer.startTimer();
    try {
      Thread.sleep(numMilliseconds);
    } catch (InterruptedException ignore) {;}
    // stop 
    done.value = true;
    memFence.value = true;

    try{
        wallThread.join();
    }catch(Exception ee) {}
    timer.stopTimer();

    // report 
    System.out.println("serial firewall test");
    System.out.println("throughput: "+wall.totalCount/timer.getElapsedTime());
    System.out.println("count: "+wall.totalCount);
    System.out.println("accepted:"+wall.totalAccepted);
    System.out.println("time: "+timer.getElapsedTime());
  }
}


@SuppressWarnings("unchecked")
class FirewallPacketParallelTest {
  public static void main(String[] args) {
    final int numAddrLog = Integer.parseInt(args[0]);
    final int numTrainsLog = Integer.parseInt(args[1]);
    final int meanTrainSize = Integer.parseInt(args[2]);
    final int meanTransPerComm = Integer.parseInt(args[3]);
    final int meanWindow = Integer.parseInt(args[4]);
    final int meanCommsPerAddr = Integer.parseInt(args[5]);
    final int meanWork = Integer.parseInt(args[6]);
    final float configFraction = Float.parseFloat(args[7]);
    final float pngFraction = Float.parseFloat(args[8]);
    final float accFraction = Float.parseFloat(args[9]);
    final long numMilliseconds = Long.parseLong(args[10]);
    
    final int numWorkers = Integer.parseInt(args[11]);

    final int segListMaxLevel = 5;
    final int numBin = 50;
    final int queueDepth = 256/numWorkers;

    StopWatch timer = new StopWatch();
    // allocate and initialize Lamport queues.
    LamportQueue<Packet>[] queues = new LamportQueue[numWorkers];

    ReentrantLock[] locks = new ReentrantLock[numWorkers];
    for(int i = 0; i < queues.length; i++) {
        queues[i] = new LamportQueue<Packet>(queueDepth);
        locks[i] = new ReentrantLock();
        locks[i].lock();
    }

    // intialize "done" signal 
    PaddedPrimitiveNonVolatile<Boolean> done = new PaddedPrimitiveNonVolatile<Boolean>(false);
    PaddedPrimitive<Boolean> memFence = new PaddedPrimitive<Boolean>(false);

    // allocate packet source.
    PacketGenerator gen = new PacketGenerator(numAddrLog, numTrainsLog, meanTrainSize, meanTransPerComm, 
                                            meanWindow, meanCommsPerAddr, meanWork, configFraction, pngFraction, accFraction);

    // allocate packet workers. 
    FireWall wall = null;
    wall = new ParallelFireWallByLock(done, numAddrLog, segListMaxLevel, numBin, gen);
    wall.warmup();
    
    Thread[] workerThreads = new Thread[numWorkers];
    ParallelFireWallWorker[] workers = new ParallelFireWallWorker[numWorkers];
    for(int i = 0; i < numWorkers; i++) {
        workers[i] = new ParallelFireWallWorker(done, wall, queues, locks, i);
        workerThreads[i] = new Thread(workers[i]);
        workerThreads[i].start();
    }

    // allocate packet dispatcher.
    SerialPacketDispatcher dispatcher = new SerialPacketDispatcher(done, gen, numWorkers, queues, queueDepth);
    Thread dispatcherThread = new Thread(dispatcher);
    dispatcherThread.start();

    // start miracle ...
    try {
        Thread.sleep(200);
    }catch(InterruptedException e) {;}

    for(int i = 0; i < numWorkers; i++) {
        locks[i].unlock();
    }

    timer.startTimer();
    try {
      Thread.sleep(numMilliseconds);
    } catch (InterruptedException ignore) {;}

    // stop 
    done.value = true;
    memFence.value = true;
    
    try{
        for(int ni = 0; ni < numWorkers; ni++) {
            workerThreads[ni].join();
        }
        dispatcherThread.join();
    }catch(InterruptedException ignore) {;}
    timer.stopTimer();

    // report 
    final long totalCount = dispatcher.totalCount;
    System.out.println("parallel firewall test");
    System.out.println("throughput: "+dispatcher.totalCount/timer.getElapsedTime());
    int workerCount = 0;
    for(int ni = 0; ni < numWorkers; ni++) {
        workerCount += workers[ni].totalCount;
    }
    System.out.println("count: "+dispatcher.totalCount);
    System.out.println("worker throughput: "+workerCount/timer.getElapsedTime());
    System.out.println("worker count: "+workerCount);
    System.out.println("accepted:"+wall.getTotalAccepted());
    System.out.println("time: "+timer.getElapsedTime());
  }
}

@SuppressWarnings("unchecked")
class FirewallPacketPipelineTest {
  public static void main(String[] args) {
    final int numAddrLog = Integer.parseInt(args[0]);
    final int numTrainsLog = Integer.parseInt(args[1]);
    final int meanTrainSize = Integer.parseInt(args[2]);
    final int meanTransPerComm = Integer.parseInt(args[3]);
    final int meanWindow = Integer.parseInt(args[4]);
    final int meanCommsPerAddr = Integer.parseInt(args[5]);
    final int meanWork = Integer.parseInt(args[6]);
    final float configFraction = Float.parseFloat(args[7]);
    final float pngFraction = Float.parseFloat(args[8]);
    final float accFraction = Float.parseFloat(args[9]);
    final long numMilliseconds = Long.parseLong(args[10]);
    
    final int numWorkers = Integer.parseInt(args[11]);
    final int numdispatchers = Integer.parseInt(args[12]);

    final int segListMaxLevel = 5;
    final int numBin = 50;
    final int queueDepth = 256/numWorkers;

    StopWatch timer = new StopWatch();
    // allocate and initialize Lamport queues.
    LamportQueue<Packet>[] queues = new LamportQueue[numWorkers];

    ReentrantLock[] locks = new ReentrantLock[numWorkers];
    ReentrantLock[] dispatcher_locks = new ReentrantLock[numWorkers];

    for(int i = 0; i < queues.length; i++) {
        queues[i] = new LamportQueue<Packet>(queueDepth);
        locks[i] = new ReentrantLock();
        dispatcher_locks[i] = new ReentrantLock();
        locks[i].lock();
    }

    // intialize "done" signal 
    PaddedPrimitiveNonVolatile<Boolean> done = new PaddedPrimitiveNonVolatile<Boolean>(false);
    PaddedPrimitive<Boolean> memFence = new PaddedPrimitive<Boolean>(false);

    // allocate packet source.
    PacketGenerator[] gen = new PacketGenerator[numdispatchers];
    
    // allocate packet workers. 
    FireWall wall = null;
    wall = new ParallelFireWallByLock(done, numAddrLog, segListMaxLevel, numBin, gen[0]);
    wall.warmup();
    
    Thread[] workerThreads = new Thread[numWorkers];
    ParallelFireWallWorker[] workers = new ParallelFireWallWorker[numWorkers];
    for(int i = 0; i < numWorkers; i++) {
        workers[i] = new ParallelFireWallWorker(done, wall, queues, locks, i);
        workerThreads[i] = new Thread(workers[i]);
        workerThreads[i].start();
    }

    // allocate packet dispatcher.
    ParallelPacketDispatcher[] dispatcher = new ParallelPacketDispatcher[numdispatchers];
    Thread[] dispatcherThreads = new Thread[numdispatchers];
    for(int i = 0; i < numdispatchers; i++) {
        gen[i] = new PacketGenerator(numAddrLog, numTrainsLog, meanTrainSize, meanTransPerComm, 
                                            meanWindow, meanCommsPerAddr, meanWork, configFraction, pngFraction, accFraction);
        dispatcher[i] = new ParallelPacketDispatcher(done, gen[i], wall, numWorkers, queues, dispatcher_locks, queueDepth, numdispatchers == 1);
        dispatcherThreads[i] = new Thread(dispatcher[i]);
        dispatcherThreads[i].start();
    }

    // start miracle ...
    try {
        Thread.sleep(200);
    }catch(InterruptedException e) {;}

    for(int i = 0; i < numWorkers; i++) {
        locks[i].unlock();
    }

    timer.startTimer();
    try {
      Thread.sleep(numMilliseconds);
    } catch (InterruptedException ignore) {;}

    // stop 
    done.value = true;
    memFence.value = true;
    
    try{
        for(int ni = 0; ni < numWorkers; ni++) {
            workerThreads[ni].join();
        }
        for(int ni = 0; ni < numdispatchers; ni++) {
            dispatcherThreads[ni].join();
        }
    }catch(InterruptedException ignore) {;}
    timer.stopTimer();

    // report 
    int workerCount = 0;
    for(int ni = 0; ni < numWorkers; ni++) {
        workerCount += workers[ni].totalCount;
    }
    System.out.println("pipeline firewall test");
    System.out.println("count: "+workerCount);
    System.out.println("throughput: "+workerCount/timer.getElapsedTime());
    System.out.println("accepted:"+wall.getTotalAccepted());
    System.out.println("time: "+timer.getElapsedTime());
  }
}


