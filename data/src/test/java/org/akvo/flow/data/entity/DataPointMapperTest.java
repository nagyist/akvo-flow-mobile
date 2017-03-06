/*
 * Copyright (C) 2017 Stichting Akvo (Akvo Foundation)
 *
 * This file is part of Akvo Flow.
 *
 * Akvo Flow is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Akvo Flow is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Akvo Flow.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.akvo.flow.data.entity;

import android.database.Cursor;

import org.akvo.flow.database.SurveyDbAdapter;
import org.akvo.flow.database.SurveyInstanceColumns;
import org.akvo.flow.domain.entity.DataPoint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

@RunWith(MockitoJUnitRunner.class)
public class DataPointMapperTest {

    public static final String MOCK_ID = "123";
    public static final long MOCK_SURVEY_GROUP_ID = 1L;
    public static final long MOCK_DATE = 1234567L;
    public static final String MOCK_NAME = "name";
    public static final double MOCK_LATITUDE = 40d;
    public static final double MOCK_LONGITUDE = 2d;
    public static final int MOCK_STATUS_INDEX = 7;
    public static final int MOCK_STATUS = 1;
    @Mock
    Cursor mockCursor;

    @Test
    public void getDataPoints_shouldReturnEmptyWhenCursorNull() {
        DataPointMapper dataPointMapper = new DataPointMapper();

        List<DataPoint> dataPoints = dataPointMapper.getDataPoints(null);

        assertEquals(0, dataPoints.size());
    }

    @Test
    public void getDataPoints_shouldReturnItemsWhenCursorNonNull() {
        DataPointMapper dataPointMapper = new DataPointMapper();
        provideMockCursorWithOneItem();

        List<DataPoint> dataPoints = dataPointMapper.getDataPoints(mockCursor);

        assertEquals(1, dataPoints.size());
        DataPoint dataPoint = dataPoints.get(0);
        assertEquals(MOCK_ID, dataPoint.getId());
        assertEquals(MOCK_SURVEY_GROUP_ID, dataPoint.getSurveyGroupId());
        assertEquals(MOCK_DATE, dataPoint.getLastModified());
        assertEquals(MOCK_NAME, dataPoint.getName());
        assertEquals(MOCK_LATITUDE, dataPoint.getLatitude());
        assertEquals(MOCK_LONGITUDE, dataPoint.getLongitude());
        assertEquals(MOCK_STATUS, dataPoint.getStatus());
    }

    private void provideMockCursorWithOneItem() {
        doNothing().when(mockCursor).close();
        doReturn(true).when(mockCursor).moveToFirst();
        doReturn(false).when(mockCursor).moveToNext();
        doReturn(MOCK_ID).when(mockCursor).getString(SurveyDbAdapter.RecordQuery.RECORD_ID);
        doReturn(MOCK_SURVEY_GROUP_ID).when(mockCursor).getLong(SurveyDbAdapter.RecordQuery.SURVEY_GROUP_ID);
        doReturn(MOCK_DATE).when(mockCursor).getLong(SurveyDbAdapter.RecordQuery.LAST_MODIFIED);
        doReturn(MOCK_NAME).when(mockCursor).getString(SurveyDbAdapter.RecordQuery.NAME);
        doReturn(false).when(mockCursor).isNull(anyInt());
        doReturn(MOCK_LATITUDE).when(mockCursor).getDouble(SurveyDbAdapter.RecordQuery.LATITUDE);
        doReturn(MOCK_LONGITUDE).when(mockCursor).getDouble(SurveyDbAdapter.RecordQuery.LONGITUDE);
        doReturn(MOCK_STATUS_INDEX).when(mockCursor).getColumnIndex(SurveyInstanceColumns.STATUS);
        doReturn(MOCK_STATUS).when(mockCursor).getInt(MOCK_STATUS_INDEX);
    }

    @Test
    public void getDataPoints_shouldReturnItemsWhenCursorNonNullMissingCoordinates() {
        DataPointMapper dataPointMapper = new DataPointMapper();
        provideMockCursorWithOneItemMissingCoordinates();

        List<DataPoint> dataPoints = dataPointMapper.getDataPoints(mockCursor);

        assertEquals(1, dataPoints.size());
        DataPoint dataPoint = dataPoints.get(0);
        assertEquals(MOCK_ID, dataPoint.getId());
        assertEquals(MOCK_SURVEY_GROUP_ID, dataPoint.getSurveyGroupId());
        assertEquals(MOCK_DATE, dataPoint.getLastModified());
        assertEquals(MOCK_NAME, dataPoint.getName());
        assertEquals(null, dataPoint.getLatitude());
        assertEquals(null, dataPoint.getLongitude());
        assertEquals(MOCK_STATUS, dataPoint.getStatus());
    }

    private void provideMockCursorWithOneItemMissingCoordinates() {
        doNothing().when(mockCursor).close();
        doReturn(true).when(mockCursor).moveToFirst();
        doReturn(false).when(mockCursor).moveToNext();
        doReturn(MOCK_ID).when(mockCursor).getString(SurveyDbAdapter.RecordQuery.RECORD_ID);
        doReturn(MOCK_SURVEY_GROUP_ID).when(mockCursor).getLong(SurveyDbAdapter.RecordQuery.SURVEY_GROUP_ID);
        doReturn(MOCK_DATE).when(mockCursor).getLong(SurveyDbAdapter.RecordQuery.LAST_MODIFIED);
        doReturn(MOCK_NAME).when(mockCursor).getString(SurveyDbAdapter.RecordQuery.NAME);
        doReturn(true).when(mockCursor).isNull(anyInt());
        doReturn(MOCK_STATUS_INDEX).when(mockCursor).getColumnIndex(SurveyInstanceColumns.STATUS);
        doReturn(MOCK_STATUS).when(mockCursor).getInt(MOCK_STATUS_INDEX);
    }

    @Test
    public void getDataPoints_shouldReturnItemsWhenCursorNonNullMissingStatus() {
        DataPointMapper dataPointMapper = new DataPointMapper();
        provideMockCursorWithOneItemMissingStatus();

        List<DataPoint> dataPoints = dataPointMapper.getDataPoints(mockCursor);

        assertEquals(1, dataPoints.size());
        DataPoint dataPoint = dataPoints.get(0);
        assertEquals(MOCK_ID, dataPoint.getId());
        assertEquals(MOCK_SURVEY_GROUP_ID, dataPoint.getSurveyGroupId());
        assertEquals(MOCK_DATE, dataPoint.getLastModified());
        assertEquals(MOCK_NAME, dataPoint.getName());
        assertEquals(MOCK_LATITUDE, dataPoint.getLatitude());
        assertEquals(MOCK_LONGITUDE, dataPoint.getLongitude());
        assertEquals(0, dataPoint.getStatus());
    }

    private void provideMockCursorWithOneItemMissingStatus() {
        doNothing().when(mockCursor).close();
        doReturn(true).when(mockCursor).moveToFirst();
        doReturn(false).when(mockCursor).moveToNext();
        doReturn(MOCK_ID).when(mockCursor).getString(SurveyDbAdapter.RecordQuery.RECORD_ID);
        doReturn(MOCK_SURVEY_GROUP_ID).when(mockCursor).getLong(SurveyDbAdapter.RecordQuery.SURVEY_GROUP_ID);
        doReturn(MOCK_DATE).when(mockCursor).getLong(SurveyDbAdapter.RecordQuery.LAST_MODIFIED);
        doReturn(MOCK_NAME).when(mockCursor).getString(SurveyDbAdapter.RecordQuery.NAME);
        doReturn(false).when(mockCursor).isNull(anyInt());
        doReturn(MOCK_LATITUDE).when(mockCursor).getDouble(SurveyDbAdapter.RecordQuery.LATITUDE);
        doReturn(MOCK_LONGITUDE).when(mockCursor).getDouble(SurveyDbAdapter.RecordQuery.LONGITUDE);
        doReturn(-1).when(mockCursor).getColumnIndex(SurveyInstanceColumns.STATUS);
    }
}