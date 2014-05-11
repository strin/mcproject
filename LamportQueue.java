import java.util.*;
import java.util.concurrent.*;

class LamportQueue<E> {
	private int head, tail;
	private ArrayList<E> array;
	final int depth;
	public LamportQueue(int depth) {
		this.depth = depth;
		array = new ArrayList<E>();
		for(int i = 0; i < depth; i++)
			array.add(null);
		this.head = 0;
		this.tail = -1;
	}
	public boolean add(E element) {
		if(tail-head+1 == depth)
			throw new IllegalStateException("queue is full");
		array.set((tail+1)%depth, element);
		tail++;
		return true;
	}
	public E remove() {
		if(tail < head) 
			throw new NoSuchElementException("queue is empty");
		E tmp = array.get(head%depth);
		head++;
		return tmp;
	}
	public boolean isempty() {
		return tail < head;
	}
	public boolean isfull() {
		return tail-head+1 == depth;
	}
	public static void main(String[] args) {
		LamportQueue<Integer> queue = new LamportQueue<Integer>(10);
		for(int i = 0; i < 11; i++) {
			queue.add(i);
		}
		for(int i = 0; i < 10; i++) {
			System.out.println(""+queue.remove());
		}
	}
}
