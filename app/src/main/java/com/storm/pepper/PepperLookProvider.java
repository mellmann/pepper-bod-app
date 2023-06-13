package com.storm.pepper;

import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.builder.LookAtBuilder;
import com.aldebaran.qi.sdk.builder.TransformBuilder;
import com.aldebaran.qi.sdk.object.actuation.Actuation;
import com.aldebaran.qi.sdk.object.actuation.Frame;
import com.aldebaran.qi.sdk.object.actuation.FreeFrame;
import com.aldebaran.qi.sdk.object.actuation.LookAt;
import com.aldebaran.qi.sdk.object.actuation.LookAtMovementPolicy;
import com.aldebaran.qi.sdk.object.actuation.Mapping;
import com.aldebaran.qi.sdk.object.geometry.Transform;

public class PepperLookProvider {

    public void lookUp(QiContext qiContext, Actuation actuation) {
        Frame robotFrame = actuation.robotFrame();
        Mapping mapping = qiContext.getMapping();
        FreeFrame targetFrame = mapping.makeFreeFrame();

        Transform transform = TransformBuilder.create().fromXTranslation(1);

        targetFrame.update(robotFrame, transform, 0L);

        LookAt lookAt = LookAtBuilder.with(qiContext)
                .withFrame(targetFrame.frame())
                .build();

        lookAt.setPolicy(LookAtMovementPolicy.HEAD_AND_BASE);

        lookAt.async().run();
    }

}
