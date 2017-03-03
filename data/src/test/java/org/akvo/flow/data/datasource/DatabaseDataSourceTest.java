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

package org.akvo.flow.data.datasource;

import android.database.Cursor;

import org.akvo.flow.data.entity.ApiDataPoint;
import org.akvo.flow.database.Constants;
import org.akvo.flow.database.britedb.BriteSurveyDbAdapter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.observers.TestSubscriber;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Matchers.anyDouble;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class DatabaseDataSourceTest {

    @Mock
    BriteSurveyDbAdapter briteSurveyDbAdapter;

    @Mock
    Cursor mockCursor;

    private void returnDefaultValues() {
        doReturn(Observable.just(mockCursor)).when(briteSurveyDbAdapter)
                .getSurveyedLocales(anyLong());
        doReturn(Observable.just(mockCursor)).when(briteSurveyDbAdapter)
                .getFilteredSurveyedLocales(anyLong(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    public void getDataPoints_ShouldCallGetSurveyedLocalesIfOrderByNull() throws Exception {
        returnDefaultValues();
        TestSubscriber<Cursor> testSubscriber = new TestSubscriber<>();
        DatabaseDataSource databaseDataSource = new DatabaseDataSource(briteSurveyDbAdapter);

        databaseDataSource.getDataPoints(1L, null, null, null).subscribe(testSubscriber);

        verify(briteSurveyDbAdapter, times(1)).getSurveyedLocales(1L);
        verify(briteSurveyDbAdapter, times(0))
                .getFilteredSurveyedLocales(anyLong(), anyDouble(), anyDouble(), anyInt());
        testSubscriber.assertCompleted();
    }

    @Test
    public void getDataPoints_ShouldCallGetFilteredSurveyedLocalesIfOrderByDate() throws Exception {
        returnDefaultValues();
        TestSubscriber<Cursor> testSubscriber = new TestSubscriber<>();
        DatabaseDataSource databaseDataSource = new DatabaseDataSource(briteSurveyDbAdapter);

        databaseDataSource.getDataPoints(1L, null, null, Constants.ORDER_BY_DATE)
                .subscribe(testSubscriber);

        verify(briteSurveyDbAdapter, times(0)).getSurveyedLocales(1L);
        verify(briteSurveyDbAdapter, times(1))
                .getFilteredSurveyedLocales(anyLong(), anyDouble(), anyDouble(), anyInt());
        testSubscriber.assertCompleted();
    }

    @Test
    public void syncSurveyedLocales_ShouldReturnEmptyIfInputNull() throws Exception {
        TestSubscriber<List<ApiDataPoint>> testSubscriber = new TestSubscriber<>();
        DatabaseDataSource databaseDataSource = new DatabaseDataSource(briteSurveyDbAdapter);

        databaseDataSource.syncSurveyedLocales(null).subscribe(testSubscriber);

        assertEquals(0, testSubscriber.getOnNextEvents().get(0).size());
        testSubscriber.assertCompleted();
    }

    @Test
    public void syncSurveyedLocales_ShouldReturnNonEmptyList() throws Exception {
        TestSubscriber<List<ApiDataPoint>> testSubscriber = new TestSubscriber<>();
        DatabaseDataSource databaseDataSource = new DatabaseDataSource(briteSurveyDbAdapter);
        List<ApiDataPoint> apiDataPoints = new ArrayList<>();

        databaseDataSource.syncSurveyedLocales(apiDataPoints).subscribe(testSubscriber);

        assertEquals(apiDataPoints, testSubscriber.getOnNextEvents().get(0));
        testSubscriber.assertCompleted();
    }
}