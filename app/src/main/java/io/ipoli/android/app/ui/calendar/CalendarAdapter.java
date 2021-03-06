package io.ipoli.android.app.ui.calendar;

import android.view.View;
import android.view.ViewGroup;

import java.util.List;

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 2/20/16.
 */
public interface CalendarAdapter<E extends CalendarEvent> {
    List<E> getEvents();

    View getView(ViewGroup parent, int position);

    void onStartTimeUpdated(E calendarEvent, int oldStartTime);

    void notifyDataSetChanged();

    void updateEvents(List<E> calendarEvents);

    void onDragStarted(View draggedView);

    void onDragEnded(View draggedView);

    void removeEvent(E calendarEvent);
}
