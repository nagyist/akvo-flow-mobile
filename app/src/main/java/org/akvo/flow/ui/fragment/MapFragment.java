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
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.overlay.Icon;
import com.mapbox.mapboxsdk.overlay.Marker;
import com.mapbox.mapboxsdk.tileprovider.tilesource.MapboxTileLayer;
import com.mapbox.mapboxsdk.views.InfoWindow;
import com.mapbox.mapboxsdk.views.MapView;

import org.akvo.flow.activity.RecordActivity;
import org.akvo.flow.activity.RecordListActivity;
import org.akvo.flow.async.loader.SurveyedLocaleLoader;
import org.akvo.flow.dao.SurveyDbAdapter;
import org.akvo.flow.domain.SurveyedLocale;
import org.akvo.flow.util.ConstantUtil;

import java.util.ArrayList;
import java.util.List;

public class MapFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = MapFragment.class.getSimpleName();

    private long mSurveyGroupId;
    private String mRecordId; // If set, load a single record
    private SurveyDbAdapter mDatabase;
    private RecordListListener mListener;

    private List<SurveyedLocale> mItems;

    private boolean mSingleRecord = false;

    private MapView mMapView;

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
        mMapView = new MapView(getActivity());
        mMapView.setTileSource(new MapboxTileLayer("examples.map-i87786ca"));
        mMapView.setMinZoomLevel(mMapView.getTileProvider().getMinimumZoomLevel());
        mMapView.setMaxZoomLevel(mMapView.getTileProvider().getMaximumZoomLevel());
        mMapView.setCenter(mMapView.getTileProvider().getCenterCoordinate());
        mMapView.setZoom(0);
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
                    Marker m = new Marker(mMapView, record.getDisplayName(getActivity()), record.getId(), new LatLng(record.getLatitude(), record.getLongitude()));
                    m.setIcon(new Icon(getActivity(), Icon.Size.SMALL, "marker-stroked", "FF0000"));
                    mMapView.addMarker(m);
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
            //mOverlays.clear();
            do {
                SurveyedLocale item = SurveyDbAdapter.getSurveyedLocale(cursor);
                if (item.getLatitude() == null || item.getLongitude() == null) {
                    continue;
                }

                mItems.add(item);

                Marker m = new CustomMarker(mMapView, item.getDisplayName(getActivity()), item.getId(),
                        new LatLng(item.getLatitude(), item.getLongitude()), item.getId());
                m.setIcon(new Icon(getActivity(), Icon.Size.SMALL, "marker-stroked", "FF0000"));
                mMapView.addMarker(m);
            } while (cursor.moveToNext());
        }
        cursor.close();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    public class CustomMarker extends Marker {
        String mRecordId;

        public CustomMarker(MapView mv, String aTitle, String aDescription, LatLng aLatLng, String recordId){
            super(mv, aTitle, aDescription, aLatLng);
            mRecordId = recordId;
        }

        @Override
        protected InfoWindow createTooltip(MapView mv) {
            InfoWindow w = super.createTooltip(mv);
            w.getView().setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    mListener.onRecordSelected(mRecordId);
                    return false;
                }
            });
            return w;
        }
    }


}
