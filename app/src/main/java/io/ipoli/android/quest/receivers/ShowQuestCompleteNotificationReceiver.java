package io.ipoli.android.quest.receivers;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.NotificationCompat;

import com.squareup.otto.Bus;

import javax.inject.Inject;

import io.ipoli.android.Constants;
import io.ipoli.android.MainActivity;
import io.ipoli.android.R;
import io.ipoli.android.app.App;
import io.ipoli.android.quest.schedulers.QuestNotificationScheduler;
import io.ipoli.android.quest.data.Quest;
import io.ipoli.android.quest.persistence.QuestPersistenceService;
import io.ipoli.android.quest.persistence.RealmQuestPersistenceService;
import io.realm.Realm;

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 2/1/16.
 */
public class ShowQuestCompleteNotificationReceiver extends BroadcastReceiver {

    public static final String ACTION_SHOW_DONE_QUEST_NOTIFICATION = "io.ipoli.android.intent.action.SHOW_QUEST_COMPLETE_NOTIFICATION";

    @Inject
    Bus eventBus;

    QuestPersistenceService questPersistenceService;

    private NotificationCompat.Builder createNotificationBuilder(Context context, Quest q) {
        Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher);
        return (NotificationCompat.Builder) new NotificationCompat.Builder(context)
                .setContentTitle(q.getName())
                .setContentText("Quest done! Ready for a break?")
                .setContentIntent(getPendingIntent(context, q.getId()))
                .setSmallIcon(R.drawable.ic_notification_small)
                .setLargeIcon(largeIcon)
                .setWhen(q.getActualStart().getTime())
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true);
    }

    private PendingIntent getPendingIntent(Context context, String questId) {
        Intent i = new Intent(context, MainActivity.class);
        i.putExtra(Constants.QUEST_ID_EXTRA_KEY, questId);
        i.setAction(MainActivity.ACTION_QUEST_COMPLETE);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        App.getAppComponent(context).inject(this);

        String questId = intent.getStringExtra(Constants.QUEST_ID_EXTRA_KEY);
        QuestNotificationScheduler.stopTimer(questId, context);
        Realm realm = Realm.getDefaultInstance();
        questPersistenceService = new RealmQuestPersistenceService(eventBus, realm);
        Quest q = questPersistenceService.findById(questId);
        NotificationCompat.Builder builder = createNotificationBuilder(context, q);
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
        notificationManagerCompat.notify(Constants.QUEST_COMPLETE_NOTIFICATION_ID, builder.build());
        realm.close();
    }
}
