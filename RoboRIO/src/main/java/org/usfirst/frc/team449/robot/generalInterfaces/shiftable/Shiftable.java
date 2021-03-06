package org.usfirst.frc.team449.robot.generalInterfaces.shiftable;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * An interface for any object that different settings for different gears
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_OBJECT, property = "@class")
public interface Shiftable {

	/**
	 * @return The gear this subsystem is currently in.
	 */
	int getGear();

	/**
	 * Shift to a specific gear.
	 *
	 * @param gear Which gear to shift to.
	 */
	void setGear(int gear);

	enum gear {
		LOW(1), HIGH(2);

		private int numVal;

		gear(int numVal) {
			this.numVal = numVal;
		}

		public int getNumVal() {
			return numVal;
		}
	}

}
