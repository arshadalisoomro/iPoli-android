package io.ipoli.android.quest.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.google.gson.Gson;
import com.squareup.otto.Bus;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.ocpsoft.prettytime.shade.net.fortuna.ical4j.model.Recur;
import org.ocpsoft.prettytime.shade.net.fortuna.ical4j.model.WeekDay;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;
import butterknife.OnItemSelected;
import butterknife.Unbinder;
import io.ipoli.android.Constants;
import io.ipoli.android.R;
import io.ipoli.android.app.App;
import io.ipoli.android.app.utils.DateUtils;
import io.ipoli.android.quest.data.Recurrence;
import io.ipoli.android.quest.ui.formatters.DateFormatter;
import io.ipoli.android.quest.ui.formatters.FlexibleTimesFormatter;

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 6/13/16.
 */
public class RecurrencePickerFragment extends DialogFragment implements DatePickerFragment.OnDatePickedListener {

    private static final String TAG = "recurrence-picker-dialog";
    public static final int FREQUENCY_DAILY = 0;
    public static final int FREQUENCY_WEEKLY = 1;
    public static final int FREQUENCY_MONTHLY = 2;
    public static final int FLEXIBLE_FREQUENCY_WEEKLY = 0;
    public static final int FLEXIBLE_FREQUENCY_MONTHLY = 1;
    private static final String RECURRENCE = "recurrence";
    private static final String DISABLE_NO_REPEAT = "disable_no_repeat";

    private static final Map<WeekDay, Integer> weekDayToCheckBoxId = new HashMap<WeekDay, Integer>() {{
        put(WeekDay.MO, R.id.monday);
        put(WeekDay.TU, R.id.tuesday);
        put(WeekDay.WE, R.id.wednesday);
        put(WeekDay.TH, R.id.thursday);
        put(WeekDay.FR, R.id.friday);
        put(WeekDay.SA, R.id.saturday);
        put(WeekDay.SU, R.id.sunday);
    }};
    private List<String> frequencies = Arrays.asList("Daily", "Weekly", "Monthly");
    private List<String> flexibleFrequencies = Arrays.asList("Weekly", "Monthly");
    private boolean isFlexible = false;
    private View view;

    public interface OnRecurrencePickedListener {
        void onRecurrencePicked(Recurrence recurrence);
    }

    @Inject
    Bus eventBus;

    @BindView(R.id.recurrence_flexibility)
    Switch flexibleRecurrence;

    @BindView(R.id.recurrence_frequency)
    Spinner recurrenceFrequency;

    @BindView(R.id.day_of_week_container)
    ViewGroup dayOfWeekContainer;

    @BindView(R.id.day_of_month_container)
    ViewGroup dayOfMonthContainer;

    @BindView(R.id.day_of_month)
    Spinner dayOfMonth;

    @BindView(R.id.flexible_count_container)
    ViewGroup flexibleCountContainer;

    @BindView(R.id.recurrence_flexible_count)
    Spinner flexibleCount;

    @BindView(R.id.preferred_days_title)
    TextView preferredDays;

    @BindView(R.id.recurrence_times_a_day)
    Spinner timesADay;

    @BindView(R.id.recurrence_until)
    Button until;

    private boolean disableNoRepeat;

    private Unbinder unbinder;
    private OnRecurrencePickedListener recurrencePickerListener;

    private Recurrence recurrence;

    public static RecurrencePickerFragment newInstance(boolean disableNoRepeat, OnRecurrencePickedListener listener) {
        return newInstance(disableNoRepeat, listener, null);
    }

    public static RecurrencePickerFragment newInstance(boolean disableNoRepeat, OnRecurrencePickedListener listener, Recurrence recurrence) {
        RecurrencePickerFragment fragment = new RecurrencePickerFragment();
        fragment.recurrencePickerListener = listener;
        Bundle args = new Bundle();
        args.putBoolean(DISABLE_NO_REPEAT, disableNoRepeat);
        if (recurrence != null) {
            args.putString(RECURRENCE, new Gson().toJson(recurrence));
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.getAppComponent(getActivity()).inject(this);
        Bundle args = getArguments();
        String recurrenceJson = args.getString(RECURRENCE);
        if (!TextUtils.isEmpty(recurrenceJson)) {
            recurrence = new Gson().fromJson(recurrenceJson, Recurrence.class);
        } else {
            recurrence = Recurrence.create();
        }
        disableNoRepeat = args.getBoolean(DISABLE_NO_REPEAT, false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();
        view = inflater.inflate(R.layout.fragment_recurrence_picker, null);
        unbinder = ButterKnife.bind(this, view);
        isFlexible = recurrence.isFlexible();
        flexibleRecurrence.setChecked(isFlexible);
        initUI();

        builder.setView(view)
                .setIcon(R.drawable.logo)
                .setTitle(R.string.recurrence_picker_title)
                .setPositiveButton(getString(R.string.done), (dialog, which) -> {
                    onDialogDone(view);
                })
                .setNegativeButton(getString(R.string.cancel), null);

        if (!disableNoRepeat) {
            builder.setNeutralButton(getString(R.string.do_not_repeat), (dialog, which) -> {
                recurrencePickerListener.onRecurrencePicked(null);
            });
        }

        return builder.create();
    }

    private void initUI() {
        if (isFlexible) {
            dayOfMonthContainer.setVisibility(View.GONE);
            flexibleCountContainer.setVisibility(View.VISIBLE);
            preferredDays.setVisibility(View.VISIBLE);
            dayOfWeekContainer.setVisibility(View.VISIBLE);
            initFlexibleFrequencies();
            initFlexibleCount();
            initDays();
        } else {
            preferredDays.setVisibility(View.GONE);
            flexibleCountContainer.setVisibility(View.GONE);
            dayOfWeekContainer.setVisibility(View.GONE);
            dayOfMonthContainer.setVisibility(View.GONE);
            initFrequencies();
            initDaysOfMonth();
            initWeekDays();
        }
        initTimesADay();
        initUntilDate();
    }

    private void initWeekDays() {
        if (recurrence.getRecurrenceType() != Recurrence.RecurrenceType.WEEKLY) {
            return;
        }
        initDays();
    }

    private void initDays() {
        if (recurrence.getRrule() == null) {
            return;
        }
        try {
            Recur recur = new Recur(recurrence.getRrule());
            for (Object obj : recur.getDayList()) {
                WeekDay weekDay = (WeekDay) obj;
                ((CheckBox) view.findViewById(weekDayToCheckBoxId.get(weekDay))).setChecked(true);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }

    }

    private void initDaysOfMonth() {
        List<String> daysOfMonth = new ArrayList<>();
        for (int i = 1; i <= 31; i++) {
            daysOfMonth.add(String.valueOf(i));
        }
        dayOfMonth.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, daysOfMonth));
        if (recurrence.getRecurrenceType() == Recurrence.RecurrenceType.MONTHLY && recurrence.getRrule() != null) {
            setSelectedDayOfMonth();
        }
    }

    private void setSelectedDayOfMonth() {
        try {
            Recur recur = new Recur(recurrence.getRrule());
            int daySelected = !recur.getMonthDayList().isEmpty() ? (int) recur.getMonthDayList().get(0) : 1;
            dayOfMonth.setSelection(daySelected - 1, false);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    private void initUntilDate() {
        if (recurrence.getDtend() != null) {
            Date dtend = DateUtils.toStartOfDay(new LocalDate(recurrence.getDtend(), DateTimeZone.UTC));
            until.setText(DateUtils.isToday(dtend) ? getString(R.string.today) : DateFormatter.format(dtend));
            until.setTag(dtend);
        }
    }

    private void initFlexibleCount() {
        List<String> flexibleCountValues = new ArrayList<>();
        int selectedPosition = 0;
        if (recurrenceFrequency.getSelectedItemPosition() == FLEXIBLE_FREQUENCY_WEEKLY) {
            for (int i = Constants.MIN_FLEXIBLE_TIMES_A_WEEK_COUNT; i <= Constants.MAX_FLEXIBLE_TIMES_A_WEEK_COUNT; i++) {
                flexibleCountValues.add(FlexibleTimesFormatter.formatReadable(i));
                if (recurrence.getFlexibleCount() == i) {
                    selectedPosition = i - Constants.MIN_FLEXIBLE_TIMES_A_WEEK_COUNT;
                }
            }
        } else if (recurrenceFrequency.getSelectedItemPosition() == FLEXIBLE_FREQUENCY_MONTHLY) {
            for (int i = Constants.MIN_FLEXIBLE_TIMES_A_MONTH_COUNT; i <= Constants.MAX_FLEXIBLE_TIMES_A_MONTH_COUNT; i++) {
                flexibleCountValues.add(FlexibleTimesFormatter.formatReadable(i));
                if (recurrence.getFlexibleCount() == i) {
                    selectedPosition = i - Constants.MIN_FLEXIBLE_TIMES_A_MONTH_COUNT;
                }
            }
        }
        flexibleCount.setAdapter(new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_dropdown_item, flexibleCountValues));
        flexibleCount.setSelection(selectedPosition);
    }

    private void initFrequencies() {
        recurrenceFrequency.setAdapter(new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_dropdown_item, frequencies));
        recurrenceFrequency.setSelection(getNotFlexibleFrequencySelection(), false);
        recurrenceFrequency.setTag(recurrence.getRecurrenceType());

    }

    private void initFlexibleFrequencies() {
        recurrenceFrequency.setAdapter(new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_dropdown_item, flexibleFrequencies));
        recurrenceFrequency.setSelection(getFlexibleFrequencySelection(), false);
        recurrenceFrequency.setTag(recurrence.getRecurrenceType());

    }

    public int getFlexibleFrequencySelection() {
        int selection = 0;
        switch (recurrence.getRecurrenceType()) {
            case WEEKLY:
                return FLEXIBLE_FREQUENCY_WEEKLY;
            case MONTHLY:
                return FLEXIBLE_FREQUENCY_MONTHLY;
        }
        return selection;
    }

    private int getNotFlexibleFrequencySelection() {
        int selection = FREQUENCY_DAILY;
        switch (recurrence.getRecurrenceType()) {
            case DAILY:
                return FREQUENCY_DAILY;
            case WEEKLY:
                return FREQUENCY_WEEKLY;
            case MONTHLY:
                return FREQUENCY_MONTHLY;
        }
        return selection;
    }

    private void initTimesADay() {
        List<String> timesADayValues = new ArrayList<>();
        int selectedPosition = 0;
        for (int i = 1; i <= 7; i++) {
            timesADayValues.add(FlexibleTimesFormatter.formatReadable(i));
            if (recurrence.getTimesADay() == i) {
                selectedPosition = i - 1;
            }
        }
        timesADay.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, timesADayValues));
        timesADay.setSelection(selectedPosition);
    }

    @OnCheckedChanged(R.id.recurrence_flexibility)
    public void onFlexibilityChanged(boolean checked) {
        isFlexible = checked;
        initUI();
    }

    @OnItemSelected(R.id.recurrence_frequency)
    public void onFrequencySelected(AdapterView<?> parent, View view, int position, long id) {
        if (isFlexible) {
            switch (position) {
                case FLEXIBLE_FREQUENCY_WEEKLY:
                    recurrenceFrequency.setTag(Recurrence.RecurrenceType.WEEKLY);
                    break;
                case FLEXIBLE_FREQUENCY_MONTHLY:
                    recurrenceFrequency.setTag(Recurrence.RecurrenceType.MONTHLY);
                    break;
            }
            initFlexibleCount();
        } else {
            switch (position) {
                case FREQUENCY_DAILY:
                    recurrenceFrequency.setTag(Recurrence.RecurrenceType.DAILY);
                    dayOfWeekContainer.setVisibility(View.GONE);
                    dayOfMonthContainer.setVisibility(View.GONE);
                    break;
                case FREQUENCY_WEEKLY:
                    recurrenceFrequency.setTag(Recurrence.RecurrenceType.WEEKLY);
                    dayOfWeekContainer.setVisibility(View.VISIBLE);
                    dayOfMonthContainer.setVisibility(View.GONE);
                    break;
                case FREQUENCY_MONTHLY:
                    recurrenceFrequency.setTag(Recurrence.RecurrenceType.MONTHLY);
                    dayOfWeekContainer.setVisibility(View.GONE);
                    dayOfMonthContainer.setVisibility(View.VISIBLE);
                    break;
            }
        }
    }

    @OnClick(R.id.recurrence_until)
    public void onUntilTapped() {
        if (until.getTag() != null) {
            DatePickerFragment.newInstance((Date) until.getTag(), true, this).show(getFragmentManager());
        } else {
            DatePickerFragment.newInstance(true, this).show(getFragmentManager());
        }
    }

    @Override
    public void onDatePicked(Date date) {
        String text = getString(R.string.end_of_time);
        if (date != null) {
            text = DateUtils.isToday(date) ? getString(R.string.today) : DateFormatter.format(date);
        }
        until.setText(text);
        until.setTag(date);
    }

    private void onDialogDone(View view) {
        Recurrence.RecurrenceType recurrenceType = (Recurrence.RecurrenceType) recurrenceFrequency.getTag();
        if (isFlexible) {
            setFlexibleValues(recurrenceType);
        } else {
            setRrule(recurrenceType);
            recurrence.setFlexibleCount(0);
        }

        recurrence.setTimesADay(FlexibleTimesFormatter.parse(timesADay.getSelectedItem().toString()));

        if (until.getTag() != null) {
            Date dtEnd = DateUtils.toStartOfDayUTC(new LocalDate((Date) until.getTag()));
            recurrence.setDtend(dtEnd);
        } else {
            recurrence.setDtend(null);
        }
        recurrencePickerListener.onRecurrencePicked(recurrence);
    }

    private void setFlexibleValues(Recurrence.RecurrenceType recurrenceType) {
        Recur recur = new Recur(Recur.WEEKLY, null);
        switch (recurrenceType) {
            case WEEKLY:
                recur.setFrequency(Recur.WEEKLY);
                recurrence.setType(Recurrence.RecurrenceType.WEEKLY);
                break;
            case MONTHLY:
                recur.setFrequency(Recur.MONTHLY);
                recurrence.setType(Recurrence.RecurrenceType.MONTHLY);
                break;
        }

        recurrence.setFlexibleCount(FlexibleTimesFormatter.parse(flexibleCount.getSelectedItem().toString()));

        for (Map.Entry<WeekDay, Integer> entry : weekDayToCheckBoxId.entrySet()) {
            if (((CheckBox) view.findViewById(entry.getValue())).isChecked()) {
                recur.getDayList().add(entry.getKey());
            }
        }
        recurrence.setRrule(recur.toString());
    }

    private void setRrule(Recurrence.RecurrenceType recurrenceType) {
        Recur recur = new Recur(Recur.DAILY, null);
        switch (recurrenceType) {
            case DAILY:
                recur.getDayList().add(WeekDay.MO);
                recur.getDayList().add(WeekDay.TU);
                recur.getDayList().add(WeekDay.WE);
                recur.getDayList().add(WeekDay.TH);
                recur.getDayList().add(WeekDay.FR);
                recur.getDayList().add(WeekDay.SA);
                recur.getDayList().add(WeekDay.SU);
                recurrence.setType(Recurrence.RecurrenceType.DAILY);
                recur.setFrequency(Recur.WEEKLY);
                break;
            case WEEKLY:
                for (Map.Entry<WeekDay, Integer> entry : weekDayToCheckBoxId.entrySet()) {
                    if (((CheckBox) view.findViewById(entry.getValue())).isChecked()) {
                        recur.getDayList().add(entry.getKey());
                    }
                }
                recur.setFrequency(Recur.WEEKLY);
                recurrence.setType(Recurrence.RecurrenceType.WEEKLY);
                break;
            case MONTHLY:
                recur.setFrequency(Recur.MONTHLY);
                recur.getMonthDayList().add(dayOfMonth.getSelectedItemPosition() + 1);
                recurrence.setType(Recurrence.RecurrenceType.MONTHLY);
                break;
        }
        recurrence.setRrule(recur.toString());
    }

    @Override
    public void onDestroyView() {
        unbinder.unbind();
        super.onDestroyView();
    }

    public void show(FragmentManager fragmentManager) {
        show(fragmentManager, TAG);
    }
}
