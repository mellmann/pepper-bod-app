package com.storm.pepper;

import com.aldebaran.qi.Future;
import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.builder.AnimateBuilder;
import com.aldebaran.qi.sdk.builder.AnimationBuilder;
import com.aldebaran.qi.sdk.object.actuation.Animate;
import com.aldebaran.qi.sdk.object.actuation.Animation;

public class AnimationExecutor {
    private static final String TAG = AnimationExecutor.class.getSimpleName();
    private Future<Animation> animationFuture;
    private QiContext qiContext;
    private Animate animate;

    public void animate(String toAnimate, QiContext qi, POSHBehaviourLibrary pbl,
                        PepperLog pepperLog) {
        qiContext = qi;
        int resID = 0;
        // Create an animation object.
        switch(toAnimate) {
            case "WaveLeft":
                resID = R.raw.left_hand_high_b001;
                break;
            case "WaveRight":
                resID = R.raw.right_hand_high_b001;
                break;
            case "TurnAround":
                resID = R.raw.turn_around;
                break;
            case "Hug":
                resID = R.raw.pepper_hug_2;
                break;
            case "HighFive":
                resID = R.raw.high_five;
                break;
            case "ShakeHands":
                resID = R.raw.hand_geben;
                break;
            case "CheckWatch":
                resID = R.raw.check_time_left_b001;
                break;
            case "WashHands":
                resID = R.raw.washing_arms_b001;
            case "Laugh":
                resID = R.raw.laughing;
            default:
                break;
        }

        animationFuture = AnimationBuilder.with(qiContext)
                .withResources(resID)
                .buildAsync();
        try {
            animationFuture.andThenConsume(myAnimation -> {
                animate = AnimateBuilder.with(qiContext)
                        .withAnimation(myAnimation)
                        .build();
                // Run the action synchronously in this thread
                animate.async().run();

                animate.addOnLabelReachedListener((label, time) -> {
                    // Called when a label is reached.
                    switch (toAnimate) {
                        case "WaveLeft":
                            pbl.haveWavedLeft = true;
                            break;
                        case "WaveRight":
                            pbl.haveWavedRight = true;
                            break;
                        case "TurnAround":
                            pbl.turnedAround = true;
                            break;
                        case "Hug":
                            pbl.haveHugged = true;
                            break;
                        case "HighFive":
                            pbl.haveHighFived = true;
                            break;
                        case "ShakeHands":
                            pbl.haveShakedHands = true;
                            break;
                        case "CheckWatch":
                            pbl.haveCheckedWatch = true;
                            break;
                        case "WashHands":
                            pbl.haveWashedHands = true;
                        case "Laugh":
                            pbl.hasLaughed = true;
                        default:
                            break;
                    }
                });
            });
        } catch (Exception e) {
            pepperLog.appendLog(TAG, "Animation Exception");
        }
    }
}
