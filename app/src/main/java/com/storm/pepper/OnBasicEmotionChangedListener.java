package com.storm.pepper;

import com.aldebaran.qi.sdk.object.human.AttentionState;
import com.aldebaran.qi.sdk.object.human.FacialExpressions;

public interface OnBasicEmotionChangedListener {
    void onBasicEmotionChanged(BasicEmotion basicEmotion);
    void onBasicEmotionChanged(AttentionState attentionState);
    void onBasicEmotionChanged(FacialExpressions facialExpressions);
}
