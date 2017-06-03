package org.usfirst.frc.team449.robot.interfaces.subsystem.MotionProfile.commands;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import edu.wpi.first.wpilibj.command.CommandGroup;
import org.usfirst.frc.team449.robot.interfaces.subsystem.MotionProfile.MPSubsystem;
import org.usfirst.frc.team449.robot.util.MotionProfileData;

/**
 * Loads and runs the given profile into the given subsystem.
 */
@JsonIdentityInfo(generator=ObjectIdGenerators.StringIdGenerator.class)
public class RunProfile extends CommandGroup {

	/**
	 * Default constructor.
	 *
	 * @param subsystem The subsystem to execute this command on.
	 * @param profile   The motion profile to load and execute.
	 * @param timeout   The maximum amount of time this command is allowed to take, in seconds.
	 */
	public RunProfile(@JsonProperty(required = true) MPSubsystem subsystem,
	                  @JsonProperty(required = true) MotionProfileData profile,
	                  @JsonProperty(required = true) double timeout) {
		addSequential(new LoadProfile(subsystem, profile));
		addSequential(new RunLoadedProfile(subsystem, timeout, true));
	}
}
