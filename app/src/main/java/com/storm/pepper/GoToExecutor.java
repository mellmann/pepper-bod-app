package com.storm.pepper;

import com.aldebaran.qi.Future;
import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.builder.AnimateBuilder;
import com.aldebaran.qi.sdk.builder.AnimationBuilder;
import com.aldebaran.qi.sdk.builder.GoToBuilder;
import com.aldebaran.qi.sdk.builder.TransformBuilder;
import com.aldebaran.qi.sdk.object.actuation.Actuation;
import com.aldebaran.qi.sdk.object.actuation.Animate;
import com.aldebaran.qi.sdk.object.actuation.Animation;
import com.aldebaran.qi.sdk.object.actuation.Frame;
import com.aldebaran.qi.sdk.object.actuation.FreeFrame;
import com.aldebaran.qi.sdk.object.actuation.GoTo;
import com.aldebaran.qi.sdk.object.actuation.Mapping;
import com.aldebaran.qi.sdk.object.geometry.Transform;
import com.aldebaran.qi.sdk.object.geometry.Vector3;
import com.aldebaran.qi.sdk.util.FutureUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class GoToExecutor {

    private static final String TAG = GoToExecutor.class.getSimpleName();

    private ArrayList<FreeFrame> targetPos = new ArrayList<>();

    private int targetLocationIndex = 0;

    // this is used by the sense arrived_at_location
    private boolean arrivedAtLocation = false;
    private long timeWhenArivedAtLocation = 0;
    private int currentLocationIndex = 0;

    private Actuation actuation;
    private Mapping mapping;

    private void initTargetPositions(PepperLog pepperLog) {
        targetPos.add(makeRelativeFreeFrame(1.0,                 0.0, pepperLog));
        targetPos.add(makeRelativeFreeFrame(0.5,  Math.sqrt(3.0)/2.0, pepperLog));
        targetPos.add(makeRelativeFreeFrame(0.0,                 0.0, pepperLog));
    }

    public void reset()
    {
        stopMoving();
        targetPos.clear();

        arrivedAtLocation = false;
        targetLocationIndex = 0;
        currentLocationIndex = 0;
    }

    public void stopMoving() {
        if(gotoFuture != null) {
            gotoFuture.cancel(true);
        }
    }


    private FreeFrame makeRelativeFreeFrame(double x, double y, PepperLog pepperLog)
    {
        return actuation.async().robotFrame().andThenApply(robotFrame -> {
            // Create a FreeFrame representing the current robot frame.
            FreeFrame locationFrame = mapping.makeFreeFrame();
            //Transform transform = TransformBuilder.create().from2DTranslation(x, y);
            Transform transform = TransformBuilder.create().fromTranslation(new Vector3(x, y, 1.80));
            locationFrame.update(robotFrame, transform, 0L);

            // Store the FreeFrame in the next slot.
            return locationFrame;
        }).getValue();
    }

    public int getCurrentLocationIndex() {
        return currentLocationIndex;
    }

    public boolean getArrivedAtLocation() {
        return arrivedAtLocation;
    }

    public double getTimeSinceArrived() {
        return (double)(System.currentTimeMillis() - timeWhenArivedAtLocation) / 1000.0;
    }

    private Future<Void> gotoFuture = null;

    public void performGotoSquare(QiContext qiContext, Actuation actuation, Mapping mapping,
                            POSHBehaviourLibrary pbl, PepperLog pepperLog) {

        pbl.setAnimating(true);
        this.actuation = actuation;
        this.mapping = mapping;

        if(targetPos.isEmpty()) {
            initTargetPositions(pepperLog);
            pepperLog.appendLog(TAG, "Target Frame initialized");
        }

        // select next point to go to
        targetLocationIndex = (targetLocationIndex + 1) % targetPos.size();
        FreeFrame locationFrame = targetPos.get(targetLocationIndex);
        pepperLog.appendLog(TAG, "FreeFrame initialized " + targetLocationIndex);

        Future<Frame> frameFuture = locationFrame.async().frame();
        frameFuture.andThenCompose(frame -> {

            // Create a GoTo action.
            GoTo goTo = GoToBuilder.with(qiContext)
                    .withFrame(locationFrame.frame())
                    .build();

            // pepperLog.appendLog(TAG, "FreeFrame locationFrame");

            // Display text when the GoTo action starts.
            goTo.addOnStartedListener(() -> {
                arrivedAtLocation = false;
                pepperLog.appendLog(TAG, "Roaming started");
            });

            // Execute the GoTo action asynchronously.
            gotoFuture = goTo.async().run();

            return gotoFuture;
        }).thenConsume(future -> {
            if (future.isSuccess()) {
                pepperLog.appendLog(TAG, "Goto finished with success!");

                arrivedAtLocation = true;
                timeWhenArivedAtLocation = System.currentTimeMillis();

                currentLocationIndex = targetLocationIndex;
                pepperLog.appendLog(TAG, "Arrived at location: " + currentLocationIndex);
            } else if (future.hasError()) {
                pepperLog.appendLog(TAG, "Goto has error!");
            } else if (future.isCancelled()) {
                pepperLog.appendLog(TAG, "Goto has been cancelled, will stop here!");
            }

            gotoFuture = null;
            pbl.setAnimating(false);
            pbl.setReachedPosition(true);
        });
    }

    public void performRoam(QiContext qiContext, Actuation actuation, Mapping mapping,
                                  POSHBehaviourLibrary pbl, PepperLog pepperLog) {

        FutureUtils.wait(0, TimeUnit.SECONDS).andThenConsume((ignore) -> {
            pbl.setAnimating(true);

            double x = ThreadLocalRandom.current().nextDouble(-0.5, 0.5);
            double y = ThreadLocalRandom.current().nextDouble(-0.5, 0.5);

            // Get the robot frame.
            Frame robotFrame = actuation.robotFrame();

            // Create a FreeFrame representing the current robot frame.
            FreeFrame locationFrame = mapping.makeFreeFrame();

            Transform transform = TransformBuilder.create().from2DTranslation(x, y);
            locationFrame.update(robotFrame, transform, 0L);

            // Create a GoTo action.
            GoTo goTo = GoToBuilder.with(qiContext)
                    .withFrame(locationFrame.frame())
                    .build();

            // Display text when the GoTo action starts.
            goTo.addOnStartedListener(() -> pepperLog.appendLog(TAG, "Roaming started"));

            // Execute the GoTo action asynchronously.
            Future<Void> goToFuture = goTo.async().run();

            goToFuture.thenConsume(future -> {
                if (future.isSuccess()) {
                    pbl.setAnimating(false);
                    pepperLog.appendLog(TAG, "Roaming finished with success!");
                } else if (future.hasError()) {
                    pbl.setAnimating(false);
                    pepperLog.appendLog(TAG, "Roaming has error!");
                } else if (future.isCancelled()) {
                    pepperLog.appendLog(TAG, "Roaming has been cancelled!");
                    // If roaming is cancelled and pepper could not reach target then turn around
                }
            });
        });
    }

    /* public void performGotoRandom(QiContext qiContext, Actuation actuation, Mapping mapping,
                            POSHBehaviourLibrary pbl, PepperLog pepperLog) {
    } */

    public void performGotoTarget(QiContext qiContext, Actuation actuation, Mapping mapping,
                                  POSHBehaviourLibrary pbl, PepperLog pepperLog, double xTarget, double yTarget) {
        FutureUtils.wait(0, TimeUnit.SECONDS).andThenConsume((ignore) -> {
            pbl.setAnimating(true);

            // Get the robot frame.
            Frame robotFrame = actuation.robotFrame();

            // Create a FreeFrame representing the current robot frame.
            FreeFrame locationFrame = mapping.makeFreeFrame();

            Transform transform = TransformBuilder.create().from2DTranslation(xTarget, yTarget);
            locationFrame.update(robotFrame, transform, 0L);

            // Create a GoTo action.
            GoTo goTo = GoToBuilder.with(qiContext)
                    .withFrame(locationFrame.frame())
                    .build();

            // Display text when the GoTo action starts.
            goTo.addOnStartedListener(() -> pepperLog.appendLog(TAG, "Roaming started"));

            // Execute the GoTo action asynchronously.
            Future<Void> goToFuture = goTo.async().run();

            goToFuture.thenConsume(future -> {
                if (future.isSuccess()) {
                    pepperLog.appendLog(TAG, "Goto finished with success!");
                } else if (future.hasError()) {
                    pepperLog.appendLog(TAG, "Goto has error!");
                } else if (future.isCancelled()) {
                    pepperLog.appendLog(TAG, "Goto has been cancelled, will turn around!");
                }
                pbl.setAnimating(false);
                pbl.setReachedPosition(true);
            });
        });
    }

}
