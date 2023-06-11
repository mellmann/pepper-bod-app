package com.storm.pepper;

import com.aldebaran.qi.Future;
import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.builder.AnimateBuilder;
import com.aldebaran.qi.sdk.builder.AnimationBuilder;
import com.aldebaran.qi.sdk.builder.GoToBuilder;
import com.aldebaran.qi.sdk.builder.HolderBuilder;
import com.aldebaran.qi.sdk.builder.TransformBuilder;
import com.aldebaran.qi.sdk.object.actuation.Animate;
import com.aldebaran.qi.sdk.object.actuation.Animation;
import com.aldebaran.qi.sdk.object.actuation.Frame;
import com.aldebaran.qi.sdk.object.actuation.FreeFrame;
import com.aldebaran.qi.sdk.object.actuation.GoTo;
import com.aldebaran.qi.sdk.object.geometry.Transform;
import com.aldebaran.qi.sdk.object.holder.AutonomousAbilitiesType;
import com.aldebaran.qi.sdk.object.holder.Holder;
import com.aldebaran.qi.sdk.util.FutureUtils;
import com.storm.posh.BaseBehaviourLibrary;
import com.storm.posh.plan.planelements.Sense;
import com.storm.posh.plan.planelements.action.ActionEvent;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class POSHBehaviourLibrary extends BaseBehaviourLibrary {
    private static final String TAG = POSHBehaviourLibrary.class.getSimpleName();

    private AnimationExecutor animExecutor = new AnimationExecutor();
    private GoToExecutor gotoExecutor = new GoToExecutor();

    protected boolean haveWavedLeft = false;
    protected boolean haveWavedRight = false;
    protected boolean turnedAround = false;
    protected boolean haveHugged = false;
    protected boolean haveHighFived = false;
    protected boolean haveShakedHands = false;
    protected boolean haveCheckedWatch = false;
    protected boolean haveSearchedGround = false;
    protected boolean haveWashedHands = false;
    protected boolean haveReset = false;
    protected boolean hasLaughed = false;
    protected boolean reachedPosition = false;

    private boolean firstGreet = false;

    private int currentDistanceLvl;

    private Date nextGreetTime;
    private Date nextRoamTime;
    private Date lastRoamTime;

    private GoTo goTo;
    private Future<Void> goToFuture;

    private boolean abilitiesHeld = false;
    // The holder for the abilities.
    private Holder holder;

    public POSHBehaviourLibrary() { setInstance(); }

    public void reset() {
        super.reset();

        haveWavedLeft = false;
        haveWavedRight = false;
        turnedAround = false;
        haveHugged = false;
        haveHighFived = false;
        haveShakedHands = false;
        haveCheckedWatch = false;
        haveSearchedGround = false;
        haveWashedHands = false;
        hasLaughed = false;
        firstGreet = false;
        abilitiesHeld = false;
        reachedPosition = false;
    }

    public boolean getBooleanSense(Sense sense) {
        boolean senseValue;

        switch (sense.getNameOfElement()) {
            case "HaveWavedLeft":
                senseValue = haveWavedLeft;
                break;
            case "HaveWavedRight":
                senseValue = haveWavedRight;
                break;
            case "TurnedAround":
                senseValue = turnedAround;
                break;
            case "HaveHugged":
                senseValue = haveHugged;
                break;
            case "HaveHighFived":
                senseValue = haveHighFived;
                break;
            case "HaveShakedHands":
                senseValue = haveShakedHands;
                break;
            case "HaveCheckedWatch":
                senseValue = haveCheckedWatch;
                break;
            case "HaveSearchedGround":
                senseValue = haveSearchedGround;
                break;
            case "haveWashedHands":
                senseValue = haveWashedHands;
                break;
            case "hasLaughed":
                senseValue = hasLaughed;
                break;
            case "ReadyToGreet":
                senseValue = getReadyToGreet();
                break;
            case "ReadyToSelfEntertain":
                senseValue = !this.humanPresent;
                break;
            case "Reset":
                senseValue = haveReset;
                break;
            case "ReachedPosition":
                senseValue = reachedPosition;
                break;
            default:
                senseValue = super.getBooleanSense(sense);
                break;
        }

        pepperLog.checkedBooleanSense(TAG, sense, senseValue);

        return senseValue;
    }

    public double getDoubleSense(Sense sense) {
        return super.getDoubleSense(sense);
    }

    public void executeAction(ActionEvent action) {
        pepperLog.appendLog(TAG, "Performing action: " + action);

        switch (action.getNameOfElement()) {
            // Actions after touch sensor has been triggered
            case "WaveLeft":
                executeAnimation("WaveLeft");
                break;
            case "WaveRight":
                executeAnimation("WaveRight");
                break;
            case "ClearWaving":
                clearWaving();
                break;
            case "TurnAround":
                executeAnimation("TurnAround");
                break;
            case "Hug":
                executeAnimation("Hug");
                break;
            case "HighFive":
                executeAnimation("HighFive");
                break;
            case "ShakeHands":
                executeAnimation("ShankeHands");
                break;
            case "CheckWatch":
                executeAnimation("CheckWatch");
                break;
            case "WashHands":
                executeAnimation("WashHands");
                break;
            case "Laugh":
                executeAnimation("Laugh");
                break;
            case "GotoSquare":
                gotoExecutor.performGotoSquare(qiContext, actuation, mapping,this, pepperLog);
                break;
            case "ApproachHuman":
                approachHuman();
                break;
            case "HoldAbilities":
                holdAbilities();
                break;
            case "ReleaseAbilities":
                releaseAbilities();
                break;
            case "Roam":
                roam();
                break;
            case "Greet":
                greet();
                break;
            case "Interrupt":
                this.turnedAround = false;
                break;
            default:
                super.executeAction(action);
                break;
        }
    }

    public boolean getReadyToGreet () {
        Date now = new Date();
        if (nextGreetTime != null && !now.before(nextGreetTime)) {
            pepperLog.appendLog(TAG, "I can greet again");
            return true;
        } else if (nextGreetTime == null) {
            pepperLog.appendLog(TAG, "Never greeted before, let me greet");
            return true;
        } else {
            return false;
        }
    }

    public void executeAnimation(String toAnimate) {
        setActive();

        if (animating) {
            pepperLog.appendLog(TAG, "Animation " + toAnimate + " IN PROGRESS");
            this.goToFuture.requestCancellation();
            return;
        }

        pepperLog.appendLog(TAG, "Animation " + toAnimate + " starting");
        setAnimating(true);
        // call animExecutor
        animExecutor.animate(toAnimate, qiContext, this);
        setAnimating(false);
        pepperLog.appendLog(TAG, "Anination " + toAnimate + " finished");
    }

    public void greet() {
        Date now = new Date();
        if (nextGreetTime != null && now.before(nextGreetTime)) {
            pepperLog.appendLog(TAG, "Don't want to greet yet");
            return;
        } else {
            setActive();
            pepperLog.appendLog(TAG, "Gonna greet!");
        }

        // set next time to hum
        int greetDelay = ThreadLocalRandom.current().nextInt(8, 15);
        pepperLog.appendLog(TAG, String.format("Next greet in %d seconds", greetDelay));
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.SECOND, greetDelay);
        nextGreetTime = calendar.getTime();

        double distance = getDistanceToHumanToGreet();
        int distanceLvl;

        if (!firstGreet) {
            firstGreet = true;
        } else {
            if (distance > 3) {
                distanceLvl = 3;
                if (distanceLvl == this.currentDistanceLvl) {
                    pepperLog.appendLog(TAG, "Distance to human has not changed, I do not need to greet.");
                    return;
                }
                this.currentDistanceLvl = 3;
                executeAnimation("Checkwatch");
                // animation: "You are far away, please get a little closer!";
            } else if (distance > 2) {
                distanceLvl = 2;
                if (distanceLvl == this.currentDistanceLvl) {
                    pepperLog.appendLog(TAG, "Distance to human has not changed, I do not need to greet.");
                    return;
                }
                this.currentDistanceLvl = 2;
                executeAnimation("WaveRight");
            } else if (distance > 1) {
                distanceLvl = 1;
                if (distanceLvl == this.currentDistanceLvl) {
                    pepperLog.appendLog(TAG, "Distance to human has not changed, I do not need to greet.");
                    return;
                }
                this.currentDistanceLvl = 1;
                executeAnimation("WaveLeft");
                // animation: "One more step and I see you perfectly.";
            } else {
                distanceLvl = 0;
                if (distanceLvl == this.currentDistanceLvl) {
                    pepperLog.appendLog(TAG, "Distance to human has not changed, I do not need to greet.");
                    return;
                }
                this.currentDistanceLvl = 0;
                executeAnimation("ShakeHands");
                // animation: "Hey there!";
            }
        }
    }

    public void roam() {
        Date now = new Date();
        if (animating) {
            long seconds_since_last_roam_started = Math.abs(now.getTime() - this.lastRoamTime.getTime()) / 1000;

            if (seconds_since_last_roam_started > 15) {
                // cancel current goto if last roam has not finished within 15 secs
                this.goToFuture.requestCancellation();
                pepperLog.appendLog(TAG, "Cancelling roaming, this took too long!");
                return;
            } else if (!this.turnedAround) {
                // cancel current goto if turning around has still not finished
                this.goToFuture.requestCancellation();
                pepperLog.appendLog(TAG, "Cancelling turnAround, this took too long!");
                return;
            } else {
                pepperLog.appendLog(TAG, String.format("Already animating, cannot roam"));
                return;
            }

        } else if (nextRoamTime != null && now.before(nextRoamTime)) {
            pepperLog.appendLog(TAG, "Don't want to roam yet");
            return;
        } else {
            pepperLog.appendLog(TAG, "Bored, gonna roam!");
        }

        // set next time to hum
        int roamDelay = ThreadLocalRandom.current().nextInt(25, 35);
        pepperLog.appendLog(TAG, String.format("Next roam in %d seconds", roamDelay));
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.SECOND, roamDelay);
        nextRoamTime = calendar.getTime();
        lastRoamTime = now;

        FutureUtils.wait(0, TimeUnit.SECONDS).andThenConsume((ignore) -> {
            setAnimating(true);

            double x = ThreadLocalRandom.current().nextDouble(0.5, 1.5);
            double y = ThreadLocalRandom.current().nextDouble(-0.5, 0.5);

            // Get the robot frame.
            Frame robotFrame = actuation.robotFrame();

            // Create a FreeFrame representing the current robot frame.
            FreeFrame locationFrame = mapping.makeFreeFrame();

            Transform transform = TransformBuilder.create().from2DTranslation(x, y);
            locationFrame.update(robotFrame, transform, 0L);

            // Create a GoTo action.
            goTo = GoToBuilder.with(qiContext)
                    .withFrame(locationFrame.frame())
                    .build();

            // Display text when the GoTo action starts.
            goTo.addOnStartedListener(() -> pepperLog.appendLog(TAG, "Roaming started"));

            // Execute the GoTo action asynchronously.
            goToFuture = goTo.async().run();

            goToFuture.thenConsume(future -> {
                if (future.isSuccess()) {
                    setAnimating(false);
                    pepperLog.appendLog(TAG, "Roaming finished with success!");
                } else if (future.hasError()) {
                    setAnimating(false);
                    pepperLog.appendLog(TAG, "Roaming has error!");
                } else if (future.isCancelled()) {
                    pepperLog.appendLog(TAG, "Roaming has been cancelled, will turn around!");
                    // If roaming is cancelled and pepper could not reach target then turn around
                    pepperLog.appendLog(TAG, "Turning around started");
                    // Create an animation object.
                    Future<Animation> myAnimationFuture = AnimationBuilder.with(qiContext)
                            .withResources(R.raw.turn_around)
                            .buildAsync();

                    myAnimationFuture.andThenConsume(myAnimation -> {
                        Animate animate = AnimateBuilder.with(qiContext)
                                .withAnimation(myAnimation)
                                .build();

                        // Run the action synchronously in this thread
                        animate.run();

                        pepperLog.appendLog(TAG, "Turning around finished");
                        setAnimating(false);
                    });
                }
            });
        });


    }

    private void holdAbilities() {
        // Build and store the holder for the abilities.
        holder = HolderBuilder.with(qiContext)
                .withAutonomousAbilities(
                        AutonomousAbilitiesType.BASIC_AWARENESS
                )
                .build();

        // Hold the abilities asynchronously.
        Future<Void> holdFuture = holder.async().hold();

        // Chain the hold with a lambda on the UI thread.
        holdFuture.andThenConsume(ignore -> {
            // Store the abilities status.
            abilitiesHeld = true;
            pepperLog.appendLog(TAG, "Holding autonomous abilities.");
        });
    }

    private void releaseAbilities() {
        // Release the holder asynchronously.
        Future<Void> releaseFuture = holder.async().release();

        releaseFuture.andThenConsume(ignore -> {
            // Store the abilities status.
            abilitiesHeld = false;
            pepperLog.appendLog(TAG, "Autonomous abilities released!");
        });
    }

    public void clearWaving() {
        setHaveWavedLeft(false);
        setHaveWavedRight(false);
        pepperLog.appendLog(TAG, "WAVING CLEARED");
    }


    // add methods for touching

    // tidy up listeners
    public void removeListeners() {
        super.removeListeners();
    }



    @Override
    public void onRobotFocusGained(QiContext qiContext) {
        super.onRobotFocusGained(qiContext);
    }

    @Override
    public void onRobotFocusLost() {
        // Remove on started listeners from the GoTo action.
        if (goTo != null) {
            goTo.removeAllOnStartedListeners();
        }

        super.onRobotFocusLost();
    }

    @Override
    public void onRobotFocusRefused(String reason) {
        // The robot focus is refused.
        super.onRobotFocusRefused(reason);
    }

    public void setHaveWavedLeft(boolean state) {
        this.haveWavedLeft = state;
    }

    public void setHaveWavedRight(boolean state) {
        this.haveWavedRight = state;
    }

    public void setReachedPosition(boolean state) { this.reachedPosition = state; }

    public void setReset(boolean state) { this.haveReset = state; }
}
