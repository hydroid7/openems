package io.openems.edge.airconditioner.hydac4kw;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.startstoppratelimited.RateLimitedStartStoppable;
import io.openems.edge.common.startstoppratelimited.StartFrequency;
import io.openems.edge.io.hal.api.DigitalIn;
import io.openems.edge.io.hal.api.DigitalOut;
import io.openems.edge.io.hal.api.Led;
import io.openems.edge.io.hal.modberry.ModBerryX500CM4;
import io.openems.edge.io.hal.modberry.ModberryX500CM4Hardware;
import io.openems.edge.io.hal.raspberrypi.RaspberryPiInterface;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "io.openems.edge.airconditioner.hydac4kw", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		property = { //
				EventConstants.EVENT_TOPIC + "=" + EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE //
		} //
)
public class HydacAirConditionerImpl extends AbstractOpenemsComponent implements HydacAirConditioner, OpenemsComponent, EventHandler {
	
	@Reference
	RaspberryPiInterface raspberryPiProvider;
	
	private Config config;
	private ModBerryX500CM4 hardware;
	private Led onGpio;
	private DigitalOut onGpio2;
	private DigitalIn error1Gpio;
	private DigitalIn error2Gpio;
	private StartFrequency maxRestartFrequency;
	private RestartController restartController;
	
	private final AtomicReference<StartStop> startStopTarget = new AtomicReference<>(StartStop.UNDEFINED);
	
	public HydacAirConditionerImpl() {
		super(OpenemsComponent.ChannelId.values(),
				HydacAirConditioner.ChannelId.values(),
				StartStoppable.ChannelId.values(),
				RateLimitedStartStoppable.ChannelId.values());
	}


	@Activate
	void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.hardware = raspberryPiProvider.getHardwareAs(ModBerryX500CM4.class);
		this.onGpio = this.hardware.getLed(ModberryX500CM4Hardware.Led.LED_1);
		this.onGpio2 = this.hardware.getDigitalOut(ModberryX500CM4Hardware.DigitalOut.DOUT_2);
		this.error1Gpio = this.hardware.getDigitalIn(ModberryX500CM4Hardware.OptoDigitalIn.DIN_1);
		this.error2Gpio = this.hardware.getDigitalIn(ModberryX500CM4Hardware.OptoDigitalIn.DIN_2);
		this.config = config;
		
		this.maxRestartFrequency = StartFrequency //
				.builder() //
				.withOccurence(config.getMaxRestartPerHour()) //
				.withDuration(Duration.ofMinutes(1)).build();
		
		this.restartController = new RestartController( //
				this.maxRestartFrequency, //
				() -> {
					this.onGpio.on();
					this.onGpio2.setOn();
				},
				() -> {
					this.onGpio.off();
					this.onGpio2.setOff();
				}
		);
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		if (event.getTopic().equals(EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE)) {
			this.reportError();
			this.updateStartCounter();
			this.updateStartStop();
		}
	}
	
	private void updateStartStop() {
		var nextVal = this.getStartStopTarget();
		this.channel(StartStoppable.ChannelId.START_STOP).setNextValue(nextVal);
		this.setStartStopTarget(nextVal);
		
	}


	private void updateStartCounter() {
		this.channel(RateLimitedStartStoppable.ChannelId.REMAINING_START_COUNT).setNextValue(restartController.remainingStartsAvaliable());
	}
	
	private void reportError() {
		this.channel(HydacAirConditioner.ChannelId.START_EXCEEDED).setNextValue(restartController.remainingStartsAvaliable() <= 0);
		this.channel(HydacAirConditioner.ChannelId.ERROR_1).setNextValue(error1Gpio.isOn() ? ErrorSignal.STATE_NORMAL : ErrorSignal.STATE_ALARM);
		this.channel(HydacAirConditioner.ChannelId.ERROR_2).setNextValue(error2Gpio.isOn() ? ErrorSignal.STATE_NORMAL : ErrorSignal.STATE_ALARM);
	}

	@Override
	public String debugLog() {
		return String.format("[Air conditioner Hydac 4kw] State: %s, Error1: %s, Error2: %s, RemainingStarts: %s", this.onGpio.isOn(), this.error1Gpio.isOn(), this.error1Gpio.isOn(), this.getRemainingStarts());
	}

	@Override
	public void setStartStop(StartStop value) throws OpenemsNamedException {
		if (this.startStopTarget.getAndSet(value) != value) {
			// Set only if value changed
			switch (value) {
				case START:
					restartController.requestStart(); 
					break;
				case STOP:
					restartController.requestStop();
					break;
			default:
				restartController.requestStop();
				break;
			}
		}
	}
	
	public StartStop getStartStopTarget() {
		switch (this.config.startStopConfig()) {
		case AUTO:
			// read StartStop-Channel
			return this.startStopTarget.get();

		case START:
			// force START
			return StartStop.START;

		case STOP:
			// force STOP
			return StartStop.STOP;
		}

		assert false;
		return StartStop.UNDEFINED; // can never happen
	}
	
	public void setStartStopTarget(StartStop newTarget) {
		try {
			this.setStartStop(newTarget);
		} catch (OpenemsNamedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public int getRemainingStarts() {
		return restartController.remainingStartsAvaliable();
	}

	@Override
	public StartFrequency getMaxStartFrequency() {
		return this.maxRestartFrequency;
	}
}
