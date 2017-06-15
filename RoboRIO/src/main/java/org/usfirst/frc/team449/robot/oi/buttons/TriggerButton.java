package org.usfirst.frc.team449.robot.oi.buttons;

import org.usfirst.frc.team449.robot.components.MappedJoystick;
import org.usfirst.frc.team449.robot.components.MappedSmoothedThrottle;
import org.usfirst.frc.team449.robot.components.MappedThrottle;

/**
 * A button that gets triggered by a specific throttle being held down at or over a certain amount.
 */
public class TriggerButton extends FactoryButton {

	/**
	 * The relevant throttle.
	 */
	private MappedThrottle throttle;

	/**
	 * The percentage pressed to trigger at, from (0, 1]
	 */
	private double triggerAt;

	/**
	 * Argument-based constructor.
	 *
	 * @param joystick  The the joystick containing the throttle.
	 * @param axis      The axis of the throttle.
	 * @param triggerAt The percentage pressed to trigger at, from (0, 1]
	 */
	TriggerButton(MappedJoystick joystick, int axis, double triggerAt) {
		throttle = new MappedSmoothedThrottle(joystick, axis, 0, false);
		this.triggerAt = triggerAt;
	}

	/**
	 * Get whether this button is pressed.
	 *
	 * @return true if the throttle's output is greater than or equal to the trigger threshold, false otherwise.
	 */
	@Override
	public boolean get() {
		return Math.abs(throttle.getValue()) >= triggerAt;
	}
}
