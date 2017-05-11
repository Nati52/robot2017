package org.usfirst.frc.team449.robot.drive.talonCluster;

import com.ctre.CANTalon;
import com.kauailabs.navx.frc.AHRS;
import edu.wpi.first.wpilibj.DoubleSolenoid;
import edu.wpi.first.wpilibj.SPI;
import edu.wpi.first.wpilibj.command.Command;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import maps.org.usfirst.frc.team449.robot.components.RotPerSecCANTalonSRXMap;
import maps.org.usfirst.frc.team449.robot.components.ToleranceBufferAnglePIDMap;
import org.usfirst.frc.team449.robot.components.NavxSubsystem;
import org.usfirst.frc.team449.robot.components.RotPerSecCANTalonSRX;
import org.usfirst.frc.team449.robot.drive.DriveSubsystem;
import org.usfirst.frc.team449.robot.oi.OI2017ArcadeGamepad;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;


/**
 * A drive with a cluster of any number of CANTalonSRX controlled motors on each side.
 */
public class TalonClusterDrive extends DriveSubsystem implements NavxSubsystem {

	/**
	 * Joystick scaling constant. Joystick output is scaled by this before being handed to the PID loop to give the
	 * loop space to compensate.
	 */
	private final double PID_SCALE = 0.9;
	/**
	 * Right master Talon
	 */
	public RotPerSecCANTalonSRX rightMaster;
	/**
	 * Left master Talon
	 */
	public RotPerSecCANTalonSRX leftMaster;
	/**
	 * The NavX gyro
	 */
	public AHRS navx;
	/**
	 * The PIDAngleCommand constants for turning to an angle with the NavX
	 */
	public ToleranceBufferAnglePIDMap.ToleranceBufferAnglePID turnPID;
	/**
	 * The PIDAngleCommand constants for using the NavX to drive straight
	 */
	public ToleranceBufferAnglePIDMap.ToleranceBufferAnglePID straightPID;
	/**
	 * The oi used to drive the robot
	 */
	public OI2017ArcadeGamepad oi;

	// TODO take these out after testing
	/**
	 * The solenoid that shifts between gears
	 */
	public DoubleSolenoid shifter;
	/**
	 * Current status of the left side MP
	 */
	public CANTalon.MotionProfileStatus leftTPointStatus;
	/**
	 * Current status of the right side MP
	 */
	public CANTalon.MotionProfileStatus rightTPointStatus;
	/**
	 * Whether or not to use the NavX for driving straight
	 */
	public boolean overrideNavX;
	/**
	 * Whether not to override auto shifting
	 */
	public boolean overrideAutoShift;
	/**
	 * The time (milliseconds) when the robot was enabled (for use in logging)
	 */
	private long startTime;
	/**
	 * The name of the file to log to
	 */
	private String logFN;

	// TODO externalize
	private String errorFN;
	/**
	 * Measured max speed of robot reached in a run. Used for testing and tuning. NOT max_speed tuning constant
	 *
	 * @deprecated
	 */
	@Deprecated
	private double maxSpeed;
	/**
	 * Upshift timeout.
	 * The robot will wait for this this number of milliseconds before upshifting after a downshift
	 */
	private double upTimeThresh;
	/**
	 * Downshift timeout.
	 * The robot will wait for this this number of milliseconds before downshifting after an upshift
	 */
	private double downTimeThresh;
	/**
	 * Whether we can up shift, used as a flag for the delay
	 */
	private boolean okToUpshift;

	// TODO refactor; THIS IS NOT A DEADBAND
	/**
	 * Whether we can down shift, used as a flag for the delay
	 */
	private boolean okToDownshift;
	/**
	 * The setpoint (on a 0-1 scale) below which we stay in low gear
	 */
	private double upshiftFwdDeadband;
	/**
	 * The last time (in milliseconds) at which we upshifted
	 */
	private long timeAboveShift;
	/**
	 * The last time (in milliseconds) at which we downshifted
	 */
	private long timeBelowShift;

	// TODO simplify shifting and make this a primitive
	/**
	 * The time we last shifted (milliseconds)
	 */
	private long timeLastShifted;
	/**
	 * The minimum time between shifting in either direction
	 */
	private Double shiftDelay;

	// TODO set this when we shift
	// TODO make this an enum instead of a boolean
	/**
	 * Whether we're in low gear
	 */
	private boolean lowGear = true;    //we want to start in low gear

	/**
	 * The speed setpoint at the upshift break
	 */
	private double upshift;

	/**
	 * The speed setpoint at the downshift break
	 */
	private double downshift;

	/**
	 * Construct a TalonClusterDrive
	 *
	 * @param map config map
	 * @param oi  OI to read throttle from
	 */
	public TalonClusterDrive(maps.org.usfirst.frc.team449.robot.drive.talonCluster.TalonClusterDriveMap
			                         .TalonClusterDrive map, OI2017ArcadeGamepad oi) {
		super(map.getDrive());

		// Write the headers for the logfile.
		// TODO externalize this shit
		String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
		logFN = "/home/lvuser/logs/drivePowerLog-" + timeStamp + ".csv";
		errorFN = "/home/lvuser/logs/driveErrorLog-" + timeStamp + ".csv";
		try (PrintWriter writer = new PrintWriter(logFN)) {
			writer.println("time,left vel,right vel,left setpoint,right setpoint,left current,right current,left voltage,right voltage");
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		try (PrintWriter writer = new PrintWriter(errorFN)) {
			writer.println("time,message");
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		this.map = map;
		this.oi = oi;
		navx = new AHRS(SPI.Port.kMXP);
		turnPID = map.getTurnPID();
		straightPID = map.getStraightPID();
		upTimeThresh = map.getUpTimeThresh();
		downTimeThresh = map.getDownTimeThresh();
		upshiftFwdDeadband = map.getUpshiftFwdDeadband();
		upshift = map.getUpshift();
		downshift = map.getDownshift();

		// Shift delay is optional, so it'll be null if it isn't in map.
		if (map.hasShiftDelay()) {
			this.shiftDelay = map.getShiftDelay();
		}

		// Initialize shifting constants, assuming robot is stationary.
		okToUpshift = false;
		okToDownshift = true;
		overrideAutoShift = false;
		timeLastShifted = 0;

		// If the map has the shifting piston, instantiate it.
		if (map.hasShifter()) {
			this.shifter = new DoubleSolenoid(map.getModuleNumber(), map.getShifter().getForward(), map.getShifter()
					.getReverse());
		}

		// Initialize max
		maxSpeed = -1;

		// Initialize master talons
		rightMaster = new RotPerSecCANTalonSRX(map.getRightMaster());
		leftMaster = new RotPerSecCANTalonSRX(map.getLeftMaster());

		// Initialize slave talons.
		for (RotPerSecCANTalonSRXMap.RotPerSecCANTalonSRX talon : map.getRightSlaveList()) {
			RotPerSecCANTalonSRX talonObject = new RotPerSecCANTalonSRX(talon);
			talonObject.canTalon.changeControlMode(CANTalon.TalonControlMode.Follower);
			talonObject.canTalon.set(map.getRightMaster().getPort());
		}
		for (RotPerSecCANTalonSRXMap.RotPerSecCANTalonSRX talon : map.getLeftSlaveList()) {
			RotPerSecCANTalonSRX talonObject = new RotPerSecCANTalonSRX(talon);
			talonObject.canTalon.changeControlMode(CANTalon.TalonControlMode.Follower);
			talonObject.canTalon.set(map.getLeftMaster().getPort());
		}

		//Weird MP shit
		leftTPointStatus = new CANTalon.MotionProfileStatus();
		rightTPointStatus = new CANTalon.MotionProfileStatus();
	}

	/**
	 * Simple helper function for clipping output to the -1 to 1 scale.
	 *
	 * @param in The number to be processed.
	 * @return That number, clipped to 1 if it's greater than 1 or clipped to -1 if it's less than -1.
	 */
	private static double clipToOne(double in) {
		return Math.min(Math.max(in, -1), 1);
	}

	// TODO refactor to specifiy PID VELOCITY setpoint

	/**
	 * Sets the left and right wheel speeds as a percent of max voltage, not nearly as precise as PID.
	 *
	 * @param left  The left voltage throttle, [-1, 1]
	 * @param right The right voltage throttle, [-1, 1]
	 */
	public void setVBusThrottle(double left, double right) {
		//Set voltage mode throttles
		leftMaster.setPercentVbus(left);
		rightMaster.setPercentVbus(-right); //This is negative so PID doesn't have to be. Future people, if your robot goes in circles in voltage mode, this may be why.
	}

	/**
	 * Sets left and right wheel PID velocity setpoint as a percent of max setpoint
	 *
	 * @param left  The left PID velocity setpoint as a percent [-1, 1]
	 * @param right The right PID velocity setpoint as a percent [-1, 1]
	 */
	private void setPIDThrottle(double left, double right) {
		//If we're not shifting, scale by the max speed in the current gear
		if (overrideAutoShift) {
			leftMaster.setSpeed(PID_SCALE * (left * leftMaster.getMaxSpeed()));
			rightMaster.setSpeed(PID_SCALE * (right * rightMaster.getMaxSpeed()));
		}
		//If we are shifting, scale by the high gear max speed to make acceleration smoother and faster.
		else {
			leftMaster.setSpeed(PID_SCALE * (left * leftMaster.getMaxSpeedHG()));
			rightMaster.setSpeed(PID_SCALE * (right * rightMaster.getMaxSpeedHG()));
		}
	}

	/**
	 * setPIDThrottle or setVBusThrottle, whichever is set as the "default"
	 *
	 * @param left  Left throttle value
	 * @param right Right throttle value
	 */
	public void setDefaultThrottle(double left, double right) {
		//Clip to one to avoid anything strange.
		setPIDThrottle(clipToOne(left), clipToOne(right));
//		setVBusThrottle(left, right);
	}

	public void logPower() {
		//time,left vel,right vel,left setpoint,right setpoint,left current,right current,left voltage,right voltage
		/*
		try (FileWriter fw = new FileWriter(logFN, true)) {
			StringBuilder sb = new StringBuilder();
			sb.append((double)(System.currentTimeMillis() - startTime) / 1000.);
			sb.append(",");
			sb.append(leftMaster.getSpeed());
			sb.append(",");
			sb.append(rightMaster.getSpeed());
			sb.append(",");
			sb.append(leftMaster.getSetpoint());
			sb.append(",");
			sb.append(rightMaster.getSetpoint());
			sb.append(",");
			sb.append(leftMaster.canTalon.getOutputCurrent());
			sb.append(",");
			sb.append(rightMaster.canTalon.getOutputCurrent());
			sb.append(",");
			sb.append(leftMaster.canTalon.getOutputVoltage());
			sb.append(",");
			sb.append(rightMaster.canTalon.getOutputVoltage());
			sb.append("\n");
			fw.write(sb.toString());
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}*/
		SmartDashboard.putNumber("Left Speed", leftMaster.getSpeed());
		SmartDashboard.putNumber("Left Setpoint", leftMaster.getSetpoint());
		SmartDashboard.putNumber("Left Current", leftMaster.canTalon.getOutputCurrent());
		SmartDashboard.putNumber("Left Voltage", leftMaster.canTalon.getOutputVoltage());
		SmartDashboard.putNumber("Right Speed", rightMaster.getSpeed());
		SmartDashboard.putNumber("Right Setpoint", rightMaster.getSetpoint());
		SmartDashboard.putNumber("Right Current", rightMaster.canTalon.getOutputCurrent());
		SmartDashboard.putNumber("Right Voltage", rightMaster.canTalon.getOutputVoltage());
	}

	public void logError(String error) {
		try (FileWriter fw = new FileWriter(errorFN, true)) {
			fw.write(((double) (System.currentTimeMillis() - startTime) / 1000.) + "," + error + "\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Log stuff to file
	 */
	public void logData() {
		logPower();
		/*
		//Print stuff to the log file for in-depth analysis or tuning.
		try (FileWriter fw = new FileWriter(logFN, true)) {
			StringBuilder sb = new StringBuilder();
			sb.append((double)(System.currentTimeMillis() - startTime) / 1000.);
			sb.append(",");
			sb.append(leftMaster.getSpeed());
			sb.append(",");
			sb.append(rightMaster.getSpeed());
			sb.append(",");
			sb.append(leftMaster.getError());
			sb.append(",");
			sb.append(rightMaster.getError());
			sb.append(",");
			sb.append(leftMaster.canTalon.getOutputCurrent());
			sb.append(",");
			sb.append(rightMaster.canTalon.getOutputCurrent());
			sb.append(",");
			sb.append(leftMaster.canTalon.getOutputVoltage());
			sb.append(",");
			sb.append(rightMaster.canTalon.getOutputVoltage());
			 /*
			 sb.append(",");
	         sb.append(leftTPointStatus.activePoint.position);
	         sb.append(",");
	         sb.append(rightTPointStatus.activePoint.position);
	         */ /*
			sb.append("\n");

			fw.write(sb.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
		//Log to SmartDashboard for quick viewing
		maxSpeed = Math.max(maxSpeed, Math.max(Math.abs(leftMaster.getSpeed()), Math.abs(rightMaster.getSpeed())));
		SmartDashboard.putNumber("Max Speed", maxSpeed);
		SmartDashboard.putNumber("Left", leftMaster.getSpeed());
		SmartDashboard.putNumber("Right", rightMaster.getSpeed());
		SmartDashboard.putNumber("Throttle", leftMaster.nativeToRPS(leftMaster.canTalon.getSetpoint()));
		SmartDashboard.putNumber("Heading", getGyroOutput());
		SmartDashboard.putNumber("Left Setpoint", leftMaster.nativeToRPS(leftMaster.canTalon.getSetpoint()));
		SmartDashboard.putNumber("Left Error", leftMaster.nativeToRPS(leftMaster.canTalon.getError()));
		SmartDashboard.putNumber("Right Setpoint", rightMaster.nativeToRPS(rightMaster.canTalon.getSetpoint()));
		SmartDashboard.putNumber("Right Error", rightMaster.nativeToRPS(rightMaster.canTalon.getError()));
		SmartDashboard.putNumber("Left F", leftMaster.canTalon.getF());
		SmartDashboard.putNumber("Right F", rightMaster.canTalon.getF());
		SmartDashboard.putNumber("Left P", leftMaster.canTalon.getP());
		SmartDashboard.putNumber("Right P", rightMaster.canTalon.getP());
		SmartDashboard.putBoolean("In low gear?", lowGear);
		SmartDashboard.putNumber("Right pos", MPLoader.nativeToFeet(rightMaster.canTalon.getEncPosition(), rightMaster.encoderCPR, Robot.WHEEL_DIAMETER));
		SmartDashboard.putNumber("Left pos", MPLoader.nativeToFeet(leftMaster.canTalon.getEncPosition(), leftMaster.encoderCPR, Robot.WHEEL_DIAMETER));
		*/
	}

	/**
	 * Log stuff to file, with a given velocity setpoint to log
	 *
	 * @param sp velocity setpoint
	 */
	public void logData(double sp) {
		logPower();
		/*
		//Print stuff to the log file for in-depth analysis or tuning.
		try (FileWriter fw = new FileWriter(logFN, true)) {
			StringBuilder sb = new StringBuilder();
			sb.append((double)(System.currentTimeMillis() - startTime) / 1000.);
			sb.append(",");
			sb.append(leftMaster.getSpeed());
			sb.append(",");
			sb.append(rightMaster.getSpeed());
			sb.append(",");
			sb.append(leftMaster.getError());
			sb.append(",");
			sb.append(rightMaster.getError());
			sb.append(",");
			sb.append(sp);
			sb.append(",");
			sb.append(sp);
			sb.append("\n");

			fw.write(sb.toString());

			SmartDashboard.putNumber("Left", leftMaster.getSpeed());
			SmartDashboard.putNumber("Right", rightMaster.getSpeed());
			SmartDashboard.putNumber("Left Pos inches", leftMaster.nativeToRPS(leftMaster.canTalon.getEncPosition()) /
					10 * Math.PI * 4);
			SmartDashboard.putNumber("Right Pos inches", rightMaster.nativeToRPS(rightMaster.canTalon.getEncPosition()
			) / 10 * Math.PI * 4);
			SmartDashboard.putNumber("Right Pos", rightMaster.canTalon.getEncPosition());
			SmartDashboard.putNumber("Left Pos", leftMaster.canTalon.getEncPosition());
			SmartDashboard.putNumber("Throttle", leftMaster.nativeToRPS(leftMaster.canTalon.getSetpoint()));
			SmartDashboard.putNumber("Heading", getGyroOutput());
			SmartDashboard.putNumber("Left Setpoint", leftMaster.nativeToRPS(leftMaster.canTalon.getSetpoint()));
			SmartDashboard.putNumber("Left Error", leftMaster.nativeToRPS(leftMaster.canTalon.getError()));
			SmartDashboard.putNumber("Right Setpoint", rightMaster.nativeToRPS(rightMaster.canTalon.getSetpoint()));
			SmartDashboard.putNumber("Right Error", rightMaster.nativeToRPS(rightMaster.canTalon.getError()));
			sb.append(PID_SCALE * sp * leftMaster.getMaxSpeed());
			sb.append(",");
			sb.append(PID_SCALE * sp * rightMaster.getMaxSpeed());
			 /*
			 sb.append(",");
	         sb.append(leftTPointStatus.activePoint.position);
	         sb.append(",");
	         sb.append(rightTPointStatus.activePoint.position);
	         */ /*
			sb.append("\n");

			fw.write(sb.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
		//Log to SmartDashboard for quick viewing
		maxSpeed = Math.max(maxSpeed, Math.max(Math.abs(leftMaster.getSpeed()), Math.abs(rightMaster.getSpeed())));
		SmartDashboard.putNumber("Max Speed", maxSpeed);
		SmartDashboard.putNumber("Left", leftMaster.getSpeed());
		SmartDashboard.putNumber("Right", rightMaster.getSpeed());
		SmartDashboard.putNumber("Throttle", leftMaster.nativeToRPS(leftMaster.canTalon.getSetpoint()));
		SmartDashboard.putNumber("Heading", navx.pidGet());
		SmartDashboard.putNumber("Left Setpoint", leftMaster.nativeToRPS(leftMaster.canTalon.getSetpoint()));
		SmartDashboard.putNumber("Left Error", leftMaster.nativeToRPS(leftMaster.canTalon.getError()));
		SmartDashboard.putNumber("Right Setpoint", rightMaster.nativeToRPS(rightMaster.canTalon.getSetpoint()));
		SmartDashboard.putNumber("Right Error", rightMaster.nativeToRPS(rightMaster.canTalon.getError()));
		SmartDashboard.putNumber("Left F", leftMaster.canTalon.getF());
		SmartDashboard.putNumber("Right F", rightMaster.canTalon.getF());
		SmartDashboard.putNumber("Left P", leftMaster.canTalon.getP());
		SmartDashboard.putNumber("Right P", rightMaster.canTalon.getP());
		SmartDashboard.putBoolean("In low gear?", lowGear);
		*/
	}

	/**
	 * Stuff run on first enable
	 * Reset startTime, turn on navX control, and start DefaultArcadeDrive
	 */
	@Override
	protected void initDefaultCommand() {
		//Set the start time to current time
		startTime = System.currentTimeMillis();
		//Start driving
		//setDefaultCommand(new DefaultArcadeDrive(straightPID, this, oi));
	}

	public void setDefaultCommandManual(Command defaultCommand) {
		setDefaultCommand(defaultCommand);
	}

	// TODO generalize up-down with an enum

	/**
	 * Get the robot's heading using the navX
	 *
	 * @return robot heading (degrees) [-180, 180]
	 */
	public double getGyroOutput() {
		return navx.pidGet();
	}

	//TODO get rid of this because it's a reaaaaaally dumb wrapper.

	/**
	 * Shift as appropriate
	 *
	 * @param setLowGear whether to shift down (if false, shift up)
	 */
	public void setLowGear(boolean setLowGear) {
		//If we have a shifter on the robot
		if (shifter != null) {
			//If we want to downshift
			if (setLowGear) {
				//Physically shift gears
				shifter.set(DoubleSolenoid.Value.kForward);
				//Switch the PID constants
				rightMaster.switchToLowGear();
				leftMaster.switchToLowGear();
				//Set logging boolean
				lowGear = true;
			} else {
				//Physically shift gears
				shifter.set(DoubleSolenoid.Value.kReverse);
				//Switch the PID constants
				rightMaster.switchToHighGear();
				leftMaster.switchToHighGear();
				//Set logging boolean
				lowGear = false;
			}
			timeLastShifted = System.currentTimeMillis();
		} else {
			//Warn the user if they try to shift but didn't define a shifting piston.
			System.out.println("You're trying to shift gears, but your drive doesn't have a shifter.");
		}
	}

	//TODO get rid of this because it's a reaaaaaally dumb wrapper.

	/**
	 * @return left cluster measured velocity
	 */
	@Deprecated
	public double getLeftSpeed() {
		return leftMaster.getSpeed();
	}

	/**
	 * @return right cluster measured velocity
	 */
	@Deprecated
	public double getRightSpeed() {
		return rightMaster.getSpeed();
	}

	// TODO simplify the should-shift logic, especially by moving repeated code into separate functions.

	/**
	 * @return whether the robot is in low gear
	 */
	public boolean inLowGear() {
		return lowGear;
	}

	/**
	 * @return whether the robot should downshift
	 */
	public boolean shouldDownshift() {
		//We should shift if we're going slower than the downshift speed
		boolean okToShift = Math.max(Math.abs(getLeftSpeed()), Math.abs(getRightSpeed())) < downshift;
		//Or if we're just turning in place.
		okToShift = okToShift || (oi.getFwd() == 0 && oi.getRot() != 0);
		//Or commanding a low speed.
		okToShift = okToShift || (Math.abs(oi.getFwd()) < upshiftFwdDeadband);
		//But there's no need to downshift if we're already in low gear.
		okToShift = okToShift && !lowGear;
		//And we don't want to shift if autoshifting is turned off.
		okToShift = okToShift && !overrideAutoShift;

		//If we're using a both-way shift delay (must wait shiftDelay seconds after shifting EITHER WAY before shifting again)
		if (shiftDelay != null) {
			return okToShift && (System.currentTimeMillis() - timeLastShifted > shiftDelay * 1000);
		}

		//Otherwise, we use a "delay" system that waits to shift until the robot has met the conditions for downTimeThresh seconds.
		if (okToShift && !okToDownshift) {
			//This block is a "flag" that triggers when we first meet the conditions to shift.
			okToDownshift = true;
			timeBelowShift = System.currentTimeMillis();
		} else if (!okToShift && okToDownshift) {
			//This resets the flag if we no longer meet the conditions to shift.
			okToDownshift = false;
		}
		//Return if we've been eligible to shift for downTimeThresh seconds and are also currently eligible.
		return (System.currentTimeMillis() - timeBelowShift > downTimeThresh * 1000 && okToShift);
	}

	/**
	 * @return whether the robot should upshift
	 */
	public boolean shouldUpshift() {
		//We should shift if we're going faster than the upshift speed...
		boolean okToShift = Math.min(Math.abs(getLeftSpeed()), Math.abs(getRightSpeed())) > upshift;
		//AND the driver's trying to go forward fast.
		okToShift = okToShift && Math.abs(oi.getFwd()) > upshiftFwdDeadband;
		//But there's no need to upshift if we're already in high gear.
		okToShift = okToShift && lowGear;
		//And we don't want to shift if autoshifting is turned off.
		okToShift = okToShift && !overrideAutoShift;

		//If we're using a both-way shift delay (must wait shiftDelay seconds after shifting EITHER WAY before shifting again)
		if (shiftDelay != null) {
			return okToShift && (System.currentTimeMillis() - timeLastShifted > shiftDelay * 1000);
		}

		//Otherwise, we use a "delay" system that waits to shift until the robot has met the conditions for upTimeThresh seconds.
		if (okToShift && !okToUpshift) {
			//This block is a "flag" that triggers when we first meet the conditions to shift.
			okToUpshift = true;
			timeAboveShift = System.currentTimeMillis();
		} else if (!okToShift && okToUpshift) {
			//This resets the flag if we no longer meet the conditions to shift.
			okToUpshift = false;
		}
		//Return if we've been eligible to shift for upTimeThresh seconds and are also currently eligible.
		return (System.currentTimeMillis() - timeAboveShift > upTimeThresh * 1000 && okToShift);
	}

	/**
	 * Shift if necessary
	 */
	public void autoShift() {
		if (shouldUpshift()) {
			//Upshift if we should
			setLowGear(false);
		} else if (shouldDownshift()) {
			//Downshift if we should
			setLowGear(true);
		}
	}
}