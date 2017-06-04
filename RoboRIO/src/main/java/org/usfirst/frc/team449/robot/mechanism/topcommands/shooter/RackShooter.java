package org.usfirst.frc.team449.robot.mechanism.topcommands.shooter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.usfirst.frc.team449.robot.util.YamlCommandGroupWrapper;
import org.usfirst.frc.team449.robot.interfaces.subsystem.Intake.IntakeSubsystem;
import org.usfirst.frc.team449.robot.interfaces.subsystem.Intake.commands.SetIntakeMode;
import org.usfirst.frc.team449.robot.interfaces.subsystem.Shooter.ShooterSubsystem;
import org.usfirst.frc.team449.robot.interfaces.subsystem.Shooter.commands.SpinUpShooter;
import org.usfirst.frc.team449.robot.interfaces.subsystem.solenoid.SolenoidSubsystem;
import org.usfirst.frc.team449.robot.interfaces.subsystem.solenoid.commands.SolenoidReverse;

/**
 * Command group for preparing the shooter to fire.
 * Starts flywheel, runs static intake, stops dynamic intake, raises intake, and stops feeder.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.StringIdGenerator.class)
public class RackShooter extends YamlCommandGroupWrapper {
	/**
	 * Constructs a RackShooter command group
	 *
	 * @param shooterSubsystem shooter subsystem
	 * @param intakeSubsystem  intake subsystem.
	 */
	@JsonCreator
	public <T extends IntakeSubsystem & SolenoidSubsystem> RackShooter(@JsonProperty(required = true) ShooterSubsystem shooterSubsystem,
	                                                                   @JsonProperty(required = true) T intakeSubsystem) {
		if (shooterSubsystem != null) {
			addParallel(new SpinUpShooter(shooterSubsystem));
		}
		if (intakeSubsystem != null) {
			addParallel(new SolenoidReverse(intakeSubsystem));
			addParallel(new SetIntakeMode(intakeSubsystem, IntakeSubsystem.IntakeMode.IN_SLOW));
		}
	}
}
