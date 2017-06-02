package org.usfirst.frc.team449.robot.mechanism.pneumatics.commands;

import org.usfirst.frc.team449.robot.ReferencingCommand;
import org.usfirst.frc.team449.robot.mechanism.pneumatics.PneumaticsSubsystem;
import org.usfirst.frc.team449.robot.util.Logger;

/**
 * Start up the pneumatic compressor.
 */
@JsonIdentityInfo(generator=ObjectIdGenerators.StringIdGenerator.class)
public class StartCompressor extends ReferencingCommand {

	private PneumaticsSubsystem subsystem;

	/**
	 * Default constructor
	 *
	 * @param subsystem The subsystem to execute this command on.
	 */
	public StartCompressor(PneumaticsSubsystem subsystem) {
		super(subsystem);
		this.subsystem = subsystem;
	}

	/**
	 * Log when this command is initialized
	 */
	@Override
	protected void initialize() {
		Logger.addEvent("StartCompressor init.", this.getClass());
	}

	/**
	 * Start the compressor.
	 */
	@Override
	protected void execute() {
		subsystem.startCompressor();
	}

	/**
	 * Finish immediately because this is a state-change command.
	 *
	 * @return true
	 */
	@Override
	protected boolean isFinished() {
		return true;
	}

	/**
	 * Log when this command ends
	 */
	@Override
	protected void end() {
		Logger.addEvent("StartCompressor end.", this.getClass());
	}

	/**
	 * Log when this command is interrupted.
	 */
	@Override
	protected void interrupted() {
		Logger.addEvent("StartCompressor Interrupted!", this.getClass());
	}
}