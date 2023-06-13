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
import com.aldebaran.qi.sdk.object.geometry.Vector3;

public class PepperLookProvider {

    public void lookUp(QiContext qiContext, Actuation actuation) {
        Frame robotFrame = actuation.robotFrame();
        Mapping mapping = qiContext.getMapping();
        FreeFrame targetFrame = mapping.makeFreeFrame();

        Vector3 target = new Vector3(1.0, 0.0, 2.7);
        Transform transform = TransformBuilder.create().fromTranslation(target);

        targetFrame.update(robotFrame, transform, 0L);

        LookAt lookAt = LookAtBuilder.with(qiContext)
                .withFrame(targetFrame.frame())
                .build();

        lookAt.setPolicy(LookAtMovementPolicy.HEAD_AND_BASE);

        lookAt.async().run();
    }

}
