package io.ipoli.android.app.services;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.gson.Gson;
import com.squareup.otto.Bus;

import org.joda.time.LocalDate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.inject.Inject;

import io.ipoli.android.BuildConfig;
import io.ipoli.android.Constants;
import io.ipoli.android.app.App;
import io.ipoli.android.app.events.PlayerCreatedEvent;
import io.ipoli.android.app.net.AuthProviderName;
import io.ipoli.android.app.net.JsonRequestBodyBuilder;
import io.ipoli.android.app.net.RemoteObject;
import io.ipoli.android.app.net.iPoliAPIService;
import io.ipoli.android.app.services.events.SyncCompleteEvent;
import io.ipoli.android.app.utils.DateUtils;
import io.ipoli.android.app.utils.IDGenerator;
import io.ipoli.android.app.utils.LocalStorage;
import io.ipoli.android.challenge.data.Challenge;
import io.ipoli.android.challenge.persistence.ChallengePersistenceService;
import io.ipoli.android.challenge.persistence.RealmChallengePersistenceService;
import io.ipoli.android.player.AuthProvider;
import io.ipoli.android.player.Player;
import io.ipoli.android.player.persistence.PlayerPersistenceService;
import io.ipoli.android.player.persistence.RealmPlayerPersistenceService;
import io.ipoli.android.quest.data.Quest;
import io.ipoli.android.quest.data.Reminder;
import io.ipoli.android.quest.data.RepeatingQuest;
import io.ipoli.android.quest.data.SubQuest;
import io.ipoli.android.quest.generators.CoinsRewardGenerator;
import io.ipoli.android.quest.generators.ExperienceRewardGenerator;
import io.ipoli.android.quest.persistence.QuestPersistenceService;
import io.ipoli.android.quest.persistence.RealmQuestPersistenceService;
import io.ipoli.android.quest.persistence.RealmRepeatingQuestPersistenceService;
import io.ipoli.android.quest.persistence.RepeatingQuestPersistenceService;
import io.ipoli.android.quest.schedulers.PersistentRepeatingQuestScheduler;
import io.ipoli.android.quest.schedulers.RepeatingQuestScheduler;
import io.realm.Realm;
import io.realm.RealmList;
import okhttp3.RequestBody;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 3/25/16.
 */
public class AppJobService extends JobService {

    public static final String DEBUG_ADVERTISING_ID = "12345";
    @Inject
    iPoliAPIService apiService;

    @Inject
    RepeatingQuestScheduler repeatingQuestScheduler;

    @Inject
    Gson gson;

    @Inject
    Bus eventBus;

    @Override
    public boolean onStartJob(JobParameters params) {
        App.getAppComponent(this).inject(this);

        Observable.defer(() -> {
            Log.d("RxJava", "Sync start");
            Realm realm = Realm.getDefaultInstance();
            try {
                PlayerPersistenceService playerPersistenceService = new RealmPlayerPersistenceService(realm);
                ChallengePersistenceService challengePersistenceService = new RealmChallengePersistenceService(eventBus, realm);
                QuestPersistenceService questPersistenceService = new RealmQuestPersistenceService(eventBus, realm);
                RepeatingQuestPersistenceService repeatingQuestPersistenceService = new RealmRepeatingQuestPersistenceService(eventBus, realm);
                Player p = syncPlayer(playerPersistenceService);
                if (p == null) {
                    return Observable.empty();
                }
                syncChallenges(challengePersistenceService, p);
                syncRemovedQuests(p);
                syncRepeatingQuests(challengePersistenceService, repeatingQuestPersistenceService, p);
                syncQuests(challengePersistenceService, questPersistenceService, repeatingQuestPersistenceService, p);
                getChallenges(challengePersistenceService, p);
                getRepeatingQuests(challengePersistenceService, repeatingQuestPersistenceService, p);
                getQuests(challengePersistenceService, questPersistenceService, repeatingQuestPersistenceService, p);
                scheduleRepeatingQuests(questPersistenceService, repeatingQuestPersistenceService);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                if (realm != null && !realm.isClosed()) {
                    realm.close();
                }
            }
            return Observable.empty();
        }).compose(applyAPISchedulers())
                .subscribe(res -> Log.d("RxJava", "OnNext " + res), throwable -> {
                    Log.e("RxJava", "Error", throwable);
                    jobFinished(params, true);
                }, () -> {
                    eventBus.post(new SyncCompleteEvent());
                    Log.d("RxJava", "Sync Job finished");
                    jobFinished(params, false);
                });
        return true;
    }

    private void scheduleRepeatingQuests(QuestPersistenceService questPersistenceService, RepeatingQuestPersistenceService repeatingQuestPersistenceService) {
        List<RepeatingQuest> repeatingQuests = repeatingQuestPersistenceService.findAllNonAllDayActiveRepeatingQuests();
        new PersistentRepeatingQuestScheduler(repeatingQuestScheduler, questPersistenceService).schedule(repeatingQuests, DateUtils.toStartOfDayUTC(LocalDate.now()));
    }

    private Player syncPlayer(PlayerPersistenceService playerPersistenceService) throws IOException {
        LocalStorage localStorage = LocalStorage.of(getApplicationContext());
        Player player = playerPersistenceService.find();
        if (player == null) {
            return null;
        }
        if (isLocalOnly(player)) {
            return createPlayer(playerPersistenceService, localStorage, player);
        }
        if (player.needsSyncWithRemote()) {
            return updatePlayer(player, playerPersistenceService);
        }
        return player;
    }

    private Player updatePlayer(Player player, PlayerPersistenceService playerPersistenceService) throws IOException {
        String localId = player.getId();
        player.setId(null);
        Player sp = apiService.updatePlayer(createRequestBody("data", player), player.getRemoteId()).execute().body();
        sp.setSyncedWithRemote();
        sp.setRemoteId(sp.getId());
        sp.setId(localId);
        playerPersistenceService.saveSync(sp, false);
        return sp;
    }

    private Player createPlayer(PlayerPersistenceService playerPersistenceService, LocalStorage localStorage, Player player) throws IOException {
        String advertisingId = getAdvertisingId();
        String localId = player.getId();
        AuthProvider authProvider = new AuthProvider(advertisingId, AuthProviderName.GOOGLE_ADVERTISING_ID.name());
        playerPersistenceService.addAuthProvider(player, authProvider);
        player.setId(null);
        Player sp = apiService.createPlayer(createRequestBody("data", player)).execute().body();
        sp.setSyncedWithRemote();
        sp.setRemoteId(sp.getId());
        sp.setId(localId);
        localStorage.saveString(Constants.KEY_PLAYER_REMOTE_ID, sp.getRemoteId());
        eventBus.post(new PlayerCreatedEvent(sp.getRemoteId()));
        playerPersistenceService.saveSync(sp, false);
        return sp;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d("RxJava", "Stopping job" + params);
        return false;
    }

    private String getAdvertisingId() {
        try {
            AdvertisingIdClient.Info idInfo = AdvertisingIdClient.getAdvertisingIdInfo(getApplicationContext());
            return idInfo.getId();
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                return DEBUG_ADVERTISING_ID;
            }
            throw new RuntimeException(e);
        }
    }

    private void syncRemovedQuests(Player player) throws IOException {
        LocalStorage localStorage = LocalStorage.of(getApplicationContext());
        Set<String> removedQuests = localStorage.readStringSet(Constants.KEY_REMOVED_QUESTS);
        if (removedQuests.isEmpty()) {
            return;
        }
        RequestBody requestBody = createRequestBody().param("data", removedQuests).param("player_id", player.getRemoteId()).build();
        apiService.deleteQuests(requestBody).execute();
        removedQuests.clear();
        localStorage.saveStringSet(Constants.KEY_REMOVED_QUESTS, removedQuests);
    }

    private void syncChallenges(ChallengePersistenceService challengePersistenceService, Player player) throws IOException {
        List<Challenge> challenges = challengePersistenceService.findAllWhoNeedSyncWithRemote();
        if (challenges.isEmpty()) {
            return;
        }

        List<String> localIds = new ArrayList<>();
        for (Challenge c : challenges) {
            localIds.add(getLocalIdForRemoteObject(c));
            c.setId(isLocalOnly(c) ? null : c.getRemoteId());
        }

        RequestBody requestBody = createRequestBody().param("data", challenges).param("player_id", player.getRemoteId()).build();
        List<Challenge> serverChallenges = apiService.syncChallenges(requestBody).execute().body();
        for (int i = 0; i < serverChallenges.size(); i++) {
            Challenge challenge = serverChallenges.get(i);
            updateChallenge(challenge, localIds.get(i));
        }
        challengePersistenceService.saveSync(serverChallenges, false);
    }

    private Challenge updateChallenge(Challenge serverChallenge, String localId) {
        serverChallenge.setSyncedWithRemote();
        serverChallenge.setRemoteId(serverChallenge.getId());
        serverChallenge.setId(localId);
        if (serverChallenge.getExperience() == null) {
            serverChallenge.setExperience(new ExperienceRewardGenerator().generate(serverChallenge));
        }
        if (serverChallenge.getCoins() == null) {
            serverChallenge.setCoins(new CoinsRewardGenerator().generate(serverChallenge));
        }
        return serverChallenge;
    }

    private void syncQuests(ChallengePersistenceService challengePersistenceService, QuestPersistenceService questPersistenceService, RepeatingQuestPersistenceService repeatingQuestPersistenceService, Player player) throws IOException {
        List<Quest> quests = questPersistenceService.findAllWhoNeedSyncWithRemote();
        if (quests.isEmpty()) {
            return;
        }
        List<String> localIds = new ArrayList<>();
        for (Quest q : quests) {
            localIds.add(getLocalIdForRemoteObject(q));
            q.setId(isLocalOnly(q) ? null : q.getRemoteId());
            if (q.getRepeatingQuest() != null) {
                q.getRepeatingQuest().setId(q.getRepeatingQuest().getRemoteId());
            }
            if (q.getChallenge() != null) {
                q.getChallenge().setId(q.getChallenge().getRemoteId());
            }

            if (q.getReminders() != null && !q.getReminders().isEmpty()) {
                for (Reminder reminder : q.getReminders()) {
                    reminder.setId(reminder.getRemoteId());
                }
            }

            if (q.getSubQuests() != null && !q.getSubQuests().isEmpty()) {
                for (SubQuest subQuest : q.getSubQuests()) {
                    subQuest.setId(subQuest.getRemoteId());
                }
            }
        }
        RequestBody requestBody = createRequestBody().param("data", quests).param("player_id", player.getRemoteId()).build();
        List<Quest> serverQuests = apiService.syncQuests(requestBody).execute().body();
        for (int i = 0; i < serverQuests.size(); i++) {
            Quest sq = serverQuests.get(i);
            String localId = localIds.get(i);
            Quest localQuest = questPersistenceService.findAnyById(localId);
            updateServerReminders(sq.getReminders(), localQuest.getReminders());
            questPersistenceService.saveReminders(localQuest, sq.getReminders(), false);
            updateServerSubquests(sq.getSubQuests(), localQuest.getSubQuests());
            questPersistenceService.saveSubQuests(localQuest, sq.getSubQuests(), false);
            updateQuest(challengePersistenceService, repeatingQuestPersistenceService, sq, localId);
        }
        questPersistenceService.saveSync(serverQuests, false);
    }

    private void updateServerReminders(RealmList<Reminder> serverReminders, RealmList<Reminder> localReminders) {
        int notificationId = localReminders == null || localReminders.isEmpty() ?
                new Random().nextInt() :
                localReminders.get(0).getNotificationId();

        for (int j = 0; j < serverReminders.size(); j++) {
            Reminder sr = serverReminders.get(j);
            sr.setRemoteId(sr.getId());

            if (localReminders != null && j < localReminders.size()) {
                Reminder lr = localReminders.get(j);
                sr.setId(lr.getId());
            } else {
                sr.setId(IDGenerator.generate());
            }
            sr.setNotificationId(notificationId);
        }
    }

    private void updateServerSubquests(RealmList<SubQuest> serverSubQuests, RealmList<SubQuest> localSubQuests) {
        for (int j = 0; j < serverSubQuests.size(); j++) {
            SubQuest ssq = serverSubQuests.get(j);
            ssq.setRemoteId(ssq.getId());

            if (localSubQuests != null && j < localSubQuests.size()) {
                SubQuest lsq = localSubQuests.get(j);
                ssq.setId(lsq.getId());
            } else {
                ssq.setId(IDGenerator.generate());
            }
        }
    }

    @NonNull
    private JsonRequestBodyBuilder createRequestBody() {
        return new JsonRequestBodyBuilder(getApplicationContext());
    }

    @NonNull
    private RequestBody createRequestBody(String param, Object value) {
        return new JsonRequestBodyBuilder(getApplicationContext()).param(param, value).build();
    }

    private void syncRepeatingQuests(ChallengePersistenceService challengePersistenceService, RepeatingQuestPersistenceService repeatingQuestPersistenceService, Player player) throws IOException {
        List<RepeatingQuest> quests = repeatingQuestPersistenceService.findAllWhoNeedSyncWithRemote();
        if (quests.isEmpty()) {
            return;
        }
        List<String> localIds = new ArrayList<>();
        for (RepeatingQuest q : quests) {
            localIds.add(getLocalIdForRemoteObject(q));
            q.setId(isLocalOnly(q) ? null : q.getRemoteId());
            if (q.getChallenge() != null) {
                q.getChallenge().setId(q.getChallenge().getRemoteId());
            }
        }
        RequestBody requestBody = createRequestBody().param("data", quests).param("player_id", player.getRemoteId()).build();
        List<RepeatingQuest> serverQuests = apiService.syncRepeatingQuests(requestBody).execute().body();
        for (int i = 0; i < serverQuests.size(); i++) {
            RepeatingQuest sq = serverQuests.get(i);
            String localId = localIds.get(i);
            RepeatingQuest repeatingQuest = repeatingQuestPersistenceService.findAnyById(localId);
            updateServerReminders(sq.getReminders(), repeatingQuest.getReminders());
            repeatingQuestPersistenceService.saveReminders(repeatingQuest, sq.getReminders(), false);
            updateServerSubquests(sq.getSubQuests(), repeatingQuest.getSubQuests());
            repeatingQuestPersistenceService.saveSubQuests(repeatingQuest, sq.getSubQuests(), false);
            updateRepeatingQuest(challengePersistenceService, sq, localIds.get(i));
        }
        repeatingQuestPersistenceService.saveSync(serverQuests, false);
    }

    private RepeatingQuest updateRepeatingQuest(ChallengePersistenceService challengePersistenceService, RepeatingQuest serverQuest, String localId) {
        serverQuest.setSyncedWithRemote();
        serverQuest.setRemoteId(serverQuest.getId());
        serverQuest.setId(localId);

        if (serverQuest.getChallenge() != null) {
            Challenge challenge = challengePersistenceService.findAnyByRemoteId(serverQuest.getChallenge().getId());
            if (challenge != null) {
                serverQuest.setChallenge(challenge);
            }
        }
        return serverQuest;
    }

    private void getChallenges(ChallengePersistenceService challengePersistenceService, Player player) throws IOException {
        List<Challenge> serverChallenges = apiService.getChallenges(player.getRemoteId()).execute().body();
        List<Challenge> challengesToSave = new ArrayList<>();
        for (Challenge sc : serverChallenges) {
            Challenge challenge = challengePersistenceService.findAnyByRemoteId(sc.getId());
            if (challenge != null && sc.getUpdatedAt().getTime() <= challenge.getUpdatedAt().getTime()) {
                continue;
            }
            String localId = getLocalIdForRemoteObject(challenge);
            challengesToSave.add(updateChallenge(sc, localId));
        }
        challengePersistenceService.saveSync(challengesToSave, false);
    }

    private void getRepeatingQuests(ChallengePersistenceService challengePersistenceService, RepeatingQuestPersistenceService repeatingQuestPersistenceService, Player player) throws IOException {
        List<RepeatingQuest> serverQuests = apiService.getRepeatingQuests(player.getRemoteId()).execute().body();
        List<RepeatingQuest> questsToSave = new ArrayList<>();
        for (RepeatingQuest sq : serverQuests) {
            RepeatingQuest repeatingQuest = repeatingQuestPersistenceService.findAnyByRemoteId(sq.getId());
            if (repeatingQuest != null && sq.getUpdatedAt().getTime() <= repeatingQuest.getUpdatedAt().getTime()) {
                continue;
            }
            String localId = getLocalIdForRemoteObject(repeatingQuest);
            if (repeatingQuest != null) {
                updateServerReminders(sq.getReminders(), repeatingQuest.getReminders());
                repeatingQuestPersistenceService.saveReminders(repeatingQuest, sq.getReminders(), false);
                updateServerSubquests(sq.getSubQuests(), repeatingQuest.getSubQuests());
                repeatingQuestPersistenceService.saveSubQuests(repeatingQuest, sq.getSubQuests(), false);
            } else {
                int notificationId = new Random().nextInt();
                for (Reminder r : sq.getReminders()) {
                    r.setRemoteId(r.getId());
                    r.setId(IDGenerator.generate());
                    r.setNotificationId(notificationId);
                }
                repeatingQuestPersistenceService.setReminders(sq, sq.getReminders());

                for(SubQuest s : sq.getSubQuests()) {
                    s.setRemoteId(s.getId());
                    s.setId(IDGenerator.generate());
                }
                repeatingQuestPersistenceService.setSubQuests(sq, sq.getSubQuests());
            }
            questsToSave.add(updateRepeatingQuest(challengePersistenceService, sq, localId));
        }
        repeatingQuestPersistenceService.saveSync(questsToSave, false);
    }

    private void getQuests(ChallengePersistenceService challengePersistenceService, QuestPersistenceService questPersistenceService, RepeatingQuestPersistenceService repeatingQuestPersistenceService, Player player) throws IOException {
        List<Quest> serverQuests = apiService.getQuests(player.getRemoteId()).execute().body();
        List<Quest> questsToSave = new ArrayList<>();
        for (Quest sq : serverQuests) {
            Quest quest = questPersistenceService.findAnyByRemoteId(sq.getId());
            if (quest != null && sq.getUpdatedAt().getTime() <= quest.getUpdatedAt().getTime()) {
                continue;
            }
            String localId = getLocalIdForRemoteObject(quest);
            if (quest != null) {
                updateServerReminders(sq.getReminders(), quest.getReminders());
                questPersistenceService.saveReminders(quest, sq.getReminders(), false);
                updateServerSubquests(sq.getSubQuests(), quest.getSubQuests());
                questPersistenceService.saveSubQuests(quest, sq.getSubQuests(), false);
            } else {
                int notificationId = new Random().nextInt();
                for (Reminder r : sq.getReminders()) {
                    r.setRemoteId(r.getId());
                    r.setId(IDGenerator.generate());
                    r.setNotificationId(notificationId);
                }
                questPersistenceService.setReminders(sq, sq.getReminders());

                for(SubQuest s : sq.getSubQuests()) {
                    s.setRemoteId(s.getId());
                    s.setId(IDGenerator.generate());
                }
                questPersistenceService.setSubQuests(sq, sq.getSubQuests());
            }
            questsToSave.add(updateQuest(challengePersistenceService, repeatingQuestPersistenceService, sq, localId));
        }
        questPersistenceService.saveSync(questsToSave, false);
    }

    private Quest updateQuest(ChallengePersistenceService challengePersistenceService, RepeatingQuestPersistenceService repeatingQuestPersistenceService, Quest serverQuest, String localId) {
        serverQuest.setSyncedWithRemote();
        serverQuest.setRemoteId(serverQuest.getId());
        serverQuest.setId(localId);
        if (serverQuest.getExperience() == null) {
            serverQuest.setExperience(new ExperienceRewardGenerator().generate(serverQuest));
        }
        if (serverQuest.getCoins() == null) {
            serverQuest.setCoins(new CoinsRewardGenerator().generate(serverQuest));
        }
        if (serverQuest.getRepeatingQuest() != null) {
            RepeatingQuest repeatingQuest = repeatingQuestPersistenceService.findAnyByRemoteId(serverQuest.getRepeatingQuest().getId());
            if (repeatingQuest != null) {
                serverQuest.setRepeatingQuest(repeatingQuest);
            }
        }
        if (serverQuest.getChallenge() != null) {
            Challenge challenge = challengePersistenceService.findAnyByRemoteId(serverQuest.getChallenge().getId());
            if (challenge != null) {
                serverQuest.setChallenge(challenge);
            }
        }

        return serverQuest;
    }

    private String getLocalIdForRemoteObject(RemoteObject<?> remoteObject) {
        if (remoteObject != null) {
            return remoteObject.getId();
        } else {
            return IDGenerator.generate();
        }
    }

    private <T> Observable.Transformer<T, T> applyAPISchedulers() {
        return observable -> observable.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private boolean isLocalOnly(RemoteObject remoteObject) {
        return TextUtils.isEmpty(remoteObject.getRemoteId());
    }
}