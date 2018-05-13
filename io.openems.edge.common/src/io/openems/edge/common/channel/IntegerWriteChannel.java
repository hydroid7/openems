package io.openems.edge.common.channel;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.openems.edge.common.channel.doc.ChannelId;
import io.openems.edge.common.component.OpenemsComponent;

public class IntegerWriteChannel extends IntegerReadChannel implements WriteChannel<Integer> {

	public IntegerWriteChannel(OpenemsComponent component, ChannelId channelId) {
		super(component, channelId);
	}

	private Optional<Integer> nextWriteValueOpt = Optional.empty();

	/**
	 * Internal method. Do not call directly.
	 * 
	 * @param value
	 */
	@Deprecated
	@Override
	public void _setNextWriteValue(Integer value) {
		this.nextWriteValueOpt = Optional.ofNullable(value);
	}

	/**
	 * Internal method. Do not call directly.
	 * 
	 * @param value
	 */
	@Deprecated
	@Override
	public Optional<Integer> _getNextWriteValue() {
		return this.nextWriteValueOpt;
	}

	/*
	 * onSetNextWrite
	 */
	private final List<Consumer<Integer>> onSetNextWriteCallbacks = new CopyOnWriteArrayList<>();

	@Override
	public List<Consumer<Integer>> getOnSetNextWrites() {
		return this.onSetNextWriteCallbacks;
	}

	@Override
	public void onSetNextWrite(Consumer<Integer> callback) {
		this.onSetNextWriteCallbacks.add(callback);
	}

}
