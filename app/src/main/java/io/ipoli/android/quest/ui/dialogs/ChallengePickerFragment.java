package io.ipoli.android.quest.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;

import com.squareup.otto.Bus;

import java.util.List;

import javax.inject.Inject;

import io.ipoli.android.R;
import io.ipoli.android.app.App;
import io.ipoli.android.challenge.data.Challenge;
import io.ipoli.android.challenge.persistence.ChallengePersistenceService;
import io.ipoli.android.challenge.persistence.RealmChallengePersistenceService;
import io.realm.Realm;

/**
 * Created by Polina Zhelyazkova <polina@ipoli.io>
 * on 6/14/16.
 */
public class ChallengePickerFragment extends DialogFragment {
    private static final String TAG = "challenge-picker-dialog";
    private static final String CHALLENGE_ID = "challenge_id";

    @Inject
    Bus eventBus;

    private Realm realm;
    private ChallengePersistenceService challengePersistenceService;

    private String challengeId;
    private int selectedChallengeIndex;

    private OnChallengePickedListener challengePickedListener;

    public interface OnChallengePickedListener {
        void onChallengePicked(String challengeId);
    }

    public static ChallengePickerFragment newInstance(String challengeId, OnChallengePickedListener challengePickedListener) {
        ChallengePickerFragment fragment = new ChallengePickerFragment();
        Bundle args = new Bundle();
        args.putString(CHALLENGE_ID, challengeId);
        fragment.setArguments(args);
        fragment.challengePickedListener = challengePickedListener;
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.getAppComponent(getContext()).inject(this);

        if (getArguments() != null) {
            challengeId = getArguments().getString(CHALLENGE_ID);
        }

        realm = Realm.getDefaultInstance();
        challengePersistenceService = new RealmChallengePersistenceService(eventBus, realm);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        selectedChallengeIndex = -1;
        List<Challenge> challenges = challengePersistenceService.findAllNotCompleted();
        String[] names = new String[challenges.size()];
        for (int i = 0; i < challenges.size(); i++) {
            names[i] = challenges.get(i).getName();
            if(challenges.get(i).getId().equals(challengeId)) {
                selectedChallengeIndex = i;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setIcon(R.drawable.logo)
                .setTitle(R.string.quest_pick_challenge)
                .setSingleChoiceItems(names, selectedChallengeIndex, (dialog, which) -> {
                    selectedChallengeIndex = which;
                })
                .setPositiveButton(R.string.help_dialog_ok, (dialog, which) -> {
                    if(selectedChallengeIndex >= 0) {
                        challengePickedListener.onChallengePicked(challenges.get(selectedChallengeIndex).getId());
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {

                })
                .setNeutralButton(R.string.none, (dialogInterface, i) -> {
                    challengePickedListener.onChallengePicked(null);
                });
        return builder.create();

    }

    @Override
    public void onDestroyView() {
        realm.close();
        super.onDestroyView();
    }

    public void show(FragmentManager fragmentManager) {
        show(fragmentManager, TAG);
    }

}
