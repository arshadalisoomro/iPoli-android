package io.ipoli.android.app.ui.calendar;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.ipoli.android.R;
import io.ipoli.android.app.utils.Time;
import io.ipoli.android.app.utils.ViewUtils;

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 2/6/16.
 */
public class CalendarDayView extends FrameLayout {

    public static final int HOURS_PER_SCREEN = 5;
    public static final int HOURS_IN_A_DAY = 24;
    public static final int TOP_PADDING_HOURS = 1;

    private float minuteHeight;
    private RelativeLayout eventsContainer;
    private int hourHeight;
    private View timeLine;

    private BaseCalendarAdapter adapter;
    private Map<View, CalendarEvent> eventViewToCalendarEvent;
    private RecyclerView hourCellContainer;
    private RelativeLayout timelineContainer;

    public CalendarDayView(Context context) {
        super(context);
        if (!isInEditMode()) {
            initUI(context);
        }
    }

    public CalendarDayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (!isInEditMode()) {
            initUI(context);
        }
    }

    public CalendarDayView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (!isInEditMode()) {
            initUI(context);
        }
    }

    public CalendarDayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (!isInEditMode()) {
            initUI(context);
        }
    }

    private void initUI(Context context) {

        eventViewToCalendarEvent = new HashMap<>();
        hourHeight = getScreenHeight(context) / HOURS_PER_SCREEN;
        minuteHeight = hourHeight / 60.0f;

        LayoutInflater inflater = LayoutInflater.from(context);

        hourCellContainer = initHourCellsContainer(context);
        hourCellContainer.addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                int yOffset = hourCellContainer.computeVerticalScrollOffset();
                timelineContainer.scrollTo(0, yOffset);
                eventsContainer.scrollTo(0, yOffset);
                super.onScrolled(recyclerView, dx, dy);
            }
        });
        addView(hourCellContainer);
        timelineContainer = initTimeLineContainer(context, inflater);
        addView(timelineContainer);
        eventsContainer = initEventsContainer(context);
        addView(eventsContainer);
    }

    @NonNull
    private RecyclerView initHourCellsContainer(Context context) {

        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);

        List<String> hours = new ArrayList<>();
        for (int i = 0; i < HOURS_IN_A_DAY; i++) {
            hours.add(String.format(Locale.getDefault(), "%02d:00", i));
        }
        recyclerView.setAdapter(new HourCellAdapter(hours));
        recyclerView.setHasFixedSize(true);
        return recyclerView;
    }

    private RelativeLayout initEventsContainer(Context context) {
        RelativeLayout eventsContainer = new RelativeLayout(context);
        eventsContainer.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return eventsContainer;
    }

    @NonNull
    private RelativeLayout initTimeLineContainer(Context context, LayoutInflater inflater) {
        RelativeLayout timeRL = new RelativeLayout(context);
        timeRL.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        timeLine = inflater.inflate(R.layout.calendar_time_line, timeRL, false);

        RelativeLayout.LayoutParams lPTime = (RelativeLayout.LayoutParams) timeLine.getLayoutParams();
        lPTime.topMargin = getCurrentTimeYPosition();
        timeRL.addView(timeLine, lPTime);

        return timeRL;
    }

    private int getScreenHeight(Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);
        return metrics.heightPixels;
    }

    public void setAdapter(BaseCalendarAdapter<? extends CalendarEvent> adapter) {
        this.adapter = adapter;
        adapter.setCalendarDayView(this);
        adapter.notifyDataSetChanged();
    }

    <E extends CalendarEvent> void addEvent(E calendarEvent, int position) {
        final View eventView = adapter.getView(eventsContainer, position);
        RelativeLayout.LayoutParams qlp = (RelativeLayout.LayoutParams) eventView.getLayoutParams();
        qlp.topMargin = getYPositionFor(calendarEvent.getStartMinute());
        qlp.height = getHeightFor(calendarEvent.getDuration());
        eventsContainer.addView(eventView, qlp);
        eventViewToCalendarEvent.put(eventView, calendarEvent);
    }

    public void onMinuteChanged() {

        RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams) timeLine.getLayoutParams();
        p.topMargin = getCurrentTimeYPosition();
        timeLine.setLayoutParams(p);
        // @TODO consider date change
    }

    int getHoursFor(float y) {
        float h = getRelativeY((int) y) / hourHeight;
        return Math.min(Math.round(h), 23);
    }

    int getMinutesFor(float y, int rangeLength) {
        int minutes = (int) ((getRelativeY((int) y) % hourHeight) / minuteHeight);
        minutes = Math.max(0, minutes);
        List<Integer> bounds = new ArrayList<>();
        int rangeStart = 0;
        for (int min = 0; min < 60; min++) {

            if (min % rangeLength == 0) {
                rangeStart = min;
            }
            bounds.add(rangeStart);
        }
        return bounds.get(minutes);
    }

    private int getYPositionFor(int hours, int minutes) {
        int y = hours * hourHeight;
        y += getMinutesHeight(minutes);
        return y;
    }

    private int getYPositionFor(int minutesAfterMidnight) {
        Time time = Time.of(minutesAfterMidnight);
        return getYPositionFor(time.getHours(), time.getMinutes());
    }

    int getHeightFor(int duration) {
        return (int) getMinutesHeight(duration);
    }

    private float getMinutesHeight(int minutes) {
        return minuteHeight * minutes;
    }

    private int getCurrentTimeYPosition() {
        return getCurrentTimeYPosition(0);
    }

    private int getCurrentTimeYPosition(int hourOffset) {
        Calendar c = Calendar.getInstance();
        int hour = Math.max(0, c.get(Calendar.HOUR_OF_DAY) - hourOffset);
        int minutes = c.get(Calendar.MINUTE);
        return getYPositionFor(hour, minutes);
    }

    private int getRelativeY(int y) {
        int offsets[] = new int[2];
        getLocationOnScreen(offsets);
        return getRelativeY(y, offsets[1]);
    }

    private int getRelativeY(int y, int yOffset) {
        return Math.max(0, hourCellContainer.computeVerticalScrollOffset() + y - yOffset);
    }

    public void scrollToNow() {
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        hour = Math.max(0, hour - TOP_PADDING_HOURS);
        hourCellContainer.scrollToPosition(hour);
    }

    public void smoothScrollToTime(final Time time) {

        int currentVisibleHour = ((LinearLayoutManager) hourCellContainer.getLayoutManager()).findFirstVisibleItemPosition();
        int hour = time.getHours();
        if (currentVisibleHour < hour) {
            hour = Math.min(HOURS_IN_A_DAY - 1, Math.max(0, hour + TOP_PADDING_HOURS));
        } else {
            hour = Math.min(HOURS_IN_A_DAY - 1, Math.max(0, hour - TOP_PADDING_HOURS));
        }
        hourCellContainer.smoothScrollToPosition(hour);
    }

    public void removeAllEvents() {
        eventsContainer.removeAllViews();
        eventViewToCalendarEvent.clear();
    }

    DragStrategy getEditViewDragStrategy(final View dragView) {
        return new DragStrategy() {
            public boolean hasDropped;
            public int initialTouchHeight;

            @Override
            public void onDragStarted(DragEvent event) {
                hasDropped = false;
                adapter.onDragStarted(dragView);
            }

            @Override
            public void onDragEntered(DragEvent event) {
                int[] dragViewLoc = new int[2];
                dragView.getLocationOnScreen(dragViewLoc);
                int[] calendarViewLoc = new int[2];
                getLocationOnScreen(calendarViewLoc);
                int dragViewTop = dragViewLoc[1] - calendarViewLoc[1];
                initialTouchHeight = (int) (event.getY() - getTop() - dragViewTop);
            }

            @Override
            public void onDragMoved(DragEvent event) {
                RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) dragView.getLayoutParams();
                layoutParams.topMargin = getRelativeY((int) (event.getY() - initialTouchHeight), getTop());
                dragView.setLayoutParams(layoutParams);
            }

            @Override
            public void onDragDropped(DragEvent event) {
                hasDropped = true;
                RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) dragView.getLayoutParams();
                int h = getHoursFor(ViewUtils.getViewRawTop(dragView));
                int m = getMinutesFor(ViewUtils.getViewRawTop(dragView), 5);
                layoutParams.topMargin = getYPositionFor(h, m);
                dragView.setLayoutParams(layoutParams);
                CalendarEvent calendarEvent = eventViewToCalendarEvent.get(dragView);
                int oldStartTime = calendarEvent.getStartMinute();
                calendarEvent.setStartMinute(Time.at(h, m).toMinutesAfterMidnight());
                adapter.onStartTimeUpdated(calendarEvent, oldStartTime);
                adapter.onDragEnded(dragView);
            }

            @Override
            public void onDragExited(DragEvent event) {
            }

            @Override
            public void onDragEnded() {
                if (!hasDropped) {
                    RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) dragView.getLayoutParams();
                    CalendarEvent calendarEvent = eventViewToCalendarEvent.get(dragView);
                    layoutParams.topMargin = getYPositionFor(calendarEvent.getStartMinute());
                    dragView.setLayoutParams(layoutParams);
                    adapter.onDragEnded(dragView);
                }
            }
        };
    }

    public void showTimeLine() {
        timeLine.setVisibility(VISIBLE);
    }

    public void hideTimeLine() {
        timeLine.setVisibility(GONE);
    }

    public class HourCellAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<String> hours;

        public HourCellAdapter(List<String> hours) {
            this.hours = hours;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                          int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view = inflater.inflate(R.layout.calendar_hour_cell, parent, false);
            ViewGroup.LayoutParams hcp = view.getLayoutParams();
            hcp.height = hourHeight;
            view.setLayoutParams(hcp);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
            ViewHolder vh = (ViewHolder) holder;
            String text = hours.get(vh.getAdapterPosition());
            vh.timeLabel.setText(text);

            vh.itemView.setOnTouchListener(new View.OnTouchListener() {
                public int startY;
                public int startX;

                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        startX = (int) event.getX();
                        startY = (int) event.getY();
                    }

                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        int endX = (int) event.getX();
                        int endY = (int) event.getY();
                        int dX = Math.abs(endX - startX);
                        int dY = Math.abs(endY - startY);


                        if (Math.sqrt(Math.pow(dX, 2) + Math.pow(dY, 2)) <= ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                            Log.d("OnHourCellClick", " " + getMinutesFor(event.getRawY(), 15));
                        }
                    }
                    return true;
                }
            });
        }

        @Override
        public int getItemCount() {
            return hours.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {

            @BindView(R.id.time_label)
            TextView timeLabel;

            public ViewHolder(View v) {
                super(v);
                ButterKnife.bind(this, v);
            }
        }
    }
}