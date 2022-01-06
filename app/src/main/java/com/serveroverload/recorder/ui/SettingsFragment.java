package com.serveroverload.recorder.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.serveroverload.recorder.R;
import com.serveroverload.recorder.util.PreferenceManager;


public class SettingsFragment extends Fragment implements View.OnClickListener {

    private View rootView;

    Button startFreqButton;
    Button endFreqButton;
    Button saveButton;
    Button exitButton;

    EditText startFreqEditText;
    EditText endFreqEditText;

    int before_start_freq = 12000;
    int before_end_freq = 16000;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.setting_fragment, container,
                false);

        startFreqButton = (Button) rootView.findViewById(R.id.start_button);
        startFreqButton.setOnClickListener(this);
        endFreqButton = (Button) rootView.findViewById(R.id.end_button);
        endFreqButton.setOnClickListener(this);

        before_start_freq = PreferenceManager.getInt(getActivity(), "start_freq");
        before_end_freq = PreferenceManager.getInt(getActivity(), "end_freq");

        startFreqEditText = (EditText) rootView.findViewById(R.id.start_frequency);
        startFreqEditText.setText(Integer.toString(before_start_freq));
        endFreqEditText = (EditText) rootView.findViewById(R.id.end_frequency);
        endFreqEditText.setText(Integer.toString(before_end_freq));

        saveButton = (Button) rootView.findViewById(R.id.save_button);
        saveButton.setOnClickListener(this);
        exitButton = (Button) rootView.findViewById(R.id.exit_button);
        exitButton.setOnClickListener(this);

        return rootView;
    }

    @Override
    public void onClick(View arg0) {

        if (arg0.getId() == R.id.start_button) {
            int current_start = PreferenceManager.getInt(getActivity(),"start_freq");
            startFreqEditText.setText(Integer.toString(current_start));
        } else if (arg0.getId() == R.id.end_button) {
            int current_end = PreferenceManager.getInt(getActivity(), "end_freq");
            endFreqEditText.setText(Integer.toString(current_end));
        } else if (arg0.getId() == R.id.save_button) {
            int new_start = before_start_freq;
            int new_end = before_end_freq;
            String start_str = startFreqEditText.getText().toString();
            String end_str = endFreqEditText.getText().toString();
            if (start_str != null)
                new_start = Integer.parseInt(start_str);
            if (end_str != null)
                new_end = Integer.parseInt(end_str);

            PreferenceManager.setInt(getActivity(), "start_freq", new_start);
            PreferenceManager.setInt(getActivity(), "end_freq", new_end);
            Toast.makeText(getActivity(), "save_button",
                    Toast.LENGTH_SHORT).show();
            closeFragment();
        } else if (arg0.getId() == R.id.exit_button) {
            Log.d("SettingFragment", "exit_button");
            Toast.makeText(getActivity(), "exit_button",
                    Toast.LENGTH_SHORT).show();
            closeFragment();
        }
    }

    private void closeFragment() {
        FragmentManager fragmentManager = getActivity()
                .getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager
                .beginTransaction();
        fragmentTransaction.remove(SettingsFragment.this).commit();
        fragmentManager.popBackStack();
    }


}