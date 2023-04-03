package io.openems.edge.airconditioner.hydac4kw;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.google.common.cache.CacheBuilder;

import io.openems.edge.common.startstoppratelimited.StartStoppEvent;

public final class EventLog<T> {
	private Deque<T> dequeue;
	private int initialSize;
	
	public EventLog (int size) {
		this.initialSize = size;
		this.dequeue = new ArrayDeque<T>(size);
	}
	
	public void push(T newElement) {
		if (this.dequeue.size() >= this.initialSize) {
			this.dequeue.pollFirst();
		}
		this.dequeue.push(newElement);
	}
	
	public boolean pushIf(Predicate<T> condition, T value) {
		if (condition.test(value)) {
			this.push(value);
			return true;
		} else {
			return false;
		}
	}
	
	public Stream<T> asStream() {
		return this.dequeue.stream();
	}
	
	@Override
	public String toString() {
		return this.asStream().toList().toString();
	}

	public Optional<T> first() {
		return Optional.ofNullable(this.dequeue.peekFirst());
	}
}
