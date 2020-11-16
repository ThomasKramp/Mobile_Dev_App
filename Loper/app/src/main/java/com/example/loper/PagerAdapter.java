package com.example.loper;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

public class PagerAdapter extends FragmentStatePagerAdapter {

    private DistanceTab mDistanceTab;
    private TimeTab mTimeTab;
    public PagerAdapter(@NonNull FragmentManager fm, DistanceTab distanceTab, TimeTab timeTab) {
        super(fm, 2);
        mDistanceTab = distanceTab;
        mTimeTab = timeTab;
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0: return mDistanceTab;
            case 1: return mTimeTab;
            default: return null;
        }
    }

    @Override
    public int getCount() {
        return 2;
    }
}
