package com.storm.pepper;

import com.aldebaran.qi.Future;
import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.builder.AnimateBuilder;
import com.aldebaran.qi.sdk.builder.AnimationBuilder;
import com.aldebaran.qi.sdk.object.actuation.Animate;
import com.aldebaran.qi.sdk.object.actuation.Animation;

import java.util.Random;

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
            case "GetAttention":
            case "WaveLeft":
                int[] motions = new int[]{R.raw.raise_both_2, R.raw.hello_07, R.raw.hello_04, R.raw.both_hands_front, R.raw.salute_left, R.raw.salute_right};
                int idx = new Random().nextInt(motions.length);
                resID = R.raw.left_hand_high_b001;
                resID = motions[idx];
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
                break;
            case "Laugh":
                //resID = R.raw.laughing;
                resID = R.raw.raise_both_2;
                break;
            default:
                pepperLog.appendLog(TAG, "Unknown Animation: " + toAnimate);
                break;
        }

            animationFuture = AnimationBuilder.with(qiContext)
                .withResources(resID)
                .buildAsync();

            animationFuture.andThenConsume(myAnimation -> {
                animate = AnimateBuilder.with(qiContext)
                        .withAnimation(myAnimation)
                        .build();
                // Run the action synchronously in this thread
                animate.run();

                switch (toAnimate) {
                    case "WaveLeft":
                        pbl.setHaveWavedLeft(true);
                        break;
                    case "WaveRight":
                        pbl.setHaveWavedRight(true);
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
                        break;
                    case "Laugh":
                        pbl.hasLaughed = true;
                        break;
                    default:
                        pepperLog.appendLog(TAG, "Unknown Animation Done: " + toAnimate);
                        break;
                }

                pepperLog.appendLog(TAG, "Animation Done: " + toAnimate);

                /*animate.addOnLabelReachedListener((label, time) -> {
                    // Called when a label is reached.
                    switch (toAnimate) {
                        case "WaveLeft":
                            pbl.setHaveWavedLeft(true);
                            break;
                        case "WaveRight":
                            pbl.setHaveWavedRight(tre);
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
                            break;
                        case "Laugh":
                            pbl.hasLaughed = true;
                            break;
                        default:
                            break;
                    }
                });*/
            });
    }
}
