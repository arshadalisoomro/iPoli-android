package io.ipoli.android.quest.events.subquests;

import io.ipoli.android.quest.data.SubQuest;

/**
 * Created by Polina Zhelyazkova <polina@ipoli.io>
 * on 7/11/16.
 */
public class UpdateSubQuestNameEvent {
    public final SubQuest subQuest;

    public UpdateSubQuestNameEvent(SubQuest subQuest) {
        this.subQuest = subQuest;
    }
}
