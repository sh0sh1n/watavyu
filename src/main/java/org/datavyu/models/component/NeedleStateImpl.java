package org.datavyu.models.component;

public class NeedleStateImpl implements NeedleState {

    private long time;

    NeedleStateImpl(long time) {
        this.time = time;
    }

    @Override
    public long getCurrentTime() {
        return time;
    }

}
