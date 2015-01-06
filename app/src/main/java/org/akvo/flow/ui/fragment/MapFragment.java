/*
 *  Copyright (C) 2013-2014 Stichting Akvo (Akvo Foundation)
 *
 *  This file is part of Akvo FLOW.
 *
 *  Akvo FLOW is free software: you can redistribute it and modify it under the terms of
 *  the GNU Affero General Public License (AGPL) as published by the Free Software Foundation,
 *  either version 3 of the License or any later version.
 *
 *  Akvo FLOW is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Affero General Public License included below for more details.
 *
 *  The full license text can also be seen at <http://www.gnu.org/licenses/agpl.html>.
 */

package org.akvo.flow.ui.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.akvo.flow.activity.RecordActivity;
import org.akvo.flow.activity.RecordListActivity;
import org.akvo.flow.async.loader.SurveyedLocaleLoader;
import org.akvo.flow.dao.SurveyDbAdapter;
import org.akvo.flow.domain.SurveyedLocale;
import org.akvo.flow.util.ConstantUtil;
import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.api.IMapView;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.ResourceProxyImpl;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.OverlayItem;

import java.util.ArrayList;
import java.util.List;

public class MapFragment extends Fragment implements LoaderCallbacks<Cursor> {
    private static final String TAG = MapFragment.class.getSimpleName();

    private long mSurveyGroupId;
    private String mRecordId; // If set, load a single record
    private SurveyDbAdapter mDatabase;
    private RecordListListener mListener;

    private List<SurveyedLocale> mItems;

    private boolean mSingleRecord = false;

    private MapView mMapView;
    private MapOverlay mOverlays;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mItems = new ArrayList<SurveyedLocale>();
        Bundle args = getArguments();
        if (args.containsKey(RecordActivity.EXTRA_RECORD_ID)) {
            // Single record mode.
            mSingleRecord = true;
            mRecordId = args.getString(RecordActivity.EXTRA_RECORD_ID);
        } else {
            mSingleRecord = false;
            mSurveyGroupId = args.getLong(RecordListActivity.EXTRA_SURVEY_GROUP_ID);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mListener = (RecordListListener)activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement SurveyedLocalesFragmentListener");
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mDatabase = new SurveyDbAdapter(getActivity());

        // Initialize overlays
        List<Overlay> mapOverlays = mMapView.getOverlays();
        Drawable drawable = getResources().getDrawable(android.R.drawable.star_big_on);
        mOverlays = new MapOverlay(drawable, getActivity());
        mapOverlays.add(mOverlays);
    }

    /**
     * Center the map in the given record's coordinates. If no record is provided,
     * the user's location will be used.
     *
     * @param record
     */
    private void centerMap(SurveyedLocale record) {
        if (mMapView == null) {
            return; // Not ready yet
        }

        // TODO
    }

    @Override
    public void onResume() {
        super.onResume();
        mDatabase.open();
        if (mItems.isEmpty()) {
            // Make sure we only fetch the data and center the map once
            refresh();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mDatabase.close();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
        ResourceProxyImpl resourceProxy = new ResourceProxyImpl(inflater.getContext().getApplicationContext());
        mMapView = new MapView(inflater.getContext(), 256, resourceProxy);
        mMapView.setBuiltInZoomControls(true);
        mMapView.setMultiTouchControls(true);
        return mMapView;
    }

    /**
     * Ideally, we should build a ContentProvider, so this notifications are handled
     * automatically, and the loaders restarted without this explicit dependency.
     */
    public void refresh() {
        if (isResumed()) {
            if (mSingleRecord) {
                // Just get it from the DB
                SurveyedLocale record = mDatabase.getSurveyedLocale(mRecordId);
                if (mMapView != null && record != null && record.getLatitude() != null
                        && record.getLongitude() != null) {
                    mItems.clear();
                    mItems.add(record);
                    GeoPoint point = new GeoPoint(record.getLatitude(), record.getLongitude());
                    OverlayItem overlayitem = new OverlayItem(record.getDisplayName(getActivity()), record.getId(), point);
                    mOverlays.addOverlay(overlayitem);
                    centerMap(record);
                }
            } else {
                getLoaderManager().restartLoader(0, null, this);
            }
        }
    }

    // ==================================== //
    // ========= Loader Callbacks ========= //
    // ==================================== //

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new SurveyedLocaleLoader(getActivity(), mDatabase, mSurveyGroupId,
                ConstantUtil.ORDER_BY_NONE);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (cursor == null) {
            Log.e(TAG, "onFinished() - Loader returned no data");
            return;
        }

        if (cursor.moveToFirst()) {
            mItems.clear();
            mOverlays.clear();
            do {
                SurveyedLocale item = SurveyDbAdapter.getSurveyedLocale(cursor);
                if (item.getLatitude() == null || item.getLongitude() == null) {
                    continue;
                }

                mItems.add(item);
                GeoPoint point = new GeoPoint(item.getLatitude(), item.getLongitude());
                OverlayItem overlayitem = new OverlayItem(item.getDisplayName(getActivity()), item.getId(), point);
                mOverlays.addOverlay(overlayitem);
            } while (cursor.moveToNext());
        }
        cursor.close();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    class MapOverlay extends ItemizedOverlay {
        private Context mContext;
        private ArrayList<OverlayItem> mOverlays = new ArrayList<>();

        MapOverlay(Drawable marker, Context context) {
            super(marker, new DefaultResourceProxyImpl(context));
            mContext = context;
        }

        @Override
        protected OverlayItem createItem(int i) {
            return mOverlays.get(i);
        }
        @Override
        public int size() {
            return mOverlays.size();
        }

        @Override
        public boolean onSnapToItem(int x, int y, Point snapPoint, IMapView mapView) {
            return false;
        }

        public void addOverlay(OverlayItem overlay) {
            mOverlays.add(overlay);
            populate();
        }

        public void clear() {
            mOverlays.clear();
            populate();
        }

        @Override
        protected boolean onTap(int index) {
            OverlayItem item = mOverlays.get(index);
            final SurveyedLocale locale = mItems.get(index);
            AlertDialog.Builder dialog = new AlertDialog.Builder(mContext);
            dialog.setTitle(item.getTitle());
            dialog.setMessage(item.getSnippet());
            if (!mSingleRecord) {
                dialog.setPositiveButton("Open", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mListener.onRecordSelected(locale.getId());
                    }
                });
                dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
            }
            dialog.show();
            return true;
        }

    }

}
