package io.ipoli.android.quest.persistence;

import org.joda.time.LocalDate;

import java.util.Date;
import java.util.List;

import io.ipoli.android.app.persistence.PersistenceService;
import io.ipoli.android.challenge.data.Challenge;
import io.ipoli.android.quest.data.Quest;
import io.ipoli.android.quest.data.Reminder;
import io.ipoli.android.quest.data.RepeatingQuest;
import io.ipoli.android.quest.data.SubQuest;

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 1/7/16.
 */
public interface QuestPersistenceService extends PersistenceService<Quest> {

    void findAllUnplanned(OnDatabaseChangedListener<Quest> listener);

    void findPlannedNonAllDayBetween(LocalDate startDate, LocalDate endDate, OnDatabaseChangedListener<Quest> listener);

    List<Quest> findAllCompletedNonAllDayBetween(LocalDate startDate, LocalDate endDate);

    List<Quest> findAllPlannedAndStartedToday();

    List<Quest> findAllIncompleteToDosBefore(LocalDate localDate);

    List<Quest> findAllCompletedWithStartTime(RepeatingQuest repeatingQuest);

    long countCompleted(RepeatingQuest repeatingQuest, LocalDate fromDate, LocalDate toDate);

    long countCompleted(RepeatingQuest repeatingQuest);

    void findAllNonAllDayForDate(LocalDate currentDate, OnDatabaseChangedListener<Quest> listener);

    void findAllNonAllDayCompletedForDate(LocalDate currentDate, OnDatabaseChangedListener<Quest> listener);

    void findAllNonAllDayIncompleteForDate(LocalDate currentDate, OnDatabaseChangedListener<Quest> listener);

    List<Quest> findAllForRepeatingQuest(RepeatingQuest repeatingQuest);

    long countAllForRepeatingQuest(RepeatingQuest repeatingQuest, LocalDate startDate, LocalDate endDate);

    List<Quest> findAllNonAllDayIncompleteForDateSync(LocalDate currentDate);

    Quest findByExternalSourceMappingId(String source, String sourceId);

    List<Quest> findAllUpcomingForRepeatingQuest(LocalDate startDate, RepeatingQuest repeatingQuest);

    long countAllCompletedWithPriorityForDate(int priority, LocalDate date);

    List<Quest> findAllForChallenge(Challenge challenge);

    Quest findByReminderId(String reminderId);

    void findAllIncompleteOrMostImportantForDate(LocalDate now, OnDatabaseChangedListener<Quest> listener);

    void setReminders(Quest quest, List<Reminder> reminders);

    void saveReminders(Quest quest, List<Reminder> reminders);

    void saveReminders(Quest quest, List<Reminder> reminders, boolean markUpdated);

    void setSubQuests(Quest quest, List<SubQuest> subQuests);

    void saveSubQuests(Quest quest, List<SubQuest> subQuests);

    void saveSubQuests(Quest quest, List<SubQuest> subQuests, boolean markUpdated);

    Date findNextUncompletedQuestEndDate(RepeatingQuest repeatingQuest);

    Date findNextUncompletedQuestEndDate(Challenge challenge);

    void findById(String questId, OnSingleDatabaseObjectChangedListener<Quest> listener);

    void findIncompleteNotRepeatingForChallenge(Challenge challenge, OnDatabaseChangedListener<Quest> listener);

    List<Quest> findIncompleteNotRepeatingNotForChallenge(String query, Challenge challenge);

    List<Quest> findAllCompleted(Challenge challenge);

    long countCompleted(Challenge challenge, LocalDate start, LocalDate end);

    long countCompleted(Challenge challenge);

    long countNotRepeating(Challenge challenge);

    long countNotDeleted(Challenge challenge);
}