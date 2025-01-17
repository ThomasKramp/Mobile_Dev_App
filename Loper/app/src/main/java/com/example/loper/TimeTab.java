package com.example.loper;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.fragment.app.Fragment;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link TimeTab#newInstance} factory method to
 * create an instance of this fragment.
 */
public class TimeTab extends Fragment {

    // region Default Frame waarden
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public TimeTab() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment TimeTab.
     */
    // TODO: Rename and change types and number of parameters
    public static TimeTab newInstance(String param1, String param2) {
        TimeTab fragment = new TimeTab();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }
    // endregion

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_time_tab, container, false);
        return rootView;
    }

    public float getTime() {
        float totalMinutes = 0;
        EditText mEditTextHours = getView().findViewById(R.id.timeInHours_input);
        EditText mEditTextMinutes = getView().findViewById(R.id.timeInMinutes_input);
        String hours = mEditTextHours.getText().toString();
        String minutes = mEditTextMinutes.getText().toString();
        if(!hours.equals(""))
            totalMinutes += Integer.parseInt(hours) * 60;
        if(!minutes.equals(""))
            totalMinutes += Integer.parseInt(minutes);
        return totalMinutes;
    }
}