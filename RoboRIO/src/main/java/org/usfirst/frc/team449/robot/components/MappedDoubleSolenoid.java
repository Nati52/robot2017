package org.usfirst.frc.team449.robot.components;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import edu.wpi.first.wpilibj.DoubleSolenoid;

/**
 * A wrapper on the {@link DoubleSolenoid} that can be constructed from a map object.
 */
@JsonIdentityInfo(generator=ObjectIdGenerators.StringIdGenerator.class)
public class MappedDoubleSolenoid extends DoubleSolenoid {

	/**
	 * Default constructor.
	 *
	 * @param module The module number of the PCM. Defaults to 0.
	 * @param forward The forward port on the PCM.
	 * @param reverse The reverse port on the PCM.
	 */
	@JsonCreator
	public MappedDoubleSolenoid(@JsonProperty(value = "module") int module,
	                            @JsonProperty(value = "forward", required = true) int forward,
	                            @JsonProperty(value = "reverse", required = true) int reverse) {
		super(module, forward, reverse);
	}
}
