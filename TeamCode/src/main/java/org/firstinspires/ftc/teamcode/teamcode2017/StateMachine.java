package org.firstinspires.ftc.teamcode.teamcode2017;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.teamcode.game.robot.PathSeg;
import org.firstinspires.ftc.teamcode.game.robot.StartPosition;
import org.firstinspires.ftc.teamcode.game.robot.TeamColor;
import org.lasarobotics.vision.android.Cameras;
import org.lasarobotics.vision.ftc.resq.Beacon;
import org.lasarobotics.vision.opmode.LinearVisionOpMode;
import org.lasarobotics.vision.util.ScreenOrientation;
import org.opencv.core.Mat;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Created by 18mjiang on 9/18/17.
 */


@Autonomous(name="StateMachine", group="auto")  // @Autonomous(...) is the other common choice
public class StateMachine extends LinearVisionOpMode {
    /***
     * VISION VARIABLES
     */

    int frameCount = 0;

    private Cameras BEACON_CAMERA = Cameras.PRIMARY;
    private Beacon.AnalysisMethod BEACON_ANALYSIS_METHOD = Beacon.AnalysisMethod.REALTIME;
    private ScreenOrientation SCREEN_ORIENTATION = ScreenOrientation.LANDSCAPE;

    /***
     * GAME VARIABLES
     */
    private Robot2017 robot;
    private ElapsedTime runtime = new ElapsedTime();

    private final double PERIODIC_INTERVAL = 100; //in milliseconds
    private double nextPeriodicTime;

    //test at tournament in field, configure variables
    static final double EDGE_LINE_THRESHOLD = 0.45; //higher = more light reflected, whiter color
    static final double MID_LINE_THRESHOLD = 0.91;

    static final double DISTANCE_BEACON_THRESHOLD = 35;
    static final double HIT_BEACON_THRESHOLD = 8;

    static final double BEACON_ANALYSIS_CONFIDENCE = 0.75;

    private boolean debugOn = true;

    public int beaconCount = 1;

    /***
     * STATE MACHINE + EVENTS
     */
    //consider as stacks
    Deque<STATE> states = new ArrayDeque();
    Deque<EVENT> events = new ArrayDeque();

    private enum STATE {
        IDLE,
        LAUNCH,
        BEGIN,
        FOLLOW_LINE,
        START_PATH,
        MOVE_PATH,
        STOP_PATH,
        ANALYZE_BEACON,
        LAUNCH_PARTICLE,
        OPEN_LOADING,
        CLOSE_LOADING,
        PARK;
    }

    private enum EVENT {
        LINE_DETECTED;
    }

    public void runOpMode() throws InterruptedException {
        initSB();

        if (debugOn) {
            //while (true) { telemetry.addData("Debug:", "init good"); }
        }

        //Wait for the match to begin, presses start button
        waitForStart();

        startSB();

        if (debugOn) {
            //while (true) { telemetry.addData("Debug:", "start good"); }
        }

        nextPeriodicTime = runtime.milliseconds();
        //Main loop
        //Camera frames and OpenCV analysis will be delivered to this method as quickly as possible
        //This loop will exit once the opmode is closed
        while (opModeIsActive()) {
            checkLatestFrame();

            if (runtime.milliseconds() >= nextPeriodicTime) {
                nextPeriodicTime = runtime.milliseconds() + PERIODIC_INTERVAL;
                periodicLoop();
            }
            continuousLoop();

            //Wait for a hardware cycle to allow other processes to run
            waitOneFullHardwareCycle();
        }

        stopSB();
    }

    private void inputGameConfig() throws InterruptedException{
        telemetry.addData("Input team color", "Red (press b) or Blue (press x)");
        telemetry.update();
        while (gamepad1.b != true && gamepad1.x != true) {
            sleep(10);
        }

        if (gamepad1.b == true) {
            robot.teamColor = TeamColor.red;
        } else {
            robot.teamColor = TeamColor.blue;
        }
        telemetry.addData("Chosen team color", robot.teamColor);
        telemetry.update();
        sleep(1000);
    }

    private void initSB() throws InterruptedException {

        robot = new Robot2017(TeamColor.blue, StartPosition.left);
        robot.init(hardwareMap);
        robot.setTelemetry(telemetry);

        //inputGameConfig();
    }

    private void startSB() {
        states.push(STATE.LAUNCH);
        //states.push(STATE.BEGIN);

        runtime.reset();
    }

    /**
     * A periodic loop that runs every PERIODIC_INTERAL milliseconds
     * Priority is higher than continuousLoop();
     */
    private void periodicLoop() throws InterruptedException {
        EVENT currEvent = events.peekLast();
        if (currEvent != null) {
            eventHandler(currEvent);
        }
    }

    private void eventHandler(EVENT currentEvent) {
        switch (currentEvent) {
            case LINE_DETECTED: {
                if (lineDetected()) {
                    events.pop(); //remove, event occured

                    states.pop(); //should pop the move_path_state used to get to line
                    states.push(STATE.FOLLOW_LINE);
                    states.push(STATE.STOP_PATH); //this state executes first b/c stack
                }
            }
        }
    }

    private boolean lineDetected() {
        telemetry.addData("waiting for", "line");
        telemetry.addData("Light", robot.ods.getLightDetected());
        telemetry.update();
        return robot.ods.getLightDetected() > EDGE_LINE_THRESHOLD;
    }

    /**
     * A continuous loop that runs as fast as the system allows
     * Priority is lower than periodicLoop();
     */
    private void continuousLoop() throws InterruptedException {
        telemetry.clearAll();

        logVisionData();

        telemetry.addData("Events waiting for:", Arrays.toString(events.toArray()));
        telemetry.addData("States in stack:", Arrays.toString(states.toArray()));
        telemetry.update();

        STATE currState = states.peekLast();

        if (currState != null) {
            telemetry.addData("curr state", currState);
            telemetry.update();
            sleep(700);
            stateMachine(currState);
        }
    }

    /**
     *
     * @param currentState
     * states.pop() removes the current state from the top of the stack.
     */
    public void stateMachine(STATE currentState) throws InterruptedException {
        switch (currentState) {
            case IDLE: {
                break;
            }

            case LAUNCH: {
                states.pop();
                states.push(STATE.BEGIN);
            }

            case BEGIN: {
                states.pop();

                //go towards beacon

                robot.drive.queuePath(new PathSeg(14 * 0.95, -14 * 0.95, 0.35, runtime, 10000));
                robot.drive.startPath();

                while (!robot.drive.pathDone() && opModeIsActive()) {
                    sleep(10);
                }
                robot.drive.stopCurrPath();

                sleep(10);

                robot.drive.queuePath(new PathSeg(12 * 5.7, 12 * 5.7, 0.5, runtime, 15000));
                robot.drive.startPath();

                while (!robot.drive.pathDone() && opModeIsActive()) {
                    sleep(10);
                }
                robot.drive.stopCurrPath();

                /*

                while (!lineDetected() && opModeIsActive()){
                    robot.drive.powerDrive(0.2, -0.2);
                }
                robot.drive.powerDrive(0, 0);

                telemetry.addData("line", "found");
                telemetry.update();

                states.push(STATE.FOLLOW_LINE);

                */

                break;
            }

            case START_PATH: {
                states.pop();

                robot.drive.startPath();

                states.push(STATE.MOVE_PATH);

                break;
            }

            case MOVE_PATH: {
                boolean pathDone = robot.drive.pathDone();
                if (pathDone) { //path has ended
                    states.pop();
                    states.push(STATE.STOP_PATH);
                }

                break;
            }

            case STOP_PATH: {
                states.pop();

                robot.drive.stopCurrPath();

                break;
            }

            /**
             * https://ftc-tricks.com/proportional-line-follower/
             */
            case FOLLOW_LINE: {
                states.pop();

                if (beaconCount == 2) {
                    if (robot.teamColor.equals(TeamColor.blue)) {
                        robot.drive.queuePath(new PathSeg(14, -14, 0.45, runtime, 10000));
                        robot.drive.startPath();

                        while (!robot.drive.pathDone() && opModeIsActive()) {
                            sleep(10);
                        }
                        robot.drive.stopCurrPath();

                        telemetry.addData("rotate", "done");
                        telemetry.update();
                        sleep(1000);
                    } else {
                        //       beaconRotLeft();
                    }
                }

                followLine();

                states.push(STATE.ANALYZE_BEACON);

                break;
            }

            case ANALYZE_BEACON: {
                states.pop();

                beaconCount++;
                telemetry.addData("beaconcount", beaconCount);
                telemetry.update();
                sleep(500);

                if (beaconCount == 2) {
                    if (robot.teamColor.equals(TeamColor.blue)) {
                        robot.drive.queuePath(new PathSeg(-14 * 0.85, 14 * 0.85, 0.4, runtime, 10000));
                        robot.drive.startPath();

                        while (!robot.drive.pathDone() && opModeIsActive()) {
                            sleep(10);
                        }
                        robot.drive.stopCurrPath();

                        telemetry.addData("rotate", "done");
                        telemetry.update();
                        sleep(1000);
                    } else {
                        //       beaconRotRight();
                    }
                    robot.drive.queuePath(new PathSeg(12 * 1.8, 12 * 1.8, 0.4, runtime, 10000.0));
                    events.push(EVENT.LINE_DETECTED);
                    states.push(STATE.START_PATH);

                } else {
                    states.push(STATE.PARK);
                }

                break;
            }

            case PARK: {
                if (robot.teamColor.equals(TeamColor.blue)) {
                    robot.drive.queuePath(new PathSeg(-14 * 0.1, 14 * 0.1, 0.35, runtime, 10000));
                    robot.drive.startPath();

                    while (!robot.drive.pathDone() && opModeIsActive()) {
                        sleep(10);
                    }
                    robot.drive.stopCurrPath();

                    telemetry.addData("rotate", "done");
                    telemetry.update();
                    sleep(1000);

                } else {
                    // parkRotRed();
                }
                robot.drive.queuePath(new PathSeg(-48, -48, 0.4, runtime, 10000.0));
                states.push(STATE.START_PATH);
            }

        }
    }
    private boolean inFrontOfBeacon() throws InterruptedException {
        telemetry.addData("ultrasonic status", robot.ultrasonic.getUltrasonicLevel());
        telemetry.update();
        sleep(500);
        return robot.ultrasonic.getUltrasonicLevel() <= DISTANCE_BEACON_THRESHOLD;
    }

    private void followLine() throws InterruptedException {
        telemetry.addData("State", "follow line");
        telemetry.update();
        sleep(500);

        double leftPower = 0;
        double rightPower = 0;

        while (!inFrontOfBeacon() && opModeIsActive()) {
            double correction = (MID_LINE_THRESHOLD - robot.ods.getLightDetected()) / 5;
            if (correction <= 0) {
                leftPower = 0.075d - correction;
                rightPower = 0.075d;
            } else {
                leftPower = 0.075d;
                rightPower = 0.075d + correction;
            }

            robot.drive.powerDrive(leftPower, rightPower);
        }
        robot.drive.powerDrive(0,0);

        telemetry.addData("State", "follow line DONE");
        telemetry.update();
        sleep(500);
    }

    private void pressBeacon() throws InterruptedException {
        telemetry.addData("State", "press beacon");
        telemetry.update();
        sleep(500);

        //move to beacon
        double avgHit = robot.ultrasonic.getUltrasonicLevel();

        while (opModeIsActive() && (HIT_BEACON_THRESHOLD <= avgHit) ) {
            avgHit = (avgHit + robot.ultrasonic.getUltrasonicLevel()) / 2;
            robot.drive.powerDrive(0.3, 0.3);
            telemetry.addData("curr distance", robot.ultrasonic.getUltrasonicLevel());
            telemetry.addData("desired distance", HIT_BEACON_THRESHOLD);
            telemetry.addData("avg distance", avgHit);
            telemetry.update();
            sleep(100);
        }
        robot.drive.stop();

        telemetry.addData("Beacon", "pressed");
        telemetry.update();
        sleep(500);

        double avgBack = robot.ultrasonic.getUltrasonicLevel();
        //back up from beacon
        while (opModeIsActive() && (DISTANCE_BEACON_THRESHOLD >= avgBack) ) {
            avgBack = (avgBack + robot.ultrasonic.getUltrasonicLevel()) / 2;
            robot.drive.powerDrive(-0.3, -0.3);
            telemetry.addData("curr distance", robot.ultrasonic.getUltrasonicLevel());
            telemetry.addData("desired distance", DISTANCE_BEACON_THRESHOLD);
            telemetry.addData("avg distance", avgBack);
            telemetry.update();
            sleep(100);
        }

        robot.drive.stop();

        telemetry.addData("backup", "done");
        telemetry.update();
        sleep(500);
    }

    private void checkLatestFrame() {
        //You can access the most recent frame data and modify it here using getFrameRgba() or getFrameGray()
        //Vision will run asynchronously (parallel) to any user code so your programs won't hang
        //You can use hasNewFrame() to test whether vision processed a new frame
        //Once you copy the frame, discard it immediately with discardFrame()
        if (hasNewFrame()) {
            //Get the frame
            Mat rgba = getFrameRgba();
            Mat gray = getFrameGray();

            //Discard the current frame to allow for the next one to render
            discardFrame();

            //Do all of your custom frame processing here
            //For this demo, let's just add to a frame counter
            frameCount++;
        }
    }

    private void logVisionData() {
        telemetry.addData("Beacon Color", beacon.getAnalysis().getColorString());
        telemetry.addData("Beacon Center", beacon.getAnalysis().getLocationString());
        telemetry.addData("Beacon Confidence", beacon.getAnalysis().getConfidenceString());
        telemetry.addData("Beacon Buttons", beacon.getAnalysis().getButtonString());
        telemetry.addData("Screen Rotation", rotation.getScreenOrientationActual());
        telemetry.addData("Frame Rate", fps.getFPSString() + " FPS");
        telemetry.addData("Frame Size", "Width: " + width + " Height: " + height);
        telemetry.addData("Frame Counter", frameCount);
    }

    private void stopSB() {

    }
}

