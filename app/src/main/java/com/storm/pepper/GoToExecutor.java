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
import com.aldebaran.qi.sdk.util.FutureUtils;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class GoToExecutor {

    private static final String TAG = GoToExecutor.class.getSimpleName();

    // public void setValues()
    double[] xPositions = { -1.0, 1.0 };
    double[] yPositions = { -1.0, 1.0 };

    public void performGotoSquare(QiContext qiContext, Actuation actuation, Mapping mapping,
                            POSHBehaviourLibrary pbl, PepperLog pepperLog) {

        FutureUtils.wait(0, TimeUnit.SECONDS).andThenConsume((ignore) -> {
            pbl.setAnimating(true);

            int xRand = ThreadLocalRandom.current().nextInt(2);
            int yRand = ThreadLocalRandom.current().nextInt(2);
            double x = xPositions[xRand];
            double y = yPositions[yRand];

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
