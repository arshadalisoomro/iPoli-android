package io.ipoli.android.services;

import android.util.Log;

import com.squareup.otto.Bus;

import io.ipoli.android.assistant.events.HelpEvent;
import io.ipoli.android.assistant.events.PlanTodayEvent;
import io.ipoli.android.assistant.events.RenameAssistantEvent;
import io.ipoli.android.assistant.events.ReviewTodayEvent;
import io.ipoli.android.assistant.events.ShowQuestsEvent;
import io.ipoli.android.assistant.events.UnknownCommandEvent;
import io.ipoli.android.quest.events.NewQuestEvent;

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 1/7/16.
 */
public class LocalCommandParserService implements CommandParserService {

    public enum Command {
        ADD_QUEST, SHOW_QUESTS, PLAN_TODAY, REVIEW_TODAY, RENAME, HELP, UNKNOWN;

        @Override
        public String toString() {
            return this.name().toLowerCase().replace("_", " ");
        }

        public static Command fromText(String text) {
            for (Command cmd : values()) {
                if (text.startsWith(cmd.toString())) {
                    return cmd;
                }
            }
            return UNKNOWN;
        }
    }

    private final Bus bus;

    public LocalCommandParserService(Bus bus) {
        this.bus = bus;
    }

    @Override
    public void parse(String command) {
        String c = command.trim();
        String lc = c.toLowerCase();
        Log.d("CommandParser", lc);
        Command cmd = Command.fromText(lc);
        switch (cmd) {
            case ADD_QUEST:
                bus.post(new NewQuestEvent(c.substring(parameterStartIndex(lc, cmd.toString()))));
                break;
            case SHOW_QUESTS:
                bus.post(new ShowQuestsEvent());
                break;
            case RENAME:
                bus.post(new RenameAssistantEvent(c.substring(parameterStartIndex(lc, cmd.toString()))));
                break;
            case PLAN_TODAY:
                bus.post(new PlanTodayEvent());
                break;
            case REVIEW_TODAY:
                bus.post(new ReviewTodayEvent());
                break;
            case HELP:
                bus.post(new HelpEvent());
                break;
            default:
                bus.post(new UnknownCommandEvent());
        }
    }

    private int parameterStartIndex(String text, String command) {
        int startIndex = text.indexOf(command);
        return startIndex + command.length() + 1;
    }
}
