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

import org.akvo.flow.database.SyncTimeColumns;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static org.mockito.Mockito.doNothing;
import static org.powermock.api.mockito.PowerMockito.doReturn;

@RunWith(MockitoJUnitRunner.class)
public class SyncedTimeMapperTest {

    public static final String MOCK_TIME = "123";
    @Mock
    Cursor mockCursor;

    @Test
    public void getTime_shouldReturnNullWhenNullCursor() {
        SyncedTimeMapper syncedTimeMapper = new SyncedTimeMapper();

        String time = syncedTimeMapper.getTime(null);

        assertNull(time);
    }

    @Test
    public void getTime_shouldReturnNullWhenEmptyCursor() {
        SyncedTimeMapper syncedTimeMapper = new SyncedTimeMapper();
        provideEmptyCursor();

        String time = syncedTimeMapper.getTime(mockCursor);

        assertNull(time);
    }

    private void provideEmptyCursor() {
        doNothing().when(mockCursor).close();
        doReturn(false).when(mockCursor).moveToFirst();
    }

    @Test
    public void getTime_shouldReturnValidDateWhenNonEmptyCursor() {
        SyncedTimeMapper syncedTimeMapper = new SyncedTimeMapper();
        provideCursor();

        String time = syncedTimeMapper.getTime(mockCursor);

        assertEquals(MOCK_TIME, time);
    }

    private void provideCursor() {
        doNothing().when(mockCursor).close();
        doReturn(true).when(mockCursor).moveToFirst();
        doReturn(0).when(mockCursor).getColumnIndexOrThrow(SyncTimeColumns.TIME);
        doReturn(MOCK_TIME).when(mockCursor).getString(0);
    }
}
