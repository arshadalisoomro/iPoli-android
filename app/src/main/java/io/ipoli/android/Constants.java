package io.ipoli.android;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 6/15/15.
 */
public interface Constants {
    String DEFAULT_PLAN_DAY_TIME = "9:00";
    String DEFAULT_REVIEW_DAY_TIME = "22:00";

    int COMPLETE_QUEST_RESULT_REQUEST_CODE = 10;
    int EDIT_QUEST_RESULT_REQUEST_CODE = 11;

    int REMIND_PLAN_DAY_NOTIFICATION_ID = 101;
    int REMIND_REVIEW_DAY_NOTIFICATION_ID = 102;
    int REMIND_START_QUEST_NOTIFICATION_ID = 103;

    int QUEST_TIMER_NOTIFICATION_ID = 201;
    int ASK_FOR_FEEDBACK_PROBABILITY = 15;
    int COMPLETE_QUEST_DEFAULT_EXPERIENCE = 5;

    int DEFAULT_PLAYER_LEVEL = 0;
    int DEFAULT_PLAYER_EXPERIENCE = 5;

    int DEFAULT_SNOOZE_TIME_MINUTES = 10;
    String DEFAULT_ASSISTANT_AVATAR = "avatar_06";
    String DEFAULT_PLAYER_AVATAR = "avatar_07";
    int INVITE_PLAYER_PROBABILITY = 50;
    int REMIND_QUEST_START_REQUEST_CODE = 1001;
    int QUEST_UPDATE_TIMER_REQUEST_CODE = 1002;

    String QUEST_ID_EXTRA_KEY = "quest_id";
    String POSITION_EXTRA_KEY = "quest_position";

    int[] BASE_XP_OUTCOMES = new int[]{5, 10, 15};


    SimpleDateFormat DEFAULT_TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.getDefault());
}